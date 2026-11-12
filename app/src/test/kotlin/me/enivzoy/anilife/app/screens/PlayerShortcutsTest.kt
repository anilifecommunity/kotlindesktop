package me.enivzoy.anilife.app.screens

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerShortcutsTest {
    private class Recorder {
        var pauses = 0
        val seeks = mutableListOf<Double>()
        val volumes = mutableListOf<Int>()
        var mutes = 0
        var fullscreens = 0
        val speeds = mutableListOf<Float>()
        val delays = mutableListOf<Double>()
        var skips = 0
        var next = 0
        var previous = 0
    }

    private fun shortcuts(recorded: Recorder) = PlayerShortcuts(
        onTogglePause = { recorded.pauses += 1 },
        onSeekRelative = { recorded.seeks += it },
        onVolumeChange = { recorded.volumes += it },
        onToggleMute = { recorded.mutes += 1 },
        onToggleFullscreen = { recorded.fullscreens += 1 },
        onSpeedChange = { recorded.speeds += it },
        onSubtitleDelay = { recorded.delays += it },
        onSkip = { recorded.skips += 1 },
        onNext = { recorded.next += 1 },
        onPrevious = { recorded.previous += 1 },
    )

    private fun press(recorded: Recorder, key: Key) {
        playerShortcutFor(key)?.let { applyPlayerShortcut(it, shortcuts(recorded)) }
    }

    @Test
    fun spaceAndKPlayAndPause() {
        val recorded = Recorder()

        press(recorded, Key.Spacebar)
        press(recorded, Key.K)

        assertEquals(2, recorded.pauses)
    }

    @Test
    fun theArrowsSeekAndChangeTheVolume() {
        val recorded = Recorder()

        press(recorded, Key.DirectionLeft)
        press(recorded, Key.DirectionRight)
        press(recorded, Key.J)
        press(recorded, Key.L)
        press(recorded, Key.DirectionUp)
        press(recorded, Key.DirectionDown)

        assertEquals(listOf(-5.0, 5.0, -10.0, 10.0), recorded.seeks)
        assertEquals(listOf(5, -5), recorded.volumes)
    }

    @Test
    fun theOtherKeysDoWhatTheySay() {
        val recorded = Recorder()

        press(recorded, Key.M)
        press(recorded, Key.F)
        press(recorded, Key.Z)
        press(recorded, Key.X)
        press(recorded, Key.LeftBracket)
        press(recorded, Key.RightBracket)
        press(recorded, Key.Enter)
        press(recorded, Key.N)
        press(recorded, Key.P)

        assertEquals(1, recorded.mutes)
        assertEquals(1, recorded.fullscreens)
        assertEquals(listOf(-0.1, 0.1), recorded.delays)
        assertEquals(listOf(-0.25f, 0.25f), recorded.speeds)
        assertEquals(1, recorded.skips)
        assertEquals(1, recorded.next)
        assertEquals(1, recorded.previous)
    }

    @Test
    fun anUnknownKeyIsNotAShortcut() {
        assertNull(playerShortcutFor(Key.Q))
    }
}
