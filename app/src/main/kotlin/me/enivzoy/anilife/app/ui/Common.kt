package me.enivzoy.anilife.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.NextAiring
import me.enivzoy.anilife.core.model.countdown
import me.enivzoy.anilife.core.model.formatLabel
import kotlinx.coroutines.CancellationException
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM

 
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

 
suspend fun <T> loadState(block: suspend () -> T): LoadState<T> = try {
    LoadState.Ready(block())
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    LoadState.Failed(t.message?.takeIf { it.isNotBlank() } ?: "Something went wrong.")
}

 
@Composable
fun Shimmer(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1350, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress",
    )
    val brush = Brush.linearGradient(
        colors = listOf(AniLifeColors.Card, AniLifeColors.CardHover, AniLifeColors.Card),
        start = Offset(progress * 900f - 450f, 0f),
        end = Offset(progress * 900f, 260f),
    )
    Box(modifier.clip(shape).background(brush))
}

 
@Composable
fun PageScrollbar(adapter: ScrollbarAdapter, modifier: Modifier = Modifier) {
    val style = LocalScrollbarStyle.current.copy(
        minimalHeight = 28.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        unhoverColor = AniLifeColors.Foreground.copy(alpha = 0.16f),
        hoverColor = AniLifeColors.Foreground.copy(alpha = 0.4f),
    )
    CompositionLocalProvider(LocalScrollbarStyle provides style) {
        VerticalScrollbar(
            adapter = adapter,
            modifier = modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp, vertical = 4.dp),
        )
    }
}

 
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = AniLifeColors.Chip,
    content: Color = AniLifeColors.Foreground,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .defaultMinSize(minHeight = 24.dp)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leading?.invoke()
        Text(
            text = text,
            color = content,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

 
@Composable
fun ScoreTag(score: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(AniLifeColors.Toast)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = AniLifeColors.Score,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = "${score.toInt()}%",
            color = AniLifeColors.Foreground,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun AdultTag(modifier: Modifier = Modifier) {
    Chip(
        text = "18+",
        modifier = modifier,
        container = AniLifeColors.ToastDanger,
        content = Color.White,
    )
}

@Composable
fun NextAiringTag(nextAiring: NextAiring, modifier: Modifier = Modifier) {
    Chip(
        text = "Ep ${nextAiring.episode} ${countdown(nextAiring.airingAt)}",
        modifier = modifier,
        container = AniLifeColors.Chip,
        content = AniLifeColors.Score,
    )
}

 
fun cardMeta(anime: AnimeCard): String = listOfNotNull(
    formatLabel(anime.format),
    anime.year?.toString(),
    anime.episodes?.let { "$it eps" },
).joinToString(" · ")

 
fun parseAnimeColor(value: String?, fallback: Color = AniLifeColors.CardHover): Color {
    val hex = value?.trim()?.removePrefix("#") ?: return fallback
    return runCatching {
        when (hex.length) {
            6 -> Color(("FF$hex").toLong(16))
            8 -> Color(hex.toLong(16))
            else -> fallback
        }
    }.getOrDefault(fallback)
}

@Composable
fun ErrorPanel(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = 440.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AniLifeColors.Card)
            .border(1.dp, AniLifeColors.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AniLifeColors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = AniLifeColors.MutedForeground,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Couldn't reach AniList", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AniLifeColors.Foreground)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = AniLifeColors.MutedForeground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AniLifeColors.Primary,
                contentColor = AniLifeColors.PrimaryForeground,
            ),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try again", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun EmptyPanel(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .widthIn(max = 440.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AniLifeColors.Card)
            .border(1.dp, AniLifeColors.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(AniLifeColors.CardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AniLifeColors.MutedForeground, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AniLifeColors.Foreground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 13.sp, lineHeight = 19.sp, color = AniLifeColors.MutedForeground, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

 
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val pixels = with(density) { size.roundToPx() }.coerceAtLeast(24)
    val bitmap = remember(pixels) { BrandMarkImage.load(pixels) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "AniLife",
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(9.dp))
                .background(AniLifeColors.Foreground),
            contentAlignment = Alignment.Center,
        ) {
            Text("Z", color = AniLifeColors.Background, fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

private object BrandMarkImage {
    private val cache = HashMap<Int, ImageBitmap>()

    fun load(pixels: Int): ImageBitmap? {
        synchronized(cache) { cache[pixels]?.let { return it } }
        val bitmap = runCatching {
            val stream = BrandMarkImage::class.java.getResourceAsStream("/anilife-icon.png")
                ?: BrandMarkImage::class.java.getResourceAsStream("/anilife-128.png")
            val bytes = stream?.use { it.readBytes() } ?: return null
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull() ?: return null
        synchronized(cache) { cache[pixels] = bitmap }
        return bitmap
    }
}

