package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.NextAiring
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties

data class LocalEpisode(
    val anime: AnimeCard,
    val episode: Double,
    val audio: String,
    val path: Path,
    val bytes: Long,
    val episodeTitle: String? = null,
)

data class DetectedFolder(
    val name: String,
    val matches: List<AnimeCard>,
    val confidence: Double,
    val episodes: List<LocalEpisode>,
)

data class AutoDetectResult(val folders: List<DetectedFolder>, val skipped: List<String>)

data class PortableImportResult(
    val anime: List<AnimeCard>,
    val episodes: List<LocalEpisode>,
    val missingFiles: List<String>,
)

internal class LocalDownloadsStore(private val file: File = downloadsFile()) {
    private val lock = Any()
    private val mutableEntries = MutableStateFlow(readEntries())
    val entries = mutableEntries.asStateFlow()

    fun addAll(episodes: List<LocalEpisode>) = synchronized(lock) {
        if (episodes.isEmpty()) return@synchronized
        val updated = mutableEntries.value.associateBy(::entryKey).toMutableMap()
        episodes.forEach { updated[entryKey(it)] = it }
        val sorted = updated.values.sortedWith(compareBy({ it.anime.title.lowercase() }, { it.episode }))
        writeEntries(sorted)
        mutableEntries.value = sorted
    }

    fun setTitleLanguage(language: String) = synchronized(lock) {
        val updated = mutableEntries.value
            .map { entry -> entry.copy(anime = entry.anime.withTitlePreference(language)) }
            .sortedWith(compareBy({ it.anime.title.lowercase() }, { it.episode }))
        if (updated != mutableEntries.value) {
            writeEntries(updated)
            mutableEntries.value = updated
        }
    }

    fun setPreferEnglishTitles(preferEnglish: Boolean) = setTitleLanguage(if (preferEnglish) "english" else "romaji")

    fun remove(episode: LocalEpisode) = synchronized(lock) {
        val updated = mutableEntries.value.filterNot { entryKey(it) == entryKey(episode) }
        if (updated.size != mutableEntries.value.size) {
            writeEntries(updated)
            mutableEntries.value = updated
        }
    }

    private fun readEntries(): List<LocalEpisode> {
        if (!file.isFile) return emptyList()
        val properties = Properties()
        file.inputStream().use(properties::load)
        val count = properties.getProperty("count")?.toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { index ->
            val prefix = "entry.$index."
            val id = properties.getProperty(prefix + "animeId")?.toLongOrNull() ?: return@mapNotNull null
            val title = properties.getProperty(prefix + "title")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val episode = properties.getProperty(prefix + "episode")?.toDoubleOrNull() ?: return@mapNotNull null
            val path = properties.getProperty(prefix + "path")?.let(Path::of) ?: return@mapNotNull null
            val bytes = properties.getProperty(prefix + "bytes")?.toLongOrNull() ?: 0L
            val airingEpisode = properties.getProperty(prefix + "airingEpisode")?.toIntOrNull()
            val airingAt = properties.getProperty(prefix + "airingAt")?.toLongOrNull()
            LocalEpisode(
                anime = AnimeCard(
                    id = id,
                    title = title,
                    titleRomaji = properties.getProperty(prefix + "titleRomaji"),
                    titleEnglish = properties.getProperty(prefix + "titleEnglish"),
                    titleNative = properties.getProperty(prefix + "titleNative"),
                    malId = properties.getProperty(prefix + "malId")?.toLongOrNull(),
                    cover = properties.getProperty(prefix + "cover"),
                    color = properties.getProperty(prefix + "color"),
                    banner = properties.getProperty(prefix + "banner"),
                    format = properties.getProperty(prefix + "format"),
                    status = properties.getProperty(prefix + "status"),
                    episodes = properties.getProperty(prefix + "totalEpisodes")?.toIntOrNull(),
                    score = properties.getProperty(prefix + "score")?.toDoubleOrNull(),
                    year = properties.getProperty(prefix + "year")?.toIntOrNull(),
                    season = properties.getProperty(prefix + "season"),
                    genres = properties.getProperty(prefix + "genres")?.split('|')?.filter(String::isNotBlank).orEmpty(),
                    nextAiring = if (airingEpisode != null && airingAt != null) NextAiring(airingEpisode, airingAt) else null,
                    isAdult = properties.getProperty(prefix + "isAdult")?.toBooleanStrictOrNull() ?: false,
                ),
                episode = episode,
                audio = properties.getProperty(prefix + "audio") ?: "sub",
                path = path,
                bytes = bytes,
                episodeTitle = properties.getProperty(prefix + "episodeTitle")?.takeIf(String::isNotBlank),
            )
        }
    }

