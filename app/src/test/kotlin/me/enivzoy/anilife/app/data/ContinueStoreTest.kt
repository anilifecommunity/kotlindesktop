package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.core.model.AnimeCard
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContinueStoreTest {
    private fun anime(id: Long = 7) = AnimeCard(id = id, title = "Test show")

    @Test
    fun theSavedPositionIsOfferedBackThreeSeconds() {
        val root = Files.createTempDirectory("AniLife-continue")
        try {
            val store = ContinueStore(root.resolve("continue.properties").toFile())
            store.upsert(anime(), episode = 3.0, position = 300.0, duration = 1440.0)

            assertEquals(297.0, store.positionFor(7, 3.0))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFinishedOrUnstartedEpisodeIsNotResumed() {
        val root = Files.createTempDirectory("AniLife-continue-finished")
        try {
            val store = ContinueStore(root.resolve("continue.properties").toFile())
            store.upsert(anime(), episode = 3.0, position = 1400.0, duration = 1440.0)

            // Past the point where an episode counts as watched.
            assertNull(store.positionFor(7, 3.0))
            // Another episode of the same title.
            assertNull(store.positionFor(7, 4.0))
            // Another title.
            assertNull(store.positionFor(8, 3.0))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun anEpisodeBarelyStartedIsNotResumed() {
        val root = Files.createTempDirectory("AniLife-continue-start")
        try {
            val store = ContinueStore(root.resolve("continue.properties").toFile())
            store.upsert(anime(), episode = 1.0, position = 4.0, duration = 1440.0)

            assertNull(store.positionFor(7, 1.0))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
