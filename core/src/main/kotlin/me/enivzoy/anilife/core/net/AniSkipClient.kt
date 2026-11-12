package me.enivzoy.anilife.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import me.enivzoy.anilife.core.model.SkipTimes
import me.enivzoy.anilife.core.model.Window
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.abs

const val ANISKIP_ENDPOINT = "https://api.aniskip.com/v2"

private const val USER_AGENT = "AniLife/1.0 (+https://github.com/sAniLife)"
private val ALL_TYPES = listOf("op", "ed", "mixed-op", "mixed-ed", "recap")
private val CORE_TYPES = listOf("op", "ed")

class AniSkipException(message: String) : IOException(message)

/**
 * One entry of AniSkip's episode redirection table.
 *
 * MAL files a long-running show as one entry per cour while streaming providers usually number
 * episodes straight through, so the episode being watched is often not the one the skip database
 * keys its timings by. A rule says "episodes [fromStart, fromEnd] of the anime asked about are
 * episodes starting at [toStart] of [toMalId]".
 */
data class AniSkipRelationRule(
    val fromStart: Int,
    val fromEnd: Int?,
    val toMalId: Long,
    val toStart: Int,
    val toEnd: Int?,
)

/** Which MAL entry and episode hold [episode]'s timings, or null when no rule applies. */
internal fun List<AniSkipRelationRule>.redirect(malId: Long, episode: Int): Pair<Long, Int>? {
    for (rule in this) {
        if (episode < rule.fromStart) continue
        if (rule.fromEnd != null && episode > rule.fromEnd) continue
        val mapped = rule.toStart + (episode - rule.fromStart)
        if (mapped < 1) continue
        if (rule.toEnd != null && mapped > rule.toEnd) continue
        // Rule sets overlap, so the first match wins, as AniSkip's own extension does it. A rule
        // pointing back at the lookup already made is not a redirect.
        if (rule.toMalId == malId && mapped == episode) continue
        return rule.toMalId to mapped
    }
    return null
}

/**
 * The community skip-time database (api.aniskip.com), keyed by MyAnimeList id. It carries the
 * intro, outro and recap markers that most streaming providers do not.
 *
 * AniSkip can hold several submissions per episode, one per encode, so every candidate is returned
 * to [parseSkipTimes] and the one nearest the stream's length wins.
 */