    private fun writeEntries(entries: List<LocalEpisode>) {
        val properties = Properties()
        properties.setProperty("count", entries.size.toString())
        entries.forEachIndexed { index, entry ->
            val prefix = "entry.$index."
            val anime = entry.anime
            fun put(key: String, value: Any?) {
                if (value != null) properties.setProperty(prefix + key, value.toString())
            }
            put("animeId", anime.id)
            put("title", anime.title)
            put("titleRomaji", anime.titleRomaji)
            put("titleEnglish", anime.titleEnglish)
            put("titleNative", anime.titleNative)
            put("malId", anime.malId)
            put("cover", anime.cover)
            put("color", anime.color)
            put("banner", anime.banner)
            put("format", anime.format)
            put("status", anime.status)
            put("totalEpisodes", anime.episodes)
            put("score", anime.score)
            put("year", anime.year)
            put("season", anime.season)
            put("genres", anime.genres.joinToString("|"))
            put("airingEpisode", anime.nextAiring?.episode)
            put("airingAt", anime.nextAiring?.airingAt)
            put("isAdult", anime.isAdult)
            put("episode", entry.episode)
            put("episodeTitle", entry.episodeTitle)
            put("audio", entry.audio)
            put("path", entry.path.toAbsolutePath().normalize())
            put("bytes", entry.bytes)
        }
        file.parentFile?.let { Files.createDirectories(it.toPath()) }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().use { properties.store(it, "AniLife local episodes") }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            temporary.delete()
            throw error
        }
    }

    private companion object {
        fun downloadsFile(): File {
            val base = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let(::File)
                ?: File(System.getProperty("user.home"), ".local/share")
            return File(base, "AniLife/downloads.properties")
        }
    }
}

internal fun entryKey(entry: LocalEpisode): String = "${entry.anime.id}|${entry.episode}|${entry.audio}"

object MediaAutoDetector {
    private val videoExtensions = setOf(".ts", ".mkv", ".mp4", ".m4v", ".webm", ".avi", ".mov")
    private val episodePatterns = listOf(
        Regex("(?:^|[\\s._\\-\\[(])(?:S\\d+)?E(?:p(?:isode)?)?[\\s._]?(\\d{1,4}(?:\\.5)?)(?!\\d)", RegexOption.IGNORE_CASE),
        Regex("-\\s*(\\d{1,4}(?:\\.5)?)(?:\\s|v\\d|\\[|\\(|$)", RegexOption.IGNORE_CASE),
        Regex("(\\d{1,4}(?:\\.5)?)\\s*$"),
    )

    fun episodeNumber(fileName: String): Double? {
        val stem = fileName.substringBeforeLast('.', fileName)
        return episodePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(stem)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        }
    }

    suspend fun scan(
        root: Path,
        audio: String,
        search: suspend (String) -> List<AnimeCard>,
        onProgress: (String, String) -> Unit = { _, _ -> },
    ): AutoDetectResult {
        val normalizedRoot = root.toAbsolutePath().normalize()
        require(Files.isDirectory(normalizedRoot)) { "Choose a folder containing anime folders." }
        val folders = Files.list(normalizedRoot).use { paths ->
            paths.filter(Files::isDirectory).sorted().toList()
        }
        val plans = mutableListOf<DetectedFolder>()
        val skipped = mutableListOf<String>()
        for (folder in folders) {
            val name = folder.fileName.toString()
            val mediaFiles = Files.list(folder).use { paths ->
                paths.filter(Files::isRegularFile)
                    .filter { path -> videoExtensions.any { path.fileName.toString().endsWith(it, ignoreCase = true) } }
                    .sorted()
                    .toList()
            }
            val episodes = mediaFiles.mapNotNull { path ->
                val number = episodeNumber(path.fileName.toString())
                if (number == null) {
                    skipped += "$name/${path.fileName}: episode number not found"
                    null
                } else {
                    LocalEpisode(
                        anime = AnimeCard(id = 0, title = name),
                        episode = number,
                        audio = audio,
                        path = path.toAbsolutePath().normalize(),
                        bytes = Files.size(path),
                    )
                }
            }.sortedBy { it.episode }
            val seasonMatch = Regex("S(\\d{1,2})", RegexOption.IGNORE_CASE).find(name)
            val season = seasonMatch?.groupValues?.get(1)?.padStart(2, '0') ?: "01"
            val firstEp = episodes.firstOrNull()?.episode
            val epLabel = if (firstEp == null) "" else {
                val epStr = if (firstEp == firstEp.toInt().toDouble()) firstEp.toInt().toString().padStart(2, '0')
                else firstEp.toString()
                if (episodes.size > 1) "S$season|E$epStr-${episodes.last().episode.toInt().toString().padStart(2,'0')}"
                else "S$season|E$epStr"
            }
            onProgress(name, epLabel)
            if (episodes.isEmpty()) {
                if (mediaFiles.isEmpty()) skipped += "$name: no supported video files"
                continue
            }
            val candidates = search(name.replace(Regex("[_.]+"), " "))
                .distinctBy { it.id }
                .map { it to matchScore(name, it) }
                .sortedByDescending { it.second }
            if (candidates.isEmpty()) {
                skipped += "$name: no AniList matches"
                continue
            }
            val bestScore = candidates.first().second
            plans += DetectedFolder(
                name = name,
                matches = candidates.take(5).map { it.first },
                confidence = bestScore,
                episodes = episodes,
            )
            delay(750)
        }
        return AutoDetectResult(plans, skipped)
    }

    fun matchScore(folder: String, anime: AnimeCard): Double {
        val folderName = normalize(folder)
        val names = (listOfNotNull(anime.title, anime.titleRomaji) + anime.aliases)
            .map(::normalize)
            .filter(String::isNotBlank)
        val folderWords = folderName.split(' ').filter(String::isNotBlank).toSet()
        val best = names.maxOfOrNull { candidate ->
            if (folderName == candidate) 1.0 else {
                val candidateWords = candidate.split(' ').filter(String::isNotBlank).toSet()
                val union = folderWords union candidateWords
                if (union.isEmpty()) 0.0 else (folderWords intersect candidateWords).size.toDouble() / union.size
            }
        } ?: 0.0
        return if (names.any { seasonNumber(it) == seasonNumber(folderName) }) best else best * 0.8
    }

    private fun normalize(value: String): String = value.lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun seasonNumber(value: String): Int {
        val normalized = normalize(value)
        val match = Regex("\\bseason (\\d+)\\b|\\b(\\d+)(?:st|nd|rd|th) season\\b|\\bpart (\\d+)\\b").find(normalized)
        return match?.groupValues?.drop(1)?.firstOrNull(String::isNotBlank)?.toIntOrNull() ?: 1
    }
}

