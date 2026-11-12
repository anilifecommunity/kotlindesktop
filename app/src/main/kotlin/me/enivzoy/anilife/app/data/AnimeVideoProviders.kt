package me.enivzoy.anilife.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream
import java.util.concurrent.ConcurrentHashMap

data class AnimeEpisode(val number: Double, val title: String?, val thumbnail: String? = null) {
    val label: String get() = "Episode ${if (number % 1.0 == 0.0) number.toLong() else number}"
}

data class AnimeSubtitleSource(
    val url: String,
    val label: String,
    val language: String,
    val headers: Map<String, String> = emptyMap(),
)

data class AnimeVideoSource(
    val provider: String,
    val server: String,
    val audio: String,
    val format: String,
    val quality: String?,
    val url: String,
    val headers: Map<String, String>,
    val subtitles: List<AnimeSubtitleSource> = emptyList(),
) {
    val label: String get() = listOfNotNull(provider, server, quality, format.uppercase()).joinToString(" · ")
}

/** Miruro's public catalog API, following the same AniList-id → episode → provider flow as AniLife. */
class AnimeVideoProviders(
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val titleIds = ConcurrentHashMap<Long, String>()
    private val malEpisodeTitleCache = ConcurrentHashMap<Long, Map<Int, String>>()
    @Volatile private var streamingConfig: JsonObject? = null
    private val wasmProviders by lazy { WasmAnimeProviders() }

    suspend fun episodes(
        anilistId: Long,
        expectedCount: Int? = null,
        malId: Long? = null,
    ): List<AnimeEpisode> = withContext(Dispatchers.IO) {
        val catalogEpisodes = runCatching {
            val title = title(anilistId) ?: return@runCatching emptyList()
            val root = json.parseToJsonElement(get("v1/anime/${path(title)}/episodes", mapOf("limit" to "10000"))) as? JsonObject
                ?: return@runCatching emptyList()
            root.array("data").orEmpty().mapNotNull { item ->
            val episode = item as? JsonObject ?: return@mapNotNull null
            if (episode.string("kind")?.equals("regular", true) == false) return@mapNotNull null
            val number = episode.string("episode_number")?.toDoubleOrNull() ?: return@mapNotNull null
            AnimeEpisode(
                number,
                episode.string("title"),
                sequenceOf("thumbnail", "image", "cover").mapNotNull { episode.string(it) }.firstOrNull(String::isNotBlank),
            )
            }
        }.getOrDefault(emptyList())
        val episodes = catalogEpisodes.ifEmpty {
            (1..expectedCount.orZero().coerceIn(0, MAX_FALLBACK_EPISODES)).map { AnimeEpisode(it.toDouble(), null) }
        }
        val missingTitleNumbers = episodes
            .filter { it.title.isNullOrBlank() && it.number % 1.0 == 0.0 }
            .map { it.number.toInt() }
            .toSet()
        if (malId == null || missingTitleNumbers.isEmpty()) return@withContext episodes

        val titles = runCatching { episodeTitles(malId, missingTitleNumbers.maxOrNull() ?: return@runCatching emptyMap()) }
            .onFailure { System.err.println("MyAnimeList episode-title lookup failed: ${it.message}") }
            .getOrDefault(emptyMap())
        episodes.map { episode ->
            if (episode.title.isNullOrBlank() && episode.number % 1.0 == 0.0) {
                episode.copy(title = titles[episode.number.toInt()])
            } else {
                episode
            }
        }
    }

    suspend fun episodeTitles(malId: Long, throughEpisode: Int): Map<Int, String> = withContext(Dispatchers.IO) {
        malEpisodeTitles(malId, throughEpisode)
    }

    private fun malEpisodeTitles(malId: Long, throughEpisode: Int): Map<Int, String> {
        if (throughEpisode < 1) return emptyMap()
        val cached = malEpisodeTitleCache[malId].orEmpty()
        if ((1..throughEpisode).all(cached::containsKey)) return cached

        val titles = cached.toMutableMap()
        var page = 1
        var hasNextPage: Boolean
        do {
            val root = json.parseToJsonElement(getExternalJson(
                "https://api.jikan.moe/v4/anime/$malId/episodes?page=$page",
            )) as? JsonObject ?: break
            root.array("data").orEmpty().forEach { item ->
                val episode = item as? JsonObject ?: return@forEach
                val number = episode["mal_id"].asInt() ?: return@forEach
                val title = sequenceOf("title", "title_romanji", "title_japanese")
                    .mapNotNull { episode.string(it) }
                    .firstOrNull(String::isNotBlank)
                if (title != null) titles[number] = title
            }
            hasNextPage = (root.obj("pagination")?.get("has_next_page") as? JsonPrimitive)
                ?.booleanOrNull ?: false
            page++
        } while (hasNextPage && page <= (throughEpisode - 1) / MAL_EPISODES_PER_PAGE + 1)

        malEpisodeTitleCache[malId] = titles
        return titles
    }

    private fun getExternalJson(url: String): String {
        try {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET().build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw IOException("MyAnimeList episode API returned HTTP ${response.statusCode()}.")
            }
            return response.body()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("MyAnimeList episode lookup was interrupted.", error)
        }
    }

    suspend fun sources(
        anilistId: Long,
        malId: Long?,
        titles: List<String>,
        episode: Double,
        dub: Boolean,
    ): List<AnimeVideoSource> = withContext(Dispatchers.IO) {
        val result = runCatching { miruroSources(anilistId, episode, dub) }
            .onFailure { System.err.println("Miruro source lookup failed: ${it.message}") }
            .getOrDefault(emptyList())
        val extra = runCatching {
            wasmProviders.sources(
                WasmAnimeTitle(anilistId, malId, titles.filter(String::isNotBlank).distinct()),
                episode,
                dub,
            )
        }.onFailure { System.err.println("WASM provider lookup failed: ${it.message}") }.getOrDefault(emptyList())
        (result.filter { it.audio == if (dub) "Dub" else "Sub" } + extra)
            .distinctBy { it.url }
            .sortedByDescending { source ->
                Regex("(\\d{3,4})\\s*p", RegexOption.IGNORE_CASE).find(source.quality.orEmpty())
                    ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            }
    }

    private suspend fun miruroSources(anilistId: Long, episode: Double, dub: Boolean): List<AnimeVideoSource> {
        val title = title(anilistId) ?: return emptyList()
        val providers = providerConfig().obj("streaming").orEmpty()
        val play = json.parseToJsonElement(get("v1/anime/${path(title)}/episodes/${number(episode)}/play")) as? JsonObject
            ?: return emptyList()
        val result = mutableListOf<AnimeVideoSource>()
        play.array("tracks").orEmpty().forEach trackLoop@ { trackItem ->
            val track = trackItem as? JsonObject ?: return@trackLoop
            val audio = when (track.string("track")) {
                "dub" -> "Dub"
                "sub", "ssub" -> "Sub"
                else -> return@trackLoop
            }
            if (audio != if (dub) "Dub" else "Sub") return@trackLoop
            track.array("providers").orEmpty().forEach providerLoop@ { providerItem ->
                val provider = providerItem as? JsonObject ?: return@providerLoop
                val key = provider.string("provider") ?: return@providerLoop
                val configEntry = providers[key] as? JsonObject
                if (configEntry?.get("visible").let { (it as? JsonPrimitive)?.booleanOrNull } == false) return@providerLoop
                val label = configEntry?.string("label")?.takeIf(String::isNotBlank) ?: key
                val subtitles = provider.array("subtitles").orEmpty().mapNotNull { subtitleItem ->
                    val subtitle = subtitleItem as? JsonObject ?: return@mapNotNull null
                    val subtitleUrl = (subtitle.string("file") ?: subtitle.string("url"))?.takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    AnimeSubtitleSource(
                        url = subtitleUrl,
                        label = subtitle.string("label") ?: subtitle.string("language") ?: "Subtitle",
                        language = subtitle.string("language") ?: "en",
                        headers = subtitle.obj("headers").orEmpty().mapNotNull { (name, value) ->
                            (value as? JsonPrimitive)?.contentOrNull?.let { name to it }
                        }.toMap(),
                    )
                }
                provider.array("servers").orEmpty().forEach serverLoop@ { serverItem ->
                    val server = serverItem as? JsonObject ?: return@serverLoop
                    val serverName = server.string("server") ?: label
                    val headers = server.obj("headers").orEmpty().mapNotNull { (name, value) ->
                        (value as? JsonPrimitive)?.contentOrNull?.let { name to it }
                    }.toMap()
                    server.array("streams").orEmpty().forEach streamLoop@ { streamItem ->
                        val stream = streamItem as? JsonObject ?: return@streamLoop
                        val url = stream.string("url")?.takeIf(String::isNotBlank) ?: return@streamLoop
                        result += AnimeVideoSource(
                            provider = label,
                            server = serverName,
                            audio = audio,
                            format = stream.string("format") ?: inferFormat(url),
                            quality = stream.string("quality") ?: serverName.takeIf { it != label },
                            url = url,
                            headers = headers,
                            subtitles = subtitles,
                        )
                    }
                }
            }
        }
        return result
    }

    private suspend fun title(anilistId: Long): String? {
        titleIds[anilistId]?.let { return it }
        val root = json.parseToJsonElement(get("v1/anime", mapOf("anilist_id_in" to anilistId.toString(), "limit" to "100"))) as? JsonObject
            ?: return null
        return (root.array("data")?.firstOrNull() as? JsonObject)?.string("id")?.also { titleIds[anilistId] = it }
    }

    private fun providerConfig(): JsonObject {
        streamingConfig?.let { return it }
        return (json.parseToJsonElement(get("config")) as? JsonObject)?.also { streamingConfig = it }
            ?: throw IOException("The provider catalog returned an invalid configuration.")
    }

    private fun get(path: String, query: Map<String, String> = emptyMap()): String {
        var last: Throwable? = null
        for (origin in MIRURO_ORIGINS) {
            try {
                val suffix = query.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }.let { if (it.isEmpty()) "" else "?$it" }
                val request = HttpRequest.newBuilder(URI.create("$origin/api/$path$suffix"))
                    .timeout(Duration.ofSeconds(22))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "$origin/")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Accept", "application/json, application/octet-stream;q=0.9, */*;q=0.8")
                    .GET().build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
                if (response.statusCode() !in 200..299) {
                    val detail = response.body().toString(Charsets.UTF_8).take(180)
                    if (response.statusCode() in 400..499) throw IOException("Provider catalog rejected the request (${response.statusCode()}): $detail")
                    throw IOException("Provider catalog returned HTTP ${response.statusCode()}.")
                }
                val bytes = response.body()
                return if (response.headers().firstValue("Content-Type").orElse("").startsWith("application/octet-stream")) decodeCatalogBody(bytes)
                else bytes.toString(Charsets.UTF_8)
            } catch (error: IOException) {
                if (error.message?.startsWith("Provider catalog rejected") == true) throw error
                last = error
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Provider lookup was interrupted.", error)
            } catch (error: Exception) {
                last = error
            }
        }
        throw IOException("Couldn't reach the video provider catalog.", last)
    }

    private fun decodeCatalogBody(bytes: ByteArray): String {
        val plain = ByteArray(bytes.size) { index -> (bytes[index].toInt() xor CATALOG_KEY[index % CATALOG_KEY.size].toInt()).toByte() }
        return GZIPInputStream(ByteArrayInputStream(plain)).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
    private fun path(value: String) = value.split('/').joinToString("/") { encode(it) }
    private fun number(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    private fun inferFormat(url: String) = when {
        ".m3u8" in url.lowercase() -> "hls"
        ".mpd" in url.lowercase() -> "dash"
        else -> "mp4"
    }

    private fun Int?.orZero() = this ?: 0

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray
    private fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private companion object {
        val MIRURO_ORIGINS = listOf("https://www.miruro.to", "https://www.miruro.tv", "https://www.miruro.bz", "https://www.miruro.ru", "https://www.miruro.cx")
        val CATALOG_KEY = "miruro/catalog".toByteArray(Charsets.US_ASCII)
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val MAX_FALLBACK_EPISODES = 300
        const val MAL_EPISODES_PER_PAGE = 100
    }
}

