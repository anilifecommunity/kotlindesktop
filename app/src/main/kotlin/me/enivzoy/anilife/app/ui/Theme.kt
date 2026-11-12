package me.enivzoy.anilife.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.AppSettings
import java.io.File

 




object AniLifeColors {
    private var appearance by mutableStateOf(AppSettings())
    fun update(settings: AppSettings) { if (appearance != settings) appearance = settings }
    private fun read(value: String, fallback: Long): Color = runCatching {
        val raw = value.removePrefix("#").toLong(16)
        Color(if (value.length == 7) (0xFF000000L or raw) else raw)
    }.getOrElse { Color(fallback) }
    val Background get() = read(appearance.backgroundColor, 0xFF0A0F0A)
    val Foreground = Color(0xFFF8F8F8)
    val Card get() = read(appearance.animeCardColor, 0xFF111A11)
    val CardForeground = Color(0xFFF8F8F8)
    val CardHover = Color(0xFF1A261A)
    val Muted = Color(0xFF1A261A)
    val MutedForeground = Color(0xFFA1A1A1)
    val Border = Color(0x1AFFFFFF)
    val Input = Color(0x26FFFFFF)
    val Primary get() = read(appearance.buttonColor, 0xFF87D66A)
    val PrimaryForeground = Color(0xFF0A0F0A)
    val Secondary = Color(0xFF1A261A)
    val SecondaryForeground = Color(0xFFF8F8F8)
    val Accent = Color(0xFF1A261A)
    val AccentForeground = Color(0xFFF8F8F8)
    val Destructive = Color(0xFFF87171)
    val Sidebar = Color(0xFF080F08)
    val SidebarAccent get() = read(appearance.sidebarIconBackgroundColor, 0xFF1A261A)
    val SidebarIconBackgroundEnabled get() = appearance.sidebarIconBackgroundEnabled
    val SidebarIconShape get() = appearance.sidebarIconShape
    val SidebarIconsJoined get() = appearance.sidebarIconsJoined
    val CheckboxColor get() = read(appearance.checkboxColor, 0xFF87D66A)
    val CheckboxTickColor get() = read(appearance.checkboxTickColor, 0xFF0A0F0A)
    val DownloadProgress get() = read(appearance.downloadProgressColor, 0xFF87D66A)
    val Ring get() = Primary
    val Score = Color(0xFF87D66A)
    val Success = Color(0xFF57F287)
    val Warning = Color(0xFFFBBF24)
    val Scrim = Color(0xCC000000)
    val Chip = Color(0xE60A0F0A)
}

private val DarkScheme = darkColorScheme(
    primary = AniLifeColors.Primary,
    onPrimary = AniLifeColors.PrimaryForeground,
    primaryContainer = AniLifeColors.Primary,
    onPrimaryContainer = AniLifeColors.PrimaryForeground,
    secondary = AniLifeColors.Secondary,
    onSecondary = AniLifeColors.SecondaryForeground,
    secondaryContainer = AniLifeColors.Accent,
    onSecondaryContainer = AniLifeColors.AccentForeground,
    background = AniLifeColors.Background,
    onBackground = AniLifeColors.Foreground,
    surface = AniLifeColors.Card,
    onSurface = AniLifeColors.CardForeground,
    surfaceVariant = AniLifeColors.Muted,
    onSurfaceVariant = AniLifeColors.MutedForeground,
    outline = AniLifeColors.Ring,
    error = AniLifeColors.Destructive,
    onError = Color.White,
    scrim = AniLifeColors.Scrim,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.9).sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.7).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun AniLifeTheme(settings: AppSettings, content: @Composable () -> Unit) {
    SideEffect { AniLifeColors.update(settings) }
    val font = androidx.compose.runtime.remember(settings.appFont, settings.appFontFile) {
        settings.appFontFile.takeIf(String::isNotBlank)?.let { path ->
            File(path).takeIf(File::isFile)?.let { file ->
                runCatching { FontFamily(Font(file)) }.getOrNull()
            }
        } ?: when (settings.appFont) {
            "Serif" -> FontFamily.Serif
            "Monospaced" -> FontFamily.Monospace
            "Cursive" -> FontFamily.Cursive
            else -> FontFamily.SansSerif
        }
    }
    val typography = androidx.compose.runtime.remember(font) { AppTypography.withFont(font) }
    val scheme = darkColorScheme(
        primary = AniLifeColors.Primary,
        onPrimary = AniLifeColors.PrimaryForeground,
        primaryContainer = AniLifeColors.Primary,
        onPrimaryContainer = AniLifeColors.PrimaryForeground,
        secondary = AniLifeColors.Secondary,
        onSecondary = AniLifeColors.SecondaryForeground,
        secondaryContainer = AniLifeColors.Accent,
        onSecondaryContainer = AniLifeColors.AccentForeground,
        background = AniLifeColors.Background,
        onBackground = AniLifeColors.Foreground,
        surface = AniLifeColors.Card,
        onSurface = AniLifeColors.CardForeground,
        surfaceVariant = AniLifeColors.Muted,
        onSurfaceVariant = AniLifeColors.MutedForeground,
        outline = AniLifeColors.Ring,
        error = AniLifeColors.Destructive,
        onError = Color.White,
        scrim = AniLifeColors.Scrim,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        content = content,
    )
}

private fun Typography.withFont(font: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = font), displayMedium = displayMedium.copy(fontFamily = font),
    displaySmall = displaySmall.copy(fontFamily = font), headlineLarge = headlineLarge.copy(fontFamily = font),
    headlineMedium = headlineMedium.copy(fontFamily = font), headlineSmall = headlineSmall.copy(fontFamily = font),
    titleLarge = titleLarge.copy(fontFamily = font), titleMedium = titleMedium.copy(fontFamily = font),
    titleSmall = titleSmall.copy(fontFamily = font), bodyLarge = bodyLarge.copy(fontFamily = font),
    bodyMedium = bodyMedium.copy(fontFamily = font), bodySmall = bodySmall.copy(fontFamily = font),
    labelLarge = labelLarge.copy(fontFamily = font), labelMedium = labelMedium.copy(fontFamily = font),
    labelSmall = labelSmall.copy(fontFamily = font),
)

