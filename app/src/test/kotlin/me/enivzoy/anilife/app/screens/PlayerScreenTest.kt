package me.enivzoy.anilife.app.screens

import me.enivzoy.anilife.core.model.SkipTimes
import me.enivzoy.anilife.core.model.Window
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

    @Test
    fun theRecapWinsOverTheOpeningWhenTheyTouch() {
        val skip = SkipTimes(
            intro = Window(62.0, 152.0),
            recap = Window(0.0, 62.0),
            outro = Window(1300.0, 1390.0),
        )

        assertEquals("recap", activeSkipWindow(skip, 30.0, 1440.0)?.kind)
        assertEquals("intro", activeSkipWindow(skip, 100.0, 1440.0)?.kind)
        assertNull(activeSkipWindow(skip, 500.0, 1440.0))
    }

    @Test
    fun anOutroOnlyCountsWhenTheCreditsReachTheLastQuarter() {
        val early = SkipTimes(outro = Window(120.0, 300.0))
        val credits = SkipTimes(outro = Window(1300.0, 1390.0))

        assertNull(activeSkipWindow(early, 200.0, 1440.0))
        assertEquals("outro", activeSkipWindow(credits, 1350.0, 1440.0)?.kind)
    }

    @Test
    fun skippingAnOutroLandsOnTheEndOfTheEpisode() {
        val window = PlayerSkipWindow("outro", "Skip Outro", Window(1300.0, 1390.0))

        assertEquals(1390.0, skipTarget(window, 1440.0))
        // An outro filed past the end of the file lands on the end.
        assertEquals(1400.0, skipTarget(window, 1400.0))
        assertEquals(1390.0, skipTarget(PlayerSkipWindow("intro", "Skip Intro", Window(0.0, 90.0)), 1400.0))
    }
}

