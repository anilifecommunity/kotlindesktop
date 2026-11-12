package me.enivzoy.anilife.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.ContinueEntry
import me.enivzoy.anilife.app.data.ContinueStoreInstance

@Composable
fun ContinueCard(
    entry: ContinueEntry,
    onResume: (ContinueEntry) -> Unit,
    onDetails: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anime = entry.anime
    val fraction = if (entry.progress.duration > 0.0)
        (entry.progress.position / entry.progress.duration).coerceIn(0.0, 1.0) else 0.0
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tint = remember(anime.color) { parseAnimeColor(anime.color) }

    Column(
        modifier = modifier.width(256.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(tint),
        ) {
            RemoteImage(
                url = anime.banner ?: anime.cover,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
            )
            // Play overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f))
                    .clickable(interactionSource = interaction, indication = null) { onResume(entry) }
                    .pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            // Corner actions
            CornerIconButton(
                icon = Icons.Rounded.Info,
                description = "Details",
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                onClick = { onDetails(anime.id) },
            )
            CornerIconButton(
                icon = Icons.Rounded.Close,
                description = "Remove",
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                onClick = { onRemove(anime.id) },
            )
            // Progress bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.toFloat().coerceAtLeast(0.03f))
                        .height(3.dp)
                        .background(AniLifeColors.Primary),
                )
            }
        }
        Text(
            text = anime.title,
            color = AniLifeColors.Foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Episode ${formatEpisodeNumber(entry.progress.episode)}",
            color = AniLifeColors.MutedForeground,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun CornerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

private fun formatEpisodeNumber(value: Double): String {
    val whole = value.toInt()
    return if (value == whole.toDouble()) whole.toString() else value.toString()
}