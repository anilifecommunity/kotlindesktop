package me.enivzoy.anilife.app.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoticeStoreTest {
    private fun store(root: java.nio.file.Path) = NoticeStore(root.resolve("notices.properties").toFile())

    @Test
    fun noticesAreKeptNewestFirstAndSurviveARestart() {
        val root = Files.createTempDirectory("AniLife-notices")
        try {
            val file = root.resolve("notices.properties").toFile()
            val store = NoticeStore(file)
            store.add(kind = "airing", title = "Frieren", body = "Episode 5 is out", animeId = 1, createdAt = 1_000L)
            store.add(kind = "download", title = "Frieren", body = "Episode 4 is ready offline", animeId = 1, createdAt = 2_000L)

            val notices = NoticeStore(file).notices.value
            assertEquals(2, notices.size)
            assertEquals("download", notices.first().kind)
            assertEquals("airing", notices.last().kind)
            assertFalse(notices.first().read)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun anEpisodeIsAnnouncedOnlyOnce() {
        val root = Files.createTempDirectory("AniLife-notices-once")
        try {
            val store = store(root)
            assertFalse(store.hasAnnounced(1, 5))
            store.add(kind = "airing", title = "Frieren", body = "Episode 5 is out", animeId = 1)
            assertTrue(store.hasAnnounced(1, 5))
            assertFalse(store.hasAnnounced(1, 6))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun markingAndClearingTheLog() {
        val root = Files.createTempDirectory("AniLife-notices-clear")
        try {
            val store = store(root)
            store.add(kind = "airing", title = "Frieren", body = "Episode 5 is out", animeId = 1)
            store.markAllRead()
            assertTrue(store.notices.value.all { it.read })
            store.clear()
            assertTrue(store.notices.value.isEmpty())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
