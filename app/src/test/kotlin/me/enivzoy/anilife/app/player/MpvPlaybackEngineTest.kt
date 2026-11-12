package me.enivzoy.anilife.app.player

import me.enivzoy.anilife.mpv.Mpv
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MpvPlaybackEngineTest {

    @Test
    fun reportsRealPlaybackStateAndRespondsToTransportControls() {
        val library = Mpv.defaultLibraryPath()
        val clip = File("src/main/resources/anilife-intro.mp4")
        if (library == null || !File(library).isFile || !clip.isFile) {
            println("libmpv or the intro clip is unavailable; skipping playback engine test")
            return
        }

        val engine = MpvPlaybackEngine(
            url = clip.absolutePath,
            fallbackDurationSeconds = 60.0,
            renderWidth = 320,
            renderHeight = 180,
        )
        engine.start()
        try {
            assertTrue(await(15_000L) { engine.videoFrame != null }, "expected a decoded video frame")
            assertTrue(await(15_000L) { engine.positionSeconds > 0.2 }, "expected the mpv clock to advance")
            assertTrue(engine.durationSeconds in 1.0..30.0, "unexpected duration ${engine.durationSeconds}")
            assertTrue(engine.isPlaying, "mpv should be playing after load")

            engine.seekTo(1.0)
            assertTrue(await(5_000L) { engine.positionSeconds in 0.9..4.0 }, "seek did not move the clock")

            engine.togglePause()
            assertTrue(await(3_000L) { !engine.isPlaying }, "pause was not reflected in the engine")

            engine.togglePause()
            assertTrue(await(3_000L) { engine.isPlaying }, "play was not reflected in the engine")

            val before = engine.positionSeconds
            engine.seekRelative(1.5)
            assertTrue(await(3_000L) { engine.positionSeconds >= before }, "relative seek moved backwards")
        } finally {
            engine.close()
        }
    }

    @Test
    fun reportsAFailureInsteadOfStayingOnTheLoadingState() {
        val engine = MpvPlaybackEngine(
            url = "file:///definitely-not-a-video.mp4",
            renderWidth = 160,
            renderHeight = 90,
        )
        engine.start()
        try {
            // Either libmpv is absent (the engine reports a load error) or it is present and
            // rejects the file; both must stop the chrome from claiming to buffer forever.
            assertTrue(
                await(15_000L) { engine.error != null || !engine.isLoading },
                "the engine stayed in the loading state for a missing file",
            )
        } finally {
            engine.close()
        }
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(25L)
        }
        return condition()
    }
}

