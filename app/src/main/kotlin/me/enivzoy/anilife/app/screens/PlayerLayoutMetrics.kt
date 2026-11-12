package me.enivzoy.anilife.app.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal data class PlayerLayoutMetrics(
    val horizontalPadding: Dp,
    val barTopPadding: Dp,
    val centerGap: Dp,
    val sideButtonSize: Dp,
    val sideGlyphSize: Dp,
    val playButtonSize: Dp,
    val playGlyphSize: Dp,
    val seekTrackHeight: Dp,
    val seekTouchHeight: Dp,
    val timeTextSize: TextUnit,
    val titleTextSize: TextUnit,
    val showPillLabels: Boolean,
    val compactHeight: Boolean,
)

internal fun playerLayoutMetrics(widthDp: Int, heightDp: Int): PlayerLayoutMetrics {
    val compactHeight = heightDp < 300
    return when {
        widthDp >= 1400 -> PlayerLayoutMetrics(
            horizontalPadding = 28.dp,
            barTopPadding = 26.dp,
            centerGap = 28.dp,
            sideButtonSize = 48.dp,
            sideGlyphSize = 20.dp,
            playButtonSize = 72.dp,
            playGlyphSize = 32.dp,
            seekTrackHeight = 6.dp,
            seekTouchHeight = 34.dp,
            timeTextSize = 15.sp,
            titleTextSize = 24.sp,
            showPillLabels = true,
            compactHeight = compactHeight,
        )
        widthDp >= 900 -> PlayerLayoutMetrics(
            horizontalPadding = 22.dp,
            barTopPadding = 24.dp,
            centerGap = 24.dp,
            sideButtonSize = 48.dp,
            sideGlyphSize = 20.dp,
            playButtonSize = 72.dp,
            playGlyphSize = 32.dp,
            seekTrackHeight = 5.dp,
            seekTouchHeight = 30.dp,
            timeTextSize = 14.sp,
            titleTextSize = 21.sp,
            showPillLabels = true,
            compactHeight = compactHeight,
        )
        widthDp >= 500 -> PlayerLayoutMetrics(
            horizontalPadding = 16.dp,
            barTopPadding = if (compactHeight) 8.dp else 24.dp,
            centerGap = 16.dp,
            sideButtonSize = 44.dp,
            sideGlyphSize = 18.dp,
            playButtonSize = 60.dp,
            playGlyphSize = 28.dp,
            seekTrackHeight = 4.dp,
            seekTouchHeight = 28.dp,
            timeTextSize = 13.sp,
            titleTextSize = 18.sp,
            showPillLabels = false,
            compactHeight = compactHeight,
        )
        else -> PlayerLayoutMetrics(
            horizontalPadding = 8.dp,
            barTopPadding = if (compactHeight) 8.dp else 20.dp,
            centerGap = 10.dp,
            sideButtonSize = 40.dp,
            sideGlyphSize = 16.dp,
            playButtonSize = 52.dp,
            playGlyphSize = 24.dp,
            seekTrackHeight = 4.dp,
            seekTouchHeight = 26.dp,
            timeTextSize = 12.sp,
            titleTextSize = 16.sp,
            showPillLabels = false,
            compactHeight = compactHeight,
        )
    }
}

