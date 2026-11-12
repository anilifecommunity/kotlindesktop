package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.core.model.AnimeCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

data class ContinueProgress(
    val episode: Double,
    val position: Double,
    val duration: Double,
)

data class ContinueEntry(
    val anime: AnimeCard,
    val progress: ContinueProgress,
    val updatedAt: Long,
)

class ContinueStore(private val file: File = continueFile()) {
    private val lock = Any()
    private val mutableEntries = MutableStateFlow(readEntries())
    val entries: StateFlow<List<ContinueEntry>> = mutableEntries.asStateFlow()

    fun upsert(anime: AnimeCard, episode: Double, position: Double, duration: Double) {
        if (duration <= 0.0) return
        val entry = ContinueEntry(
            anime = anime,
            progress = ContinueProgress(episode, position.coerceIn(0.0, duration), duration),
            updatedAt = System.currentTimeMillis(),
        )
        synchronized(lock) {
            val updated = (mutableEntries.value.filterNot { it.anime.id == anime.id } + entry)
                .sortedByDescending { it.updatedAt }
                .take(24)
            writeEntries(updated)
            mutableEntries.value = updated
        }
    }

    fun remove(id: Long) = synchronized(lock) {
        val updated = mutableEntries.value.filterNot { it.anime.id == id }
        if (updated.size != mutableEntries.value.size) {
            writeEntries(updated)
            mutableEntries.value = updated
        }
    }

    fun clear() = synchronized(lock) {
        writeEntries(emptyList())
        mutableEntries.value = emptyList()
    }

    private fun readEntries(): List<ContinueEntry> {
        if (!file.isFile) return emptyList()
        val properties = Properties()
        file.inputStream().use { properties.load(it) }
        return properties.stringPropertyNames()
            .asSequence()
            .filter { it.startsWith("entry.") && it.endsWith(".id") }
            .mapNotNull { key ->
                val prefix = key.removeSuffix("id")
                val id = properties.getProperty(key)?.toLongOrNull() ?: return@mapNotNull null
                fun value(name: String) = properties.getProperty("${prefix}${name}")?.takeIf(String::isNotBlank)
                val title = value("title") ?: return@mapNotNull null
                val episode = value("episode")?.toDoubleOrNull() ?: return@mapNotNull null
                val position = value("position")?.toDoubleOrNull() ?: 0.0
                val duration = value("duration")?.toDoubleOrNull() ?: 0.0
                val updatedAt = value("updatedAt")?.toLongOrNull() ?: 0L
                if (duration <= 0.0) return@mapNotNull null
                val anime = AnimeCard(
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
                    episodes = value("episodes")?.toIntOrNull(),
                    score = value("score")?.toDoubleOrNull(),
                    year = value("year")?.toIntOrNull(),
                    season = value("season"),
                    genres = value("genres")?.split('|')?.filter(String::isNotBlank).orEmpty(),
                    isAdult = value("isAdult")?.toBooleanStrictOrNull() ?: false,
                )
                ContinueEntry(anime, ContinueProgress(episode, position, duration), updatedAt)
            }
            .sortedByDescending { it.updatedAt }
            .toList()
    }

    private fun writeEntries(entries: List<ContinueEntry>) {
        val properties = Properties()
        entries.forEach { e ->
            val a = e.anime
            val prefix = "entry.${a.id}."
            fun put(name: String, value: Any?) {
                if (value != null) properties.setProperty(prefix + name, value.toString())
            }
            put("id", a.id)
            put("title", a.title)
            put("titleRomaji", a.titleRomaji)
            put("titleEnglish", a.titleEnglish)
            put("titleNative", a.titleNative)
            put("malId", a.malId)
            put("cover", a.cover)
            put("color", a.color)
            put("banner", a.banner)
            put("format", a.format)
            put("status", a.status)
            put("episodes", a.episodes)
            put("score", a.score)
            put("year", a.year)
            put("season", a.season)
            put("genres", a.genres.joinToString("|"))
            put("isAdult", a.isAdult)
            put("episode", e.progress.episode)
            put("position", e.progress.position)
            put("duration", e.progress.duration)
            put("updatedAt", e.updatedAt)
        }
        file.parentFile?.let { java.nio.file.Files.createDirectories(it.toPath()) }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().use { properties.store(it, "AniLife continue watching") }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            temporary.delete()
            throw error
        }
    }

    companion object {
        private fun continueFile(): File {
            val base = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
                ?: System.getProperty("user.home")
            return File(base, "AniLife/continue.properties")
        }
    }
}

val ContinueStoreInstance: ContinueStore = ContinueStore()