package me.enivzoy.anilife.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.formatLabel
import me.enivzoy.anilife.core.model.seasonLabel
import kotlinx.coroutines.delay

 



@Composable
fun Hero(
    items: List<AnimeCard>,
    loading: Boolean,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    synopsis: String? = null,
) {
    if (loading) {
        Shimmer(
            modifier = modifier
                .fillMaxWidth()
                .height(416.dp),
            shape = RoundedCornerShape(0.dp),
        )
        return
    }
    if (items.isEmpty()) return

    var index by remember(items) { mutableIntStateOf(0) }
    LaunchedEffect(items.size, index) {
        if (items.size > 1) {
            delay(8_000)
            index = (index + 1) % items.size
        }
    }

    val current = items[index % items.size]
    val tint = remember(current.color) { parseAnimeColor(current.color) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clipToBounds()
            .background(tint),
    ) {
        Crossfade(targetState = current, animationSpec = tween(900), label = "hero-artwork") { anime ->
            RemoteImage(
                url = anime.banner ?: anime.cover,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                placeholderColor = parseAnimeColor(anime.color),
            )
        }

         
         
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to AniLifeColors.Background,
                        0.34f to AniLifeColors.Background.copy(alpha = 0.9f),
                        0.62f to AniLifeColors.Background.copy(alpha = 0.35f),
                        0.82f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to AniLifeColors.Background,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 32.dp, vertical = 14.dp)
                .widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(
                    formatLabel(current.format),
                    seasonLabel(current.season, current.year),
                    current.score?.let { "${it.toInt()}%" },
                ).forEach { label ->
                    Chip(text = label, container = AniLifeColors.ChipOverlay)
                }
            }

            Text(
                text = current.title,
                color = AniLifeColors.Foreground,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (current.genres.isNotEmpty()) {
                Text(
                    text = current.genres.joinToString(" · "),
                    color = AniLifeColors.MutedForeground,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!synopsis.isNullOrBlank()) {
                Text(
                    text = synopsis,
                    color = AniLifeColors.MutedForeground.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onOpen(current.id) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(44.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AniLifeColors.Primary,
                        contentColor = AniLifeColors.PrimaryForeground,
                    ),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Watch now", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { onOpen(current.id) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(44.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AniLifeColors.Secondary,
                        contentColor = AniLifeColors.Foreground,
                    ),
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Details", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { i, item ->
                    val width by animateDpAsState(if (i == index) 32.dp else 16.dp, tween(220), label = "hero-dot")
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (i == index) AniLifeColors.Primary else AniLifeColors.MutedForeground.copy(alpha = 0.4f))
                            .clickable { index = i }
                            .pointerHoverIcon(PointerIcon.Hand),
                    )
                }
            }
        }
    }
}

