package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
private fun PlayerChromeBox(
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val scale = if (maxWidth >= 400.dp) 1f else (maxWidth / 400.dp).coerceAtLeast(0.75f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            BoxWithConstraints(Modifier.fillMaxSize(), content = content)
        }
    }
}

@Composable
internal fun PlayerControlsScaffold(
    title: String,
    subtitle: String? = null,
    positionSeconds: Double,
    durationSeconds: Double,
    isPlaying: Boolean,
    canControl: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Double) -> Unit,
    topRight: @Composable RowScope.() -> Unit = {},
    bottomLeftActions: @Composable RowScope.(showLabels: Boolean) -> Unit = {},
    bottomActions: @Composable RowScope.(showLabels: Boolean) -> Unit = {},
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    var scrubPosition by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            now = LocalTime.now()
        }
    }
    val shownPosition = scrubPosition ?: positionSeconds
    val remaining = (durationSeconds - shownPosition).coerceAtLeast(0.0)
    val endsAt = if (durationSeconds > 0.0 && remaining > 0.0) {
        now.plusSeconds(remaining.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
    } else null

    PlayerChromeBox(Modifier.fillMaxSize()) {
        val metrics = playerLayoutMetrics(maxWidth.value.toInt(), maxHeight.value.toInt())
        val showActionLabels = metrics.showPillLabels
        val centerGap = metrics.centerGap
        val sideButton = metrics.sideButtonSize
        val sideGlyph = metrics.sideGlyphSize
        val playButton = metrics.playButtonSize
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.76f), Color.Transparent))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.96f)))),
        )

        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = metrics.horizontalPadding, vertical = metrics.barTopPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            PlayerIconButton(onClick = onBack, contentDescription = "Back") {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("NOW PLAYING", color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                Text(title, color = Color.White, fontSize = metrics.titleTextSize, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = if (metrics.showPillLabels) 13.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    endsAt?.let { "${now.format(DateTimeFormatter.ofPattern("HH:mm"))} · Ends at $it" }
                        ?: now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    Modifier.clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.48f))
                        .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), content = topRight)
            }
        }

        Row(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = metrics.horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(centerGap, Alignment.CenterHorizontally),
        ) {
            TransportButton("Previous episode", enabled = canPrevious, onClick = onPrevious, size = sideButton) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (canPrevious) 1f else 0.35f),
                    modifier = Modifier.size(sideGlyph),
                )
            }
            TransportButton("Rewind ten seconds", enabled = canControl, onClick = onRewind, size = sideButton) {
                Icon(
                    Icons.Rounded.FastRewind,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (canControl) 1f else 0.35f),
                    modifier = Modifier.size(sideGlyph),
                )
            }
            TransportButton(if (isPlaying) "Pause" else "Play", enabled = canControl, onClick = onPlayPause, size = playButton, primary = true) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (canControl) 1f else 0.35f),
                    modifier = Modifier.size(metrics.playGlyphSize),
                )
            }
            TransportButton("Forward ten seconds", enabled = canControl, onClick = onForward, size = sideButton) {
                Icon(
                    Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (canControl) 1f else 0.35f),
                    modifier = Modifier.size(sideGlyph),
                )
            }
            TransportButton("Next episode", enabled = canNext, onClick = onNext, size = sideButton) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (canNext) 1f else 0.35f),
                    modifier = Modifier.size(sideGlyph),
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = metrics.horizontalPadding, end = metrics.horizontalPadding, top = metrics.barTopPadding, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (metrics.compactHeight) 2.dp else 5.dp),
        ) {
            PlayerSeekBar(
                positionSeconds,
                durationSeconds,
                onSeek,
                trackHeight = metrics.seekTrackHeight,
                touchHeight = metrics.seekTouchHeight,
                enabled = canControl,
                onScrubChange = { scrubPosition = it },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TimePill(formatPlayerTime(shownPosition), metrics.timeTextSize)
                Spacer(Modifier.weight(1f))
                if (durationSeconds > 0.0) TimePill("-${formatPlayerTime(remaining)}", metrics.timeTextSize)
                else TimePill("LIVE", metrics.timeTextSize)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, content = { bottomLeftActions(showActionLabels) })
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = if (showActionLabels) Arrangement.spacedBy(4.dp) else Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = { bottomActions(showActionLabels) },
                )
            }
        }
    }
}

@Composable
private fun TransportButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    primary: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val circleSize = if (primary) size else size - 8.dp
    val fill = when {
        !enabled -> Color.Black.copy(alpha = 0.24f)
        primary -> Color.White.copy(alpha = if (hovered) 0.32f else 0.24f)
        hovered -> Color.White.copy(alpha = 0.15f)
        else -> Color.Black.copy(alpha = 0.42f)
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.size(size).semantics { contentDescription = label },
    ) {
        Box(
            Modifier.size(circleSize).clip(CircleShape).background(fill)
                .then(
                    if (primary) {
                        Modifier.border(
                            2.dp,
                            Color.White.copy(alpha = if (enabled) 0.92f else 0.35f),
                            CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
internal fun PlayerIconButton(onClick: () -> Unit, contentDescription: String, enabled: Boolean = true, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.18f else 0.07f), CircleShape)
            .semantics { this.contentDescription = contentDescription },
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

@Composable
internal fun PlayerActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    showLabel: Boolean,
    enabled: Boolean = true,
) {
    if (!showLabel) {
        PlayerIconButton(onClick, label, enabled) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(19.dp))
        }
    } else {
        Row(
            Modifier.heightIn(min = 42.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = if (enabled) 0.45f else 0.22f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = label }
                .padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(17.dp))
            Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.35f), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun TimePill(value: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(value, Modifier.clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.48f))
        .padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White.copy(alpha = 0.88f), fontSize = fontSize, fontWeight = FontWeight.Medium)
}

