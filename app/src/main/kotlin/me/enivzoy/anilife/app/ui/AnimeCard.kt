package me.enivzoy.anilife.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.core.model.AnimeCard

 



@Composable
fun AnimePosterCard(
    anime: AnimeCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 160.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.05f else 1f, tween(280), label = "poster-scale")
    val ring by animateColorAsState(
        if (hovered) AniLifeColors.Ring.copy(alpha = 0.75f) else Color.Transparent,
        tween(180),
        label = "poster-ring",
    )
    val shape = RoundedCornerShape(8.dp)
    val tint = remember(anime.color) { parseAnimeColor(anime.color) }

    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(tint)
                .then(if (hovered) Modifier.border(2.dp, ring, shape) else Modifier),
        ) {
            RemoteImage(
                url = anime.cover,
                contentDescription = anime.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                placeholderColor = tint,
            )
            val score = anime.score
            if (score != null) {
                ScoreTag(score, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            if (anime.isAdult) {
                AdultTag(Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            val nextAiring = anime.nextAiring
            if (nextAiring != null) {
                NextAiringTag(nextAiring, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = anime.title,
                color = AniLifeColors.Foreground,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = cardMeta(anime),
                color = AniLifeColors.MutedForeground,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun PosterSkeleton(modifier: Modifier = Modifier, width: Dp? = 160.dp) {
    Column(
        modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Shimmer(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(8.dp),
        )
        Shimmer(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Shimmer(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(11.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(Modifier.height(2.dp))
    }
}