object PortableLibraryImporter {
    fun read(jsonFile: Path, mediaRoot: Path): PortableImportResult {
        val root = Json.parseToJsonElement(Files.readString(jsonFile)).jsonObject
        require(root["AniLife_sync"]?.jsonPrimitive?.intOrNull == 1) {
            "This isn't an AniLife portable library JSON file."
        }
        val anime = root["anime"]?.asArray().orEmpty().mapNotNull { value ->
            val objectValue = value as? JsonObject ?: return@mapNotNull null
            val cardElement = objectValue["card"] ?: return@mapNotNull null
            val card = if (cardElement is JsonPrimitive && cardElement.isString) {
                Json.parseToJsonElement(cardElement.content) as? JsonObject
                    ?: error("An anime card in the portable library isn't a JSON object.")
            } else {
                cardElement as? JsonObject ?: error("An anime card in the portable library isn't a JSON object.")
            }
            parseCard(card)
        }.distinctBy { it.id }
        val byId = anime.associateBy { it.id }
        val safeRoot = mediaRoot.toAbsolutePath().normalize().toRealPath()
        val missing = mutableListOf<String>()
        val episodes = root["downloads"]?.asArray().orEmpty().mapNotNull { value ->
            val record = value as? JsonObject ?: return@mapNotNull null
            if (record.string("status") != "done") return@mapNotNull null
            val animeId = record.long("anime_id") ?: return@mapNotNull null
            val relative = record.string("relPath")?.replace('\\', '/') ?: return@mapNotNull null
            val relativePath = Path.of(relative)
            if (relativePath.isAbsolute || relativePath.any { it.toString() == ".." }) {
                missing += "$relative (unsafe relative path skipped)"
                return@mapNotNull null
            }
            val candidate = safeRoot.resolve(relativePath).normalize()
            if (!candidate.startsWith(safeRoot) || !Files.isRegularFile(candidate)) {
                missing += candidate.toString()
                return@mapNotNull null
            }
            val realPath = candidate.toRealPath()
            if (!realPath.startsWith(safeRoot)) {
                missing += "$relative (path escapes the selected media folder)"
                return@mapNotNull null
            }
            val title = record.string("title") ?: byId[animeId]?.title ?: "Anime $animeId"
            val card = byId[animeId] ?: AnimeCard(id = animeId, title = title)
            val episode = record.double("episode") ?: return@mapNotNull null
            val audio = record.string("audio")?.lowercase()?.takeIf { it == "sub" || it == "dub" } ?: "sub"
            LocalEpisode(card, episode, audio, realPath, Files.size(realPath))
        }
        return PortableImportResult(anime, episodes, missing)
    }

