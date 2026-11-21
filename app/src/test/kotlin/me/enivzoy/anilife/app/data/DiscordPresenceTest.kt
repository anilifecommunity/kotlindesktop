package me.enivzoy.anilife.app.data

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import xyz.orbitary.perigee.activity.ActivityType

class DiscordPresenceTest {
    private fun playing(
        position: Double = 120.0,
        duration: Double = 1_440.0,
        paused: Boolean = false,
        episode: Double = 3.0,
        total: Int? = 12,
        episodeTitle: String? = null,
        cover: String? = "https://s4.anilist.co/cover.jpg",
    ) = Presence(
        animeId = 42,
        title = "My Series",
        episode = episode,
        episodeTitle = episodeTitle,
        total = total,
        cover = cover,
        position = position,
        duration = duration,
        paused = paused,
    )

    @Test
    fun identityIgnoresClockButTracksCardChanges() {
        val base = playing()
        assertEquals(base.identity(), base.copy(position = 900.0).identity())
        assertNotEquals(base.identity(), base.copy(paused = true).identity())
        assertNotEquals(base.identity(), base.copy(episode = 4.0).identity())
        assertNotEquals(base.identity(), base.copy(episodeTitle = "The News").identity())
        assertNotEquals(base.identity(), base.copy(cover = "https://s4.anilist.co/other-cover.jpg").identity())
    }

    @Test
    fun episodeLabelKeepsHalfEpisodesAndOmitsUnknownTotals() {
        assertEquals("Ep: 3/12", episodeLabel(3.0, 12))
        assertEquals("Ep: 10.5/12", episodeLabel(10.5, 12))
        assertEquals("Ep: 3", episodeLabel(3.0, null))
    }

    @Test
    fun activityRendersEpisodeCounterAndProgressTimestamps() {
        val card = playing(episodeTitle = "The News")
        val activity = buildActivity(card)

        assertEquals(ActivityType.WATCHING, activity.type())
        assertEquals("My Series", activity.details())
        assertEquals("Ep: 3/12 - The News", activity.state())
        // The large image is the vertical AniList cover; the wide splashart gets cropped/scaled.
        assertEquals("https://s4.anilist.co/cover.jpg", activity.assets().largeImage())
        assertEquals("Ep: 3/12 - The News", activity.assets().largeText())
        assertNull(activity.assets().smallImage())
        assertEquals(2, activity.buttons().size)
        assertEquals("View Anime on AniList", activity.buttons()[0].label())
        assertEquals("https://anilist.co/anime/42", activity.buttons()[0].url())
        assertEquals("View Source", activity.buttons()[1].label())
        assertEquals(ANILIFE_REPO_URL, activity.buttons()[1].url())

        val timestamps = assertNotNull(activity.timestamps())
        val start = assertNotNull(timestamps.start())
        val end = assertNotNull(timestamps.end())
        // Bar length is the full episode; the start is offset by the current position.
        assertEquals(1_440, Duration.between(start, end).seconds)
        assertEquals(120, Duration.between(start, java.time.Instant.now()).seconds)
    }

    @Test
    fun pausedActivityDropsTimestampsAndKeepsTextOnlyBadge() {
        val activity = buildActivity(playing(paused = true))

        assertEquals("https://s4.anilist.co/cover.jpg", activity.assets().largeImage())
        assertNull(activity.assets().smallImage())
        assertNull(activity.assets().smallText())
        assertNull(activity.timestamps())
    }

    @Test
    fun largeImageOmitsInvalidUrls() {
        assertNull(buildActivity(playing(cover = null)).assets().largeImage())
        assertNull(buildActivity(playing(cover = " ")).assets().largeImage())
        assertNull(buildActivity(playing(cover = "file:///tmp/cover.jpg")).assets().largeImage())
    }

    @Test
    fun durationlessPlaybackCountsUpWithoutAnEnd() {
        val activity = buildActivity(playing(duration = 0.0, position = 45.0))

        val timestamps = assertNotNull(activity.timestamps())
        assertNotNull(timestamps.start())
        assertNull(timestamps.end())
    }

    @Test
    fun longTitlesAreTrimmedToCodePoints() {
        val longTitle = "あ".repeat(200)
        val activity = buildActivity(playing().copy(title = longTitle))

        assertEquals(128, activity.details()?.codePointCount(0, activity.details()!!.length))
        assertTrue(activity.details()!!.endsWith("…"))
    }
}
