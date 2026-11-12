package me.enivzoy.anilife.core.net

import me.enivzoy.anilife.core.model.AiringEntry
import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.AnimeDetail
import me.enivzoy.anilife.core.model.Home
import me.enivzoy.anilife.core.model.NextAiring
import me.enivzoy.anilife.core.model.SearchPage
import me.enivzoy.anilife.core.model.plainDescription
import me.enivzoy.anilife.core.model.seasonOf
import me.enivzoy.anilife.core.model.seasonYearOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.random.Random

private const val ENDPOINT = "https://graphql.anilist.co"
private const val USER_AGENT = "AniLife/1.0 (+https://github.com/sAniLife)"

 
private const val NEWEST_AIRED_BUFFER_SECONDS = 15 * 60L

 
private const val CARD_FIELDS = """
  id idMal isAdult format status episodes averageScore season seasonYear genres
  title { romaji english native userPreferred }
  coverImage { extraLarge large color }
  bannerImage
  nextAiringEpisode { episode airingAt }
"""

 
class AniListException(override val message: String) : RuntimeException(message)

 




class AniListClient(
    private val http: HttpClient = defaultHttpClient(),
    private val endpoint: String = ENDPOINT,
) {
    @Volatile
    var titleLanguage: String = "english"

    suspend fun home(
        showAdult: Boolean = false,
        season: String = seasonOf(),
        year: Int = seasonYearOf(),
    ): Home {
        val adult = if (showAdult) "" else ", isAdult: false"
        val query = """
            query (${'$'}season: MediaSeason, ${'$'}year: Int, ${'$'}airedBefore: Int) {
              trending: Page(perPage: 20) { media(type: ANIME, sort: TRENDING_DESC$adult) { $CARD_FIELDS } }
              seasonal: Page(perPage: 20) { media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}year, sort: POPULARITY_DESC$adult) { $CARD_FIELDS } }
              popular: Page(perPage: 20) { media(type: ANIME, sort: POPULARITY_DESC$adult) { $CARD_FIELDS } }
              topRated: Page(perPage: 20) { media(type: ANIME, sort: SCORE_DESC$adult) { $CARD_FIELDS } }
              upcoming: Page(perPage: 20) { media(type: ANIME, status: NOT_YET_RELEASED, sort: POPULARITY_DESC$adult) { $CARD_FIELDS } }
              movies: Page(perPage: 20) { media(type: ANIME, format: MOVIE, sort: POPULARITY_DESC$adult) { $CARD_FIELDS } }
              newest: Page(perPage: 50) { airingSchedules(airingAt_lesser: ${'$'}airedBefore, sort: TIME_DESC) { media { $CARD_FIELDS } } }
            }
        """.trimIndent()

        val data = execute(
            query,
            buildJsonObject {
                put("season", season)
                put("year", year)
                put("airedBefore", nowSeconds() - NEWEST_AIRED_BUFFER_SECONDS)
            },
        )

        fun pageCards(key: String): List<AnimeCard> =
            (data[key] as? JsonObject)
                ?.get("media")
                .asArray()
                ?.mapNotNull { (it as? JsonObject)?.let { media -> parseCard(media, titleLanguage) } }
                ?.filter { showAdult || !it.isAdult }
                .orEmpty()

        val newest = (data["newest"] as? JsonObject)
            ?.get("airingSchedules")
            .asArray()
            ?.mapNotNull { schedule ->
                ((schedule as? JsonObject)?.get("media") as? JsonObject)?.let { media -> parseCard(media, titleLanguage) }
            }
            ?.filter { showAdult || !it.isAdult }
            ?.distinctBy { it.id }
            ?.take(20)
            .orEmpty()

        return Home(
            trending = pageCards("trending"),
            seasonal = pageCards("seasonal"),
            popular = pageCards("popular"),
            topRated = pageCards("topRated"),
            upcoming = pageCards("upcoming"),
            movies = pageCards("movies"),
            newest = newest,
        )
    }

     



    suspend fun search(
        query: String,
        genre: String? = null,
        page: Int = 1,
        perPage: Int = 30,
        showAdult: Boolean = false,
    ): SearchPage {
        val gql = """
            query (${'$'}page: Int, ${'$'}search: String, ${'$'}genre: String, ${'$'}sort: [MediaSort], ${'$'}isAdult: Boolean) {
              Page(page: ${'$'}page, perPage: $perPage) {
                pageInfo { hasNextPage }
                media(type: ANIME, search: ${'$'}search, genre: ${'$'}genre, sort: ${'$'}sort, isAdult: ${'$'}isAdult) {
                  $CARD_FIELDS
                  title { native }
                  synonyms
                }
              }
            }
        """.trimIndent()

        val sort = when {
            !genre.isNullOrBlank() && query.isBlank() -> "POPULARITY_DESC"
            query.isNotBlank() -> "SEARCH_MATCH"
            else -> "TRENDING_DESC"
        }

        val data = execute(
            gql,
            buildJsonObject {
                put("page", page)
                put("search", query.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
                put("genre", genre?.let(::JsonPrimitive) ?: JsonNull)
                put("sort", buildJsonArray { add(JsonPrimitive(sort)) })
                put("isAdult", JsonPrimitive(showAdult))
            },
        )

        val pageObj = data["Page"] as? JsonObject
        val results = pageObj?.get("media").asArray()
            ?.mapNotNull { (it as? JsonObject)?.let { media -> parseCard(media, titleLanguage) } }
            ?.filter { showAdult || !it.isAdult }
            .orEmpty()
        val hasNext = (pageObj?.get("pageInfo") as? JsonObject)?.bool("hasNextPage") ?: false
        return SearchPage(results = results, hasNext = hasNext)
    }

     
    suspend fun detail(id: Long): AnimeDetail {
        val query = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                $CARD_FIELDS
                idMal duration source synonyms description(asHtml: false)
                title { native }
                startDate { year month day }
                endDate { year month day }
                studios(isMain: true) { nodes { name } }
                trailer { site id }
                tags { name rank isMediaSpoiler }
                relations { edges { relationType(version: 2) node { type $CARD_FIELDS } } }
                recommendations(perPage: 12, sort: RATING_DESC) { nodes { mediaRecommendation { type $CARD_FIELDS } } }
                characters(perPage: 14, sort: [ROLE, RELEVANCE]) {
                  edges {
                    role
                    node { name { userPreferred } image { large } }
                    voiceActors(language: JAPANESE) { name { userPreferred } }
                  }
                }
              }
            }
        """.trimIndent()

        val data = execute(query, buildJsonObject { put("id", id) })
        val media = data["Media"] as? JsonObject ?: throw AniListException("AniList has no title $id")
        val card = parseCard(media, titleLanguage) ?: throw AniListException("AniList returned a title without an id")

        val title = media["title"] as? JsonObject
        val studios = (media["studios"] as? JsonObject)
            ?.get("nodes")
            .asArray()
            ?.mapNotNull { (it as? JsonObject)?.string("name") }
            .orEmpty()

        val relations = (media["relations"] as? JsonObject)
            ?.get("edges")
            .asArray()
            ?.mapNotNull { element ->
                val edge = element as? JsonObject ?: return@mapNotNull null
                val node = edge["node"] as? JsonObject ?: return@mapNotNull null
                if (node.string("type") != "ANIME") return@mapNotNull null
                val anime = parseCard(node, titleLanguage) ?: return@mapNotNull null
                AnimeDetail.Relation(edge.string("relationType") ?: "RELATED", anime)
            }
            .orEmpty()

        val recommendations = (media["recommendations"] as? JsonObject)
            ?.get("nodes")
            .asArray()
            ?.mapNotNull { element ->
                val node = (element as? JsonObject)?.get("mediaRecommendation") as? JsonObject
                    ?: return@mapNotNull null
                if (node.string("type") != null && node.string("type") != "ANIME") return@mapNotNull null
                parseCard(node, titleLanguage)
            }
            ?.filter { it.id != card.id }
            .orEmpty()

        val characters = (media["characters"] as? JsonObject)
            ?.get("edges")
            .asArray()
            ?.mapNotNull { element ->
                val edge = element as? JsonObject ?: return@mapNotNull null
                val node = edge["node"] as? JsonObject ?: return@mapNotNull null
                val name = (node["name"] as? JsonObject)?.string("userPreferred") ?: return@mapNotNull null
                val image = (node["image"] as? JsonObject)?.string("large")
                val voiceActor = (edge["voiceActors"].asArray()?.firstOrNull() as? JsonObject)
                    ?.get("name")
                    ?.let { (it as? JsonObject)?.string("userPreferred") }
                AnimeDetail.Character(
                    name = name,
                    image = image,
                    role = edge.string("role"),
                    voiceActor = voiceActor,
                )
            }
            .orEmpty()

        val tags = media["tags"].asArray()
            ?.mapNotNull { (it as? JsonObject)?.string("name") }
            .orEmpty()

        val trailer = (media["trailer"] as? JsonObject)?.let { t ->
            val site = t.string("site") ?: return@let null
            val videoId = t.string("id") ?: return@let null
            AnimeDetail.Trailer(site = site, id = videoId)
        }

        return AnimeDetail(
            id = card.id,
            malId = media.long("idMal"),
            titleNative = title?.string("native"),
            synonyms = media["synonyms"].asArray()?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
            description = plainDescription(media.string("description")),
            duration = media.int("duration"),
            source = media.string("source"),
            studios = studios,
            startDate = fuzzyDate(media["startDate"] as? JsonObject),
            endDate = fuzzyDate(media["endDate"] as? JsonObject),
            trailer = trailer,
            relations = relations,
            recommendations = recommendations,
            characters = characters,
            tags = tags,
            logo = null,
            title = card.title,
            titleRomaji = card.titleRomaji,
            titleEnglish = card.titleEnglish,
            cover = card.cover,
            color = card.color,
            banner = card.banner,
            format = card.format,
            status = card.status,
            episodes = card.episodes,
            score = card.score,
            year = card.year,
            season = card.season,
            genres = card.genres,
            nextAiring = card.nextAiring,
            isAdult = card.isAdult,
        )
    }

    suspend fun episodeTitles(id: Long): Map<Int, String> {
        val query = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                streamingEpisodes { title }
              }
            }
        """.trimIndent()
        val media = execute(query, buildJsonObject { put("id", id) })["Media"] as? JsonObject
            ?: return emptyMap()
        val episodeTitlePattern = Regex("""(?:episode|ep\.?)\s*#?\s*(\d+)\s*[:\-–.]?\s*(.*)""", RegexOption.IGNORE_CASE)
        return media["streamingEpisodes"].asArray()?.mapNotNull { item ->
            val title = (item as? JsonObject)?.string("title")?.trim().orEmpty()
            val match = episodeTitlePattern.matchEntire(title) ?: return@mapNotNull null
            val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val episodeTitle = match.groupValues[2].trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            number to episodeTitle
        }?.toMap().orEmpty()
    }

     



    suspend fun schedule(days: Int = 7, showAdult: Boolean = false): List<AiringEntry> {
        val from = nowSeconds()
        val to = from + days * 86_400L
        val query = """
            query (${'$'}page: Int, ${'$'}from: Int, ${'$'}to: Int) {
              Page(page: ${'$'}page, perPage: 50) {
                pageInfo { hasNextPage }
                airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: TIME) {
                  airingAt episode media { $CARD_FIELDS }
                }
              }
            }
        """.trimIndent()

        val entries = mutableListOf<AiringEntry>()
        for (page in 1..4) {
            val data = execute(
                query,
                buildJsonObject {
                    put("page", page)
                    put("from", from)
                    put("to", to)
                },
            )
            val pageObj = data["Page"] as? JsonObject ?: break
            pageObj["airingSchedules"].asArray()?.forEach { element ->
                val schedule = element as? JsonObject ?: return@forEach
                val anime = (schedule["media"] as? JsonObject)?.let { media -> parseCard(media, titleLanguage) } ?: return@forEach
                if (!showAdult && anime.isAdult) return@forEach
                val airingAt = schedule.long("airingAt") ?: return@forEach
                val episode = schedule.int("episode") ?: return@forEach
                entries += AiringEntry(airingAt = airingAt, episode = episode, anime = anime)
            }
            val hasNext = (pageObj["pageInfo"] as? JsonObject)?.bool("hasNextPage") ?: false
            if (!hasNext) break
        }

        return entries
            .distinctBy { it.anime.id to it.airingAt }
            .sortedBy { it.airingAt }
    }

     
    suspend fun random(showAdult: Boolean = false): AnimeCard? {        val page = Random.nextInt(1, 61)
        val query = """
            query (${'$'}page: Int, ${'$'}isAdult: Boolean) {
              Page(page: ${'$'}page, perPage: 1) {
                media(type: ANIME, sort: POPULARITY_DESC, popularity_greater: 10000, isAdult: ${'$'}isAdult) { $CARD_FIELDS }
              }
            }
        """.trimIndent()
        val data = execute(
            query,
            buildJsonObject {
                put("page", page)
                put("isAdult", JsonPrimitive(showAdult))
            },
        )
        return (data["Page"] as? JsonObject)
            ?.get("media")
            .asArray()
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.let { media -> parseCard(media, titleLanguage) } }
    }

    private suspend fun execute(query: String, variables: JsonObject = buildJsonObject { }): JsonObject {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString()

        val request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(25))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val response = withContext(Dispatchers.IO) {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() == 429) {
            throw AniListException("AniList rate limit reached — try again in a minute.")
        }

        val root = runCatching { Json.parseToJsonElement(response.body()) as? JsonObject }
            .getOrNull()
            ?: throw AniListException("AniList sent an unreadable response (HTTP ${response.statusCode()}).")

        val errors = root["errors"].asArray()
        if (errors != null && errors.isNotEmpty()) {
            val first = errors.firstOrNull() as? JsonObject
            val message = first?.string("message") ?: "AniList rejected the request."
            throw AniListException(message)
        }

        if (response.statusCode() >= 400) {
            throw AniListException("AniList request failed (HTTP ${response.statusCode()}).")
        }

        return root["data"] as? JsonObject ?: throw AniListException("AniList returned no data.")
    }
}

