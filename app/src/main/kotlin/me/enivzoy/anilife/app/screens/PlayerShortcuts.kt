package me.enivzoy.anilife.app.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * What the player's keyboard shortcuts do, gathered so the key handler stays a lookup table.
 */
internal data class PlayerShortcuts(
    val onTogglePause: () -> Unit,
    val onSeekRelative: (Double) -> Unit,
    val onVolumeChange: (Int) -> Unit,
    val onToggleMute: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onSpeedChange: (Float) -> Unit,
    val onSubtitleDelay: (Double) -> Unit,
    val onSkip: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
)

/** The player's keyboard shortcuts, as the reference player answers to them. */
internal enum class PlayerShortcut {
    PlayPause,
    SeekBackFive,
    SeekForwardFive,
    SeekBackTen,
    SeekForwardTen,
    VolumeUp,
    VolumeDown,
    Mute,
    Fullscreen,
    SubtitleEarlier,
    SubtitleLater,
    Slower,
    Faster,
    Skip,
    NextEpisode,
    PreviousEpisode,
}

/**
 * The shortcut a key stands for, or null when the key is not one of them. Space or K plays and
 * pauses, the arrows seek and change the volume, J and L jump ten seconds, M mutes, F goes
 * fullscreen, Z and X shift the subtitles and the brackets change the speed.
 */
internal fun playerShortcutFor(key: Key): PlayerShortcut? = when (key) {
    Key.Spacebar, Key.K -> PlayerShortcut.PlayPause
    Key.DirectionLeft -> PlayerShortcut.SeekBackFive
    Key.DirectionRight -> PlayerShortcut.SeekForwardFive
    Key.J -> PlayerShortcut.SeekBackTen
    Key.L -> PlayerShortcut.SeekForwardTen
    Key.DirectionUp -> PlayerShortcut.VolumeUp
    Key.DirectionDown -> PlayerShortcut.VolumeDown
    Key.M -> PlayerShortcut.Mute
    Key.F -> PlayerShortcut.Fullscreen
    Key.Z -> PlayerShortcut.SubtitleEarlier
    Key.X -> PlayerShortcut.SubtitleLater
    Key.LeftBracket -> PlayerShortcut.Slower
    Key.RightBracket -> PlayerShortcut.Faster
    Key.Enter, Key.NumPadEnter -> PlayerShortcut.Skip
    Key.N -> PlayerShortcut.NextEpisode
    Key.P -> PlayerShortcut.PreviousEpisode
    else -> null
}

/** Runs a shortcut. */
internal fun applyPlayerShortcut(shortcut: PlayerShortcut, shortcuts: PlayerShortcuts) {
    when (shortcut) {
        PlayerShortcut.PlayPause -> shortcuts.onTogglePause()
        PlayerShortcut.SeekBackFive -> shortcuts.onSeekRelative(-5.0)
        PlayerShortcut.SeekForwardFive -> shortcuts.onSeekRelative(5.0)
        PlayerShortcut.SeekBackTen -> shortcuts.onSeekRelative(-10.0)
        PlayerShortcut.SeekForwardTen -> shortcuts.onSeekRelative(10.0)
        PlayerShortcut.VolumeUp -> shortcuts.onVolumeChange(5)
        PlayerShortcut.VolumeDown -> shortcuts.onVolumeChange(-5)
        PlayerShortcut.Mute -> shortcuts.onToggleMute()
        PlayerShortcut.Fullscreen -> shortcuts.onToggleFullscreen()
        PlayerShortcut.SubtitleEarlier -> shortcuts.onSubtitleDelay(-0.1)
        PlayerShortcut.SubtitleLater -> shortcuts.onSubtitleDelay(0.1)
        PlayerShortcut.Slower -> shortcuts.onSpeedChange(-0.25f)
        PlayerShortcut.Faster -> shortcuts.onSpeedChange(0.25f)
        PlayerShortcut.Skip -> shortcuts.onSkip()
        PlayerShortcut.NextEpisode -> shortcuts.onNext()
        PlayerShortcut.PreviousEpisode -> shortcuts.onPrevious()
    }
}

/**
 * Answers a key press with the player's shortcuts. Returns false when the key is not one of them,
 * so the caller can let it through.
 */
internal fun handlePlayerShortcut(event: KeyEvent, shortcuts: PlayerShortcuts): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false
    val shortcut = playerShortcutFor(event.key) ?: return false
    applyPlayerShortcut(shortcut, shortcuts)
    return true
}
