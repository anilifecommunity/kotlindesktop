package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.NextAiring
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

 
class LocalLibraryStore(private val file: File = libraryFile()) {
    private val lock = Any()
    private val mutableEntries = MutableStateFlow(readEntries())
    val entries = mutableEntries.asStateFlow()

    fun add(anime: AnimeCard) = synchronized(lock) {
        addAll(listOf(anime))
    }

    fun addAll(anime: List<AnimeCard>) = synchronized(lock) {
        if (anime.isEmpty()) return@synchronized
        val updated = (mutableEntries.value.filterNot { existing -> anime.any { it.id == existing.id } } + anime)
            .sortedBy { it.title.lowercase() }
        writeEntries(updated)
        mutableEntries.value = updated
    }

    fun setTitleLanguage(language: String) = synchronized(lock) {
        val updated = mutableEntries.value
            .map { anime -> anime.withTitlePreference(language) }
            .sortedBy { it.title.lowercase() }
        if (updated != mutableEntries.value) {
            writeEntries(updated)
            mutableEntries.value = updated
        }
    }

    fun setPreferEnglishTitles(preferEnglish: Boolean) = setTitleLanguage(if (preferEnglish) "english" else "romaji")

    fun remove(id: Long) = synchronized(lock) {
        val updated = mutableEntries.value.filterNot { it.id == id }
        if (updated.size != mutableEntries.value.size) {
            writeEntries(updated)
            mutableEntries.value = updated
        }
    }

    private fun readEntries(): List<AnimeCard> {
        if (!file.isFile) return emptyList()
        val properties = Properties()
        file.inputStream().use(properties::load)
        return properties.stringPropertyNames()
            .asSequence()
            .filter { it.startsWith("anime.") && it.endsWith(".title") }
            .mapNotNull { titleKey ->
                val prefix = titleKey.removeSuffix("title")
                val id = prefix.removePrefix("anime.").removeSuffix(".").toLongOrNull() ?: return@mapNotNull null
                val title = properties.getProperty(titleKey)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                fun value(name: String) = properties.getProperty("${prefix}${name}")?.takeIf(String::isNotBlank)
                fun intValue(name: String) = value(name)?.toIntOrNull()
                fun doubleValue(name: String) = value(name)?.toDoubleOrNull()
                val airingEpisode = intValue("airingEpisode")
                val airingAt = value("airingAt")?.toLongOrNull()
                AnimeCard(
                    id = id,
                    title = title,
                    titleRomaji = value("titleRomaji"),
                    titleEnglish = value("titleEnglish"),
                    titleNative = value("titleNative"),
                    malId = value("malId")?.toLongOrNull(),
                    cover = value("cover"),
                    color = value("color"),
                    banner = value("banner"),
                    format = value("format"),
                    status = value("status"),
                    episodes = intValue("episodes"),
                    score = doubleValue("score"),
                    year = intValue("year"),
                    season = value("season"),
                    genres = value("genres")?.split('|')?.filter(String::isNotBlank).orEmpty(),
                    nextAiring = if (airingEpisode != null && airingAt != null) {
                        NextAiring(airingEpisode, airingAt)
                    } else {
                        null
                    },
                    isAdult = value("isAdult")?.toBooleanStrictOrNull() ?: false,
                )
            }
            .sortedBy { it.title.lowercase() }
            .toList()
    }

    private fun writeEntries(entries: List<AnimeCard>) {
        val properties = Properties()
        entries.forEach { anime ->
            val prefix = "anime.${anime.id}."
            fun put(name: String, value: Any?) {
                if (value != null) properties.setProperty(prefix + name, value.toString())
            }
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
            put("episodes", anime.episodes)
            put("score", anime.score)
            put("year", anime.year)
            put("season", anime.season)
            put("genres", anime.genres.joinToString("|"))
            put("airingEpisode", anime.nextAiring?.episode)
            put("airingAt", anime.nextAiring?.airingAt)
            put("isAdult", anime.isAdult)
        }

        file.parentFile?.let { Files.createDirectories(it.toPath()) }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().use { properties.store(it, "AniLife local library") }
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            temporary.delete()
            throw error
        }
    }

    private companion object {
        fun libraryFile(): File {
            val appData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
            val base = appData?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
            return File(base, "AniLife/library.properties")
        }
    }
}