class AniSkipClient(
    private val http: HttpClient = defaultHttpClient(),
    private val endpoint: String = ANISKIP_ENDPOINT,
) {
    /**
     * Intro, outro and recap windows for one episode; null when AniSkip has none (unknown episodes
     * answer 404). [episodeLengthSeconds] lets the database pick timings for the encode playing.
     */
    suspend fun skipTimes(
        malId: Long,
        episode: Int,
        episodeLengthSeconds: Double = 0.0,
    ): SkipTimes? {
        if (malId <= 0 || episode <= 0) return null
        require(episodeLengthSeconds.isFinite() && episodeLengthSeconds >= 0.0) {
            "episodeLengthSeconds must be finite and non-negative"
        }

        // AniSkip intermittently answers 500s for the full type list while serving openings and
        // endings cleanly, so a narrower retry stands between a flaky upstream and an episode with
        // no timings at all. A second failure still throws: an outage must not look like "none".
        val body = lookup(malId, episode, episodeLengthSeconds) ?: return null
        parseSkipTimes(body, episodeLengthSeconds)?.let { return it }

        val rules = try {
            relationRules(malId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val (toMalId, toEpisode) = rules.redirect(malId, episode) ?: return null
        val redirected = lookup(toMalId, toEpisode, episodeLengthSeconds) ?: return null
        return parseSkipTimes(redirected, episodeLengthSeconds)
    }

    /** The redirection table for one MAL entry, empty when AniSkip publishes none for it. */
    suspend fun relationRules(malId: Long): List<AniSkipRelationRule> {
        if (malId <= 0) return emptyList()
        val body = get("$endpoint/relation-rules/$malId") ?: return emptyList()
        return parseRelationRules(body)
    }

    private suspend fun lookup(malId: Long, episode: Int, length: Double): JsonObject? =
        try {
            fetchSkipTimes(malId, episode, length, ALL_TYPES)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            fetchSkipTimes(malId, episode, length, CORE_TYPES)
        }

    private suspend fun fetchSkipTimes(
        malId: Long,
        episode: Int,
        length: Double,
        types: List<String>,
    ): JsonObject? {
        val query = buildString {
            types.forEach { append("types%5B%5D=").append(it).append('&') }
            append("episodeLength=").append(length)
        }
        return get("$endpoint/skip-times/$malId/$episode?$query")
    }

    /** One GET: the parsed body, null for AniSkip's documented 404, an [IOException] otherwise. */
    private suspend fun get(url: String): JsonObject? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = withContext(Dispatchers.IO) {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        }
        if (response.statusCode() == 404) return null
        if (response.statusCode() !in 200..299) {
            throw AniSkipException("AniSkip HTTP ${response.statusCode()}")
        }
        return runCatching { Json.parseToJsonElement(response.body()) as? JsonObject }
            .getOrNull()
            ?: throw AniSkipException("AniSkip sent an unreadable response.")
    }
}

internal data class AniSkipSegment(
    val type: String,
    val window: Window,
    val episodeLength: Double?,
)

/** Chooses the windows AniSkip should offer for an episode from all its submissions. */
internal fun parseSkipTimes(body: JsonObject, episodeLengthSeconds: Double): SkipTimes? {
    if (body["found"]?.asPrimitive()?.booleanOrNull != true) return null
    val results = body["results"]?.asArray().orEmpty()
    val segments = results.mapNotNull { element ->
        val result = element as? JsonObject ?: return@mapNotNull null
        val interval = result["interval"] as? JsonObject ?: return@mapNotNull null
        val start = interval["startTime"]?.asPrimitive()?.doubleOrNull ?: return@mapNotNull null
        val end = interval["endTime"]?.asPrimitive()?.doubleOrNull ?: return@mapNotNull null
        val type = result["skipType"]?.asPrimitive()?.contentOrNull ?: return@mapNotNull null
        val window = Window(start, end)
        if (!window.isValid) return@mapNotNull null
        AniSkipSegment(
            type = type,
            window = window,
            episodeLength = result["episodeLength"]?.asPrimitive()?.doubleOrNull,
        )
    }
    val intro = pickSegment(segments, "op", "mixed-op", episodeLengthSeconds)
    val outro = pickSegment(segments, "ed", "mixed-ed", episodeLengthSeconds)
    val recap = pickSegment(segments, "recap", "recap", episodeLengthSeconds)
    if (intro == null && outro == null && recap == null) return null
    return SkipTimes(intro = intro, outro = outro, recap = recap)
}

internal fun parseRelationRules(body: JsonObject): List<AniSkipRelationRule> {
    if (body["found"]?.asPrimitive()?.booleanOrNull != true) return emptyList()
    return body["rules"]?.asArray().orEmpty().mapNotNull { element ->
        val rule = element as? JsonObject ?: return@mapNotNull null
        val from = rule["from"] as? JsonObject ?: return@mapNotNull null
        val to = rule["to"] as? JsonObject ?: return@mapNotNull null
        AniSkipRelationRule(
            fromStart = from["start"]?.asPrimitive()?.intOrNull ?: return@mapNotNull null,
            fromEnd = from["end"]?.asPrimitive()?.intOrNull,
            toMalId = to["malId"]?.asPrimitive()?.intOrNull?.toLong() ?: return@mapNotNull null,
            toStart = to["start"]?.asPrimitive()?.intOrNull ?: return@mapNotNull null,
            toEnd = to["end"]?.asPrimitive()?.intOrNull,
        )
    }
}

/** The plain kind before its "mixed" fallback, nearest in length to the stream playing. */
private fun pickSegment(
    segments: List<AniSkipSegment>,
    primary: String,
    fallback: String,
    requestedLength: Double,
): Window? {
    val candidates = segments.filter { it.type == primary }.ifEmpty {
        segments.filter { it.type == fallback }
    }
    return candidates.minByOrNull { segment ->
        if (requestedLength <= 0.0) {
            0.0
        } else {
            segment.episodeLength?.takeIf(Double::isFinite)?.let { abs(it - requestedLength) }
                ?: Double.MAX_VALUE
        }
    }?.window
}

private fun defaultHttpClient(): HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

private fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray).orEmpty()

private fun JsonElement?.asPrimitive(): JsonPrimitive? = this as? JsonPrimitive
