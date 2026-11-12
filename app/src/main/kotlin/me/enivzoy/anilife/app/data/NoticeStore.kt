package me.enivzoy.anilife.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * One entry in the app's own notification log: a new episode of a title in the library, a
 * reminder set for an episode, or a finished download. The log is what the notifications page
 * shows when no account is signed in.
 */
data class Notice(
    val id: Long,
    /** `airing`, `reminder` or `download`. */
    val kind: String,
    val title: String,
    val body: String? = null,
    val animeId: Long? = null,
    val image: String? = null,
    val createdAt: Long,
    val read: Boolean = false,
)

class NoticeStore(private val file: File = noticeFile()) {
    private val lock = Any()
    private val mutableNotices = MutableStateFlow(readNotices())
    val notices: StateFlow<List<Notice>> = mutableNotices.asStateFlow()

    fun add(
        kind: String,
        title: String,
        body: String? = null,
        animeId: Long? = null,
        image: String? = null,
        createdAt: Long = System.currentTimeMillis(),
    ): Notice = synchronized(lock) {
        val notice = Notice(
            id = nextId(),
            kind = kind,
            title = title,
            body = body,
            animeId = animeId,
            image = image,
            createdAt = createdAt,
        )
        val updated = (listOf(notice) + mutableNotices.value).take(MAX_NOTICES)
        writeNotices(updated)
        mutableNotices.value = updated
        notice
    }

    /**
     * Whether an episode of a title has already been announced, so the same alert is never posted
     * twice: the feed and the local alert can both see an episode air.
     */
    fun hasAnnounced(animeId: Long, episode: Int): Boolean = synchronized(lock) {
        val marker = "Episode $episode is out"
        mutableNotices.value.any { it.animeId == animeId && it.body == marker }
    }

    fun markAllRead() = synchronized(lock) {
        val updated = mutableNotices.value.map { it.copy(read = true) }
        if (updated != mutableNotices.value) {
            writeNotices(updated)
            mutableNotices.value = updated
        }
    }

    fun clear() = synchronized(lock) {
        writeNotices(emptyList())
        mutableNotices.value = emptyList()
    }

    private fun nextId(): Long = (mutableNotices.value.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun readNotices(): List<Notice> {
        if (!file.isFile) return emptyList()
        val properties = Properties()
        file.inputStream().use(properties::load)
        val count = properties.getProperty("count")?.toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { index ->
            val prefix = "notice.$index."
            val id = properties.getProperty(prefix + "id")?.toLongOrNull() ?: return@mapNotNull null
            val title = properties.getProperty(prefix + "title")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            Notice(
                id = id,
                kind = properties.getProperty(prefix + "kind") ?: "app",
                title = title,
                body = properties.getProperty(prefix + "body")?.takeIf(String::isNotBlank),
                animeId = properties.getProperty(prefix + "animeId")?.toLongOrNull(),
                image = properties.getProperty(prefix + "image")?.takeIf(String::isNotBlank),
                createdAt = properties.getProperty(prefix + "createdAt")?.toLongOrNull() ?: 0L,
                read = properties.getProperty(prefix + "read")?.toBooleanStrictOrNull() ?: false,
            )
        }.sortedByDescending { it.createdAt }
    }

    private fun writeNotices(notices: List<Notice>) {
        val properties = Properties()
        properties.setProperty("count", notices.size.toString())
        notices.forEachIndexed { index, notice ->
            val prefix = "notice.$index."
            properties.setProperty(prefix + "id", notice.id.toString())
            properties.setProperty(prefix + "kind", notice.kind)
            properties.setProperty(prefix + "title", notice.title)
            notice.body?.let { properties.setProperty(prefix + "body", it) }
            notice.animeId?.let { properties.setProperty(prefix + "animeId", it.toString()) }
            notice.image?.let { properties.setProperty(prefix + "image", it) }
            properties.setProperty(prefix + "createdAt", notice.createdAt.toString())
            properties.setProperty(prefix + "read", notice.read.toString())
        }
        val parent = file.parentFile ?: return
        Files.createDirectories(parent.toPath())
        val temporary = File(parent, "${file.name}.tmp")
        temporary.outputStream().use { properties.store(it, "AniLife notifications") }
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
        private const val MAX_NOTICES = 200

        private fun noticeFile(): File {
            val base = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
                ?.let(::File)
                ?: File(System.getProperty("user.home"), ".local/share")
            return File(base, "AniLife/notices.properties")
        }
    }
}

val NoticeStoreInstance: NoticeStore = NoticeStore()
