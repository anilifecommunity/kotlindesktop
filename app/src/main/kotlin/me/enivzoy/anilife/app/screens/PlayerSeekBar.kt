package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong

@Composable
internal fun PlayerSeekBar(
    positionSeconds: Double,
    durationSeconds: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 4.dp,
    touchHeight: Dp = 30.dp,
    enabled: Boolean = true,
    onScrubChange: (Double?) -> Unit = {},
) {
    var dragging by remember { mutableStateOf<Double?>(null) }
    val duration = durationSeconds.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val fraction = (dragging ?: positionSeconds.takeIf(Double::isFinite).orZero())
        .let { if (duration > 0.0) (it / duration).coerceIn(0.0, 1.0).toFloat() else 0f }
    val trackColor = Color.White.copy(alpha = if (enabled && duration > 0.0) 0.30f else 0.14f)
    val playedColor = Color.White.copy(alpha = if (enabled && duration > 0.0) 0.92f else 0.2f)

    BoxWithConstraints(modifier.fillMaxWidth().height(touchHeight + 18.dp)) {
        val barModifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(touchHeight)
            .pointerInput(duration, enabled) {
                detectTapGestures { point ->
                    if (enabled && duration > 0.0) onSeek((point.x / size.width * duration).coerceIn(0.0, duration))
                }
            }
            .pointerInput(duration, enabled) {
                detectDragGestures(
                    onDragStart = { point ->
                        if (enabled && duration > 0.0) {
                            dragging = (point.x / size.width * duration).coerceIn(0.0, duration)
                            onScrubChange(dragging)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (enabled && duration > 0.0) {
                            dragging = (change.position.x / size.width * duration).coerceIn(0.0, duration)
                            onScrubChange(dragging)
                        }
                    },
                    onDragEnd = { dragging?.let(onSeek); dragging = null; onScrubChange(null) },
                    onDragCancel = { dragging = null; onScrubChange(null) },
                )
            }
        Box(barModifier) {
            Canvas(Modifier.fillMaxWidth().height(touchHeight)) {
                val inset = 6.dp.toPx()
                val left = inset
                val right = (size.width - inset).coerceAtLeast(left)
                val y = size.height / 2f
                val stroke = if (dragging != null) (trackHeight.toPx() * 1.7f).coerceAtLeast(8.dp.toPx()) else trackHeight.toPx().coerceAtLeast(4.dp.toPx())
                drawLine(trackColor, Offset(left, y), Offset(right, y), stroke, StrokeCap.Round)
                val x = left + (right - left) * fraction
                if (duration > 0.0) drawLine(playedColor, Offset(left, y), Offset(x, y), stroke, StrokeCap.Round)
                drawCircle(playedColor, if (dragging != null) 7.dp.toPx() else 4.dp.toPx(), Offset(x, y))
            }
        }
        dragging?.let { preview ->
            val thumbX = (maxWidth * fraction - 30.dp).coerceIn(0.dp, (maxWidth - 64.dp).coerceAtLeast(0.dp))
            Text(
                formatPlayerTime(preview),
                Modifier.align(Alignment.TopStart).offset(x = thumbX).clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF333333), Color(0xFF111111))))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

private fun Double?.orZero(): Double = this ?: 0.0

internal fun formatPlayerTime(seconds: Double): String {
    val total = seconds.takeIf(Double::isFinite)?.coerceAtLeast(0.0)?.roundToLong() ?: 0L
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val remainder = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%02d:%02d".format(minutes, remainder)
}

