package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.Home
import me.enivzoy.anilife.core.model.NextAiring
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal class HomeSnapshotStore(private val file: File = snapshotFile()) {
    fun read(): Home? {
        if (!file.isFile) return null
        return runCatching {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            Home(
                trending = root.cards("trending"),
                seasonal = root.cards("seasonal"),
                popular = root.cards("popular"),
                topRated = root.cards("topRated"),
                upcoming = root.cards("upcoming"),
                newest = root.cards("newest"),
                movies = root.cards("movies"),
            )
        }.getOrNull()
    }

    fun write(home: Home) {
        val target = file.absoluteFile
        val parent = requireNotNull(target.parentFile)
        Files.createDirectories(parent.toPath())
        val content = buildJsonObject {
            put("trending", home.trending.toJson())
            put("seasonal", home.seasonal.toJson())
            put("popular", home.popular.toJson())
            put("topRated", home.topRated.toJson())
            put("upcoming", home.upcoming.toJson())
            put("newest", home.newest.toJson())
            put("movies", home.movies.toJson())
        }.toString()
        val temporary = File(parent, "${target.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(content)
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }

    private fun List<AnimeCard>.toJson(): JsonArray = buildJsonArray {
        forEach { card ->
            add(buildJsonObject {
                put("id", card.id)
                put("title", card.title)
                put("titleRomaji", card.titleRomaji)
                put("titleEnglish", card.titleEnglish)
                put("titleNative", card.titleNative)
                put("malId", card.malId)
                put("cover", card.cover)
                put("color", card.color)
                put("banner", card.banner)
                put("format", card.format)
                put("status", card.status)
                put("episodes", card.episodes)
                put("score", card.score)
                put("year", card.year)
                put("season", card.season)
                put("genres", buildJsonArray { card.genres.forEach { add(JsonPrimitive(it)) } })
                put("nextAiring", card.nextAiring?.let { next ->
                    buildJsonObject {
                        put("episode", next.episode)
                        put("airingAt", next.airingAt)
                    }
                } ?: JsonNull)
                put("isAdult", card.isAdult)
                put("aliases", buildJsonArray { card.aliases.forEach { add(JsonPrimitive(it)) } })
            })
        }
    }

    private fun JsonObject.cards(key: String): List<AnimeCard> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toCard() }

    private fun JsonObject.toCard(): AnimeCard? {
        val id = long("id") ?: return null
        val title = string("title") ?: return null
        val next = (this["nextAiring"] as? JsonObject)?.let { obj ->
            val episode = obj.int("episode") ?: return@let null
            val airingAt = obj.long("airingAt") ?: return@let null
            NextAiring(episode, airingAt)
        }
        return AnimeCard(
            id = id,
            title = title,
            titleRomaji = string("titleRomaji"),
            titleEnglish = string("titleEnglish"),
            titleNative = string("titleNative"),
            malId = long("malId"),
            cover = string("cover"),
            color = string("color"),
            banner = string("banner"),
            format = string("format"),
            status = string("status"),
            episodes = int("episodes"),
            score = double("score"),
            year = int("year"),
            season = string("season"),
            genres = strings("genres"),
            nextAiring = next,
            isAdult = boolean("isAdult") ?: false,
            aliases = strings("aliases"),
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    companion object {
        private fun snapshotFile(): File {
            val base = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let(::File)
                ?: File(System.getProperty("user.home"), ".local/share")
            return File(base, "AniLife/home-cache.json")
        }
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

