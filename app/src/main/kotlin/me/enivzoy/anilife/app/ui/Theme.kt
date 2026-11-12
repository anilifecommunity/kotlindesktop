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
    private var overrides by mutableStateOf<Map<String, Long>>(emptyMap())

    fun update(settings: AppSettings) {
        if (appearance == settings) return
        appearance = settings
        overrides = ThemeCss.parse(settings.themeCss).colors
    }

    /** Looks up a raw token, used by surfaces that are not part of the Material scheme. */
    fun color(name: String, fallback: Long): Color =
        overrides[name]?.let(::Color) ?: Color(fallback)

    private fun legacy(value: String, fallback: Long): Long = runCatching {
        val raw = value.removePrefix("#").toLong(16)
        if (value.length == 7) 0xFF000000L or raw else raw
    }.getOrElse { fallback }

    private fun themed(name: String, legacyValue: String, fallback: Long): Color =
        overrides[name]?.let(::Color) ?: Color(legacy(legacyValue, fallback))

    private fun token(name: String, fallback: Long): Color = color(name, fallback)

    val Background get() = themed("background", appearance.backgroundColor, 0xFF0A0F0A)
    val Foreground get() = token("foreground", 0xFFF8F8F8)
    val Card get() = themed("card", appearance.animeCardColor, 0xFF111A11)
    val CardForeground get() = token("card-foreground", 0xFFF8F8F8)
    val CardHover get() = token("card-hover", 0xFF1A261A)
    val Muted get() = token("muted", 0xFF1A261A)
    val MutedForeground get() = token("muted-foreground", 0xFFA1A1A1)
    val Border get() = token("border", 0x1AFFFFFF)
    val Input get() = token("input", 0x26FFFFFF)
    val Primary get() = themed("primary", appearance.buttonColor, 0xFF87D66A)
    val PrimaryForeground get() = token("primary-foreground", 0xFF0A0F0A)
    val Secondary get() = token("secondary", 0xFF1A261A)
    val SecondaryForeground get() = token("secondary-foreground", 0xFFF8F8F8)
    val Accent get() = token("accent", 0xFF1A261A)
    val AccentForeground get() = token("accent-foreground", 0xFFF8F8F8)
    val Destructive get() = token("destructive", 0xFFF87171)
    val Sidebar get() = token("sidebar", 0xFF080F08)
    val SidebarAccent get() = themed("sidebar-accent", appearance.sidebarIconBackgroundColor, 0xFF1A261A)
    val SidebarIconBackgroundEnabled get() = appearance.sidebarIconBackgroundEnabled
    val SidebarIconShape get() = appearance.sidebarIconShape
    val SidebarIconsJoined get() = appearance.sidebarIconsJoined
    val CheckboxColor get() = themed("checkbox", appearance.checkboxColor, 0xFF87D66A)
    val CheckboxTickColor get() = themed("checkbox-tick", appearance.checkboxTickColor, 0xFF0A0F0A)
    val DownloadProgress get() = themed("download-progress", appearance.downloadProgressColor, 0xFF87D66A)
    val Ring get() = Primary
    val Score get() = token("score", 0xFF87D66A)
    val Success get() = token("success", 0xFF57F287)
    val Warning get() = token("warning", 0xFFFBBF24)
    val Scrim get() = token("scrim", 0xCC000000)
    val Chip get() = token("chip", 0xE60A0F0A)
    val ChipOverlay get() = token("chip-overlay", 0xCC262626)
    val Overlay get() = token("overlay", 0xCC171717)
    val Toast get() = token("toast", 0xD9171717)
    val ToastDanger get() = token("toast-danger", 0xE6B91C1C)
    val Shadow get() = token("shadow", 0x4D000000)
    val SeekBarTop get() = token("seekbar-top", 0xFF333333)
    val SeekBarBottom get() = token("seekbar-bottom", 0xFF111111)
    val DownloadSurface get() = token("download-surface", 0xFF1E2024)
    val DownloadSurfaceRaised get() = token("download-surface-raised", 0xFF25272C)
    val DownloadOutline get() = token("download-outline", 0xFF3A3D43)
    val DownloadText get() = token("download-text", 0xFFE8E9EC)
    val DownloadMuted get() = token("download-muted", 0xFFA2A5AC)
    val DownloadAccent get() = token("download-accent", 0xFFFF8FC9)
    val DownloadAccentForeground get() = token("download-accent-foreground", 0xFF291522)
    val SettingsBackground get() = token("settings-bg", 0xFF000000)
    val SettingsPanel get() = token("settings-panel", 0xFF090909)
    val SettingsBorder get() = token("settings-border", 0xFF1A1A1A)
    val SettingsDivider get() = token("settings-divider", 0xFF212222)
    val SettingsSelected get() = token("settings-selected", 0xFF222222)
    val SettingsSelectedForeground get() = token("settings-selected-foreground", 0xFFFAFAFA)
    val SettingsControl get() = token("settings-control", 0xFF0B0B0B)
    val SettingsControlBorder get() = token("settings-control-border", 0xFF2A2A2A)
    val SettingsControlActive get() = token("settings-control-active", 0xFFE5E5E5)
    val SettingsControlActiveForeground get() = token("settings-control-active-foreground", 0xFF1B1B1B)
    val SettingsSwitchTrack get() = token("settings-switch-track", 0xFF272727)
    val SettingsSwitchKnob get() = token("settings-switch-knob", 0xFFE5E5E5)
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
    // No line height on the body style: a `Text` that sets only a font size then keeps the
    // font's own metrics, so a small label sits centred in its pill instead of dropping to the
    // bottom of a tall line box.
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
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