    private fun parseCard(value: JsonObject): AnimeCard? {
        val id = value.long("id") ?: return null
        val title = value.string("title")?.takeIf(String::isNotBlank) ?: return null
        val airing = value["nextAiring"] as? JsonObject
        return AnimeCard(
            id = id,
            title = title,
            titleRomaji = value.string("titleRomaji"),
            titleEnglish = value.string("titleEnglish"),
            malId = value.long("malId"),
            cover = value.string("cover"),
            color = value.string("color"),
            banner = value.string("banner"),
            format = value.string("format"),
            status = value.string("status"),
            episodes = value.int("episodes"),
            score = value.double("score"),
            year = value.int("year"),
            season = value.string("season"),
            genres = value["genres"]?.asArray()?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
            nextAiring = airing?.let { card ->
                val episode = card.int("episode") ?: return@let null
                val airingAt = card.long("airingAt") ?: return@let null
                NextAiring(episode, airingAt)
            },
            isAdult = value.boolean("isAdult") ?: false,
        )
    }

    private fun JsonElement.asArray(): JsonArray? = this as? JsonArray
    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNullSafe()
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.contentOrNullSafe()?.toLongOrNull()
    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
    private fun JsonPrimitive.contentOrNullSafe(): String? = if (this == JsonNull) null else content
}

object DirectMediaDownloader {
    private val videoExtensions = setOf(".ts", ".mkv", ".mp4", ".m4v", ".webm", ".avi", ".mov")
    private val client by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    suspend fun download(
        url: String,
        animeTitle: String,
        episode: Double,
        audio: String,
        downloadRoot: Path = defaultDownloadDirectory(),
        anime: AnimeCard? = null,
        episodeTitle: String? = null,
    ): LocalEpisode =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            require(animeTitle.isNotBlank()) { "Enter the anime title." }
            require(episode > 0.0 && episode.isFinite()) { "Enter a valid episode number." }
            val uri = URI(url.trim())
            require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
                "Only a direct HTTP(S) video-file URL can be downloaded. Use Play for HLS/DASH streams."
            }
            require(!uri.host.isNullOrBlank()) { "The download URL must include a host name." }
            val remoteName = Path.of(uri.path.orEmpty()).fileName?.toString().orEmpty()
            val extension = remoteName.substringAfterLast('.', "")
                .takeIf { remoteName.contains('.') }
                ?.let { ".$it" }?.lowercase().orEmpty()
            require(extension in videoExtensions) {
                "The URL must point directly to a video file (.mp4, .mkv, .webm, .ts, and similar)."
            }

            val directory = downloadRoot.resolve(safeName(animeTitle)).toFile()
            Files.createDirectories(directory.toPath())
            val episodeLabel = if (episode % 1.0 == 0.0) episode.toInt().toString() else episode.toString()
            val safeEpisodeTitle = episodeTitle?.takeIf(String::isNotBlank)?.let { " - ${safeName(it)}" }.orEmpty()
            val destination = File(directory, "${safeName(animeTitle)} - E$episodeLabel$safeEpisodeTitle.${extension.removePrefix(".")}")
            val temporary = File(directory, "${destination.name}.${java.util.UUID.randomUUID()}.part")
            try {
                val request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(30))
                    .header("User-Agent", "AniLife/1.0")
                    .header("Accept", "video/*,application/octet-stream;q=0.9,*/*;q=0.1")
                    .GET()
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary.toPath()))
                require(response.statusCode() in 200..299) { "The video server returned HTTP ${response.statusCode()}." }
                require(Files.size(temporary.toPath()) > 0L) { "The video server returned an empty file." }
                try {
                    Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                val stableId = anime?.id ?: (java.util.UUID.nameUUIDFromBytes(animeTitle.trim().lowercase().toByteArray()).mostSignificantBits and Long.MAX_VALUE)
                    .coerceAtLeast(1L)
                LocalEpisode(
                    anime = anime ?: AnimeCard(id = stableId, title = animeTitle.trim()),
                    episode = episode,
                    audio = if (audio == "dub") "dub" else "sub",
                    path = destination.toPath(),
                    bytes = Files.size(destination.toPath()),
                    episodeTitle = episodeTitle?.takeIf(String::isNotBlank),
                )
            } catch (error: Throwable) {
                Files.deleteIfExists(temporary.toPath())
                throw error
            }
        }

    private fun safeName(value: String): String = value
        .replace(Regex("[<>:\"/\\\\|?*\\p{Cntrl}]"), "_")
        .trim()
        .trimEnd('.', ' ')
        .take(100)
        .ifBlank { "Anime" }

    fun defaultDownloadDirectory(): Path {
        val base = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let(::File)
            ?: File(System.getProperty("user.home"), ".local/share")
        return File(base, "AniLife/Downloads").toPath()
    }
}

