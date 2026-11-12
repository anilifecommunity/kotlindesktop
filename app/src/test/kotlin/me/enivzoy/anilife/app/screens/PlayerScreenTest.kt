package me.enivzoy.anilife.app.screens

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerScreenTest {
    @Test
    fun estimatesEpisodeEndFromLocalSystemTimeAndWrapsMidnight() {
        assertEquals("3:05", estimateEpisodeEndTime(LocalTime.of(2, 39), 26))
        assertEquals("0:20", estimateEpisodeEndTime(LocalTime.of(23, 50), 30))
        assertNull(estimateEpisodeEndTime(LocalTime.NOON, 0))
    }

    @Test
    fun transportMovesToTheAdjacentEpisode() {
        val episodes = listOf(9.0, 7.0, 8.0, 8.0, 10.0, 10.5)

        assertEquals(7.0, previousEpisodeNumber(episodes, 8.0))
        assertEquals(9.0, nextEpisodeNumber(episodes, 8.0))
        assertEquals(10.0, previousEpisodeNumber(episodes, 10.5))
        assertNull(previousEpisodeNumber(episodes, 7.0))
        assertNull(nextEpisodeNumber(episodes, 10.5))
        assertNull(previousEpisodeNumber(episodes, null))
        assertNull(nextEpisodeNumber(episodes, null))
    }

    @Test
    fun episodeLabelsDropTheDecimalForWholeNumbers() {
        assertEquals("8", formatEpisodeNumber(8.0))
        assertEquals("8.5", formatEpisodeNumber(8.5))
    }
}