private fun defaultHttpClient(): HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(15))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

 
internal fun parseCard(json: JsonObject, titleLanguage: String = "english"): AnimeCard? {
    val id = json.long("id") ?: return null
    val title = json["title"] as? JsonObject
    val englishTitle = title?.string("english")
    val romajiTitle = title?.string("romaji")
    val nativeTitle = title?.string("native")
    val userPreferred = title?.string("userPreferred")
    val chosenTitle = when (titleLanguage) {
        "romaji" -> romajiTitle ?: userPreferred ?: englishTitle ?: nativeTitle
        "native" -> nativeTitle ?: romajiTitle ?: userPreferred ?: englishTitle
        else -> englishTitle ?: userPreferred ?: romajiTitle ?: nativeTitle
    } ?: "Untitled"
    val coverImage = json["coverImage"] as? JsonObject
    val nextAiring = (json["nextAiringEpisode"] as? JsonObject)?.let { episode ->
        val number = episode.int("episode") ?: return@let null
        val at = episode.long("airingAt") ?: return@let null
        NextAiring(episode = number, airingAt = at)
    }

    return AnimeCard(
        id = id,
        title = chosenTitle,
        titleRomaji = romajiTitle,
        titleEnglish = englishTitle,
        titleNative = nativeTitle,
        malId = json.long("idMal"),
        cover = coverImage?.string("extraLarge") ?: coverImage?.string("large"),
        color = coverImage?.string("color"),
        banner = json.string("bannerImage"),
        format = json.string("format"),
        status = json.string("status"),
        episodes = json.int("episodes"),
        score = json.int("averageScore")?.toDouble(),
        year = json.int("seasonYear"),
        season = json.string("season"),
        genres = json["genres"].asArray()?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
        nextAiring = nextAiring,
        isAdult = json.bool("isAdult") ?: false,
        aliases = buildList {
            title?.string("native")?.let(::add)
            json["synonyms"].asArray()?.forEach { (it as? JsonPrimitive)?.contentOrNull?.let(::add) }
        }.distinct(),
    )
}

private fun fuzzyDate(date: JsonObject?): String? {
    if (date == null) return null
    val year = date.int("year") ?: return null
    val month = date.int("month")
    val day = date.int("day")
    return buildString {
        append(year)
        if (month != null) {
            append('-')
            append(month.toString().padStart(2, '0'))
            if (day != null) {
                append('-')
                append(day.toString().padStart(2, '0'))
            }
        }
    }
}

private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.contentOrNull?.toLongOrNull() }

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

