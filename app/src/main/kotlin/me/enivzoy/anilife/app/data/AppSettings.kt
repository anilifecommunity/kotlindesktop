package me.enivzoy.anilife.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Path
import java.util.Properties

const val DEFAULT_DISCORD_APPLICATION_ID = "1567301811604553728"

data class AppSettings(
    val audio: String = "sub",
    val subtitleLanguage: String = "en",
    val subtitlesByDefault: Boolean = true,
    val autoplayNextEpisode: Boolean = true,
    val resumePlayback: Boolean = true,
    val hardwareDecoding: Boolean = true,
    val playbackBufferSize: String = "auto",
    val contentScale: String = "fit",
    val showSkipButton: Boolean = true,
    val skipButtonSeconds: Int = 85,
    val autoSkipIntro: Boolean = false,
    val autoSkipOutro: Boolean = false,
    val pauseDetails: Boolean = false,
    val volume: Int = 100,
    val subtitleScale: Float = 1f,
    val captionFont: String = "SansSerif",
    val captionFontFile: String = "",
    val buttonColor: String = "#87D66A",
    val backgroundColor: String = "#0A0F0A",
    val animeCardColor: String = "#111A11",
    val downloadProgressColor: String = "#87D66A",
    val checkboxColor: String = "#87D66A",
    val checkboxTickColor: String = "#0A0F0A",
    val sidebarIconBackgroundColor: String = "#1A261A",
    val themeCss: String = "",
    val sidebarIconBackgroundEnabled: Boolean = false,
    val sidebarIconShape: String = "rounded",
    val sidebarIconsJoined: Boolean = false,
    val appFont: String = "SansSerif",
    val appFontFile: String = "",
    val downloadDirectory: String? = null,
    val playIntro: Boolean = true,
    val titleLanguage: String = "english",
    val discordPresenceEnabled: Boolean = true,
    val discordApplicationId: String = DEFAULT_DISCORD_APPLICATION_ID,
)

class AppSettingsStore(private val file: File = settingsFile()) {
    private val lock = Any()
    private val mutableSettings = MutableStateFlow(readSettings())
    val settings = mutableSettings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) = synchronized(lock) {
        val updated = transform(mutableSettings.value).validated()
        writeSettings(updated)
        mutableSettings.value = updated
    }

    private fun readSettings(): AppSettings {
        if (!file.isFile) return AppSettings()
        val properties = Properties()
        file.inputStream().use(properties::load)
        return AppSettings(
            audio = properties.getProperty("audio", "sub"),
            subtitleLanguage = properties.getProperty("subtitleLanguage", "en"),
            subtitlesByDefault = properties.getProperty("subtitlesByDefault", "true").toBooleanStrictOrNull() ?: true,
            autoplayNextEpisode = properties.getProperty("autoplayNextEpisode", "true").toBooleanStrictOrNull() ?: true,
            resumePlayback = properties.getProperty("resumePlayback", "true").toBooleanStrictOrNull() ?: true,
            hardwareDecoding = properties.getProperty("hardwareDecoding", "true").toBooleanStrictOrNull() ?: true,
            playbackBufferSize = properties.getProperty("playbackBufferSize", "auto"),
            contentScale = properties.getProperty("contentScale", "fit"),
            showSkipButton = properties.getProperty("showSkipButton", "true").toBooleanStrictOrNull() ?: true,
            skipButtonSeconds = properties.getProperty("skipButtonSeconds", "85").toIntOrNull() ?: 85,
            autoSkipIntro = properties.getProperty("autoSkipIntro", "false").toBooleanStrictOrNull() ?: false,
            autoSkipOutro = properties.getProperty("autoSkipOutro", "false").toBooleanStrictOrNull() ?: false,
            pauseDetails = properties.getProperty("pauseDetails", "false").toBooleanStrictOrNull() ?: false,
            volume = properties.getProperty("volume", "100").toIntOrNull() ?: 100,
            subtitleScale = properties.getProperty("subtitleScale", "1.0").toFloatOrNull() ?: 1f,
            captionFont = properties.getProperty("captionFont", "SansSerif"),
            captionFontFile = properties.getProperty("captionFontFile", ""),
            buttonColor = properties.getProperty("buttonColor", "#87D66A"),
            backgroundColor = properties.getProperty("backgroundColor", "#0A0F0A"),
            animeCardColor = properties.getProperty("animeCardColor", "#111A11"),
            downloadProgressColor = properties.getProperty("downloadProgressColor", "#87D66A"),
            checkboxColor = properties.getProperty("checkboxColor", "#87D66A"),
            checkboxTickColor = properties.getProperty("checkboxTickColor", "#0A0F0A"),
            sidebarIconBackgroundColor = properties.getProperty("sidebarIconBackgroundColor", "#1A261A"),
            themeCss = properties.getProperty("themeCss", ""),
            sidebarIconBackgroundEnabled = properties.getProperty("sidebarIconBackgroundEnabled", "false").toBooleanStrictOrNull() ?: false,
            sidebarIconShape = properties.getProperty("sidebarIconShape", "rounded"),
            sidebarIconsJoined = properties.getProperty("sidebarIconsJoined", "false").toBooleanStrictOrNull() ?: false,
            appFont = properties.getProperty("appFont", "SansSerif"),
            appFontFile = properties.getProperty("appFontFile", ""),
            downloadDirectory = properties.getProperty("downloadDirectory")?.takeIf(String::isNotBlank),
            playIntro = properties.getProperty("playIntro", "true").toBooleanStrictOrNull() ?: true,
            titleLanguage = properties.getProperty("titleLanguage") ?: if (properties.getProperty("preferEnglishTitles", "true").toBooleanStrictOrNull() ?: true) "english" else "romaji",
            discordPresenceEnabled = properties.getProperty("discordPresenceEnabled", "true").toBooleanStrictOrNull() ?: true,
            discordApplicationId = properties.getProperty("discordApplicationId", DEFAULT_DISCORD_APPLICATION_ID),
        ).validated()
    }

    private fun writeSettings(settings: AppSettings) {
        file.parentFile?.mkdirs()
        val properties = Properties().apply {
            setProperty("audio", settings.audio)
            setProperty("subtitleLanguage", settings.subtitleLanguage)
            setProperty("subtitlesByDefault", settings.subtitlesByDefault.toString())
            setProperty("autoplayNextEpisode", settings.autoplayNextEpisode.toString())
            setProperty("resumePlayback", settings.resumePlayback.toString())
            setProperty("hardwareDecoding", settings.hardwareDecoding.toString())
            setProperty("playbackBufferSize", settings.playbackBufferSize)
            setProperty("contentScale", settings.contentScale)
            setProperty("showSkipButton", settings.showSkipButton.toString())
            setProperty("skipButtonSeconds", settings.skipButtonSeconds.toString())
            setProperty("autoSkipIntro", settings.autoSkipIntro.toString())
            setProperty("autoSkipOutro", settings.autoSkipOutro.toString())
            setProperty("pauseDetails", settings.pauseDetails.toString())
            setProperty("volume", settings.volume.toString())
            setProperty("subtitleScale", settings.subtitleScale.toString())
            setProperty("captionFont", settings.captionFont)
            setProperty("captionFontFile", settings.captionFontFile)
            setProperty("buttonColor", settings.buttonColor)
            setProperty("backgroundColor", settings.backgroundColor)
            setProperty("animeCardColor", settings.animeCardColor)
            setProperty("downloadProgressColor", settings.downloadProgressColor)
            setProperty("checkboxColor", settings.checkboxColor)
            setProperty("checkboxTickColor", settings.checkboxTickColor)
            setProperty("sidebarIconBackgroundColor", settings.sidebarIconBackgroundColor)
            setProperty("themeCss", settings.themeCss)
            setProperty("sidebarIconBackgroundEnabled", settings.sidebarIconBackgroundEnabled.toString())
            setProperty("sidebarIconShape", settings.sidebarIconShape)
            setProperty("sidebarIconsJoined", settings.sidebarIconsJoined.toString())
            setProperty("appFont", settings.appFont)
            setProperty("appFontFile", settings.appFontFile)
            settings.downloadDirectory?.let { setProperty("downloadDirectory", it) }
            setProperty("playIntro", settings.playIntro.toString())
            setProperty("titleLanguage", settings.titleLanguage)
            setProperty("discordPresenceEnabled", settings.discordPresenceEnabled.toString())
            setProperty("discordApplicationId", settings.discordApplicationId)
        }
        file.outputStream().use { properties.store(it, null) }
    }

    private fun AppSettings.validated(): AppSettings = copy(
        audio = if (audio == "dub") "dub" else "sub",
        subtitleLanguage = subtitleLanguage.takeIf { it in supportedSubtitleLanguages } ?: "en",
        playbackBufferSize = playbackBufferSize.takeIf { it in bufferSizes } ?: "auto",
        contentScale = contentScale.takeIf { it in contentScales } ?: "fit",
        skipButtonSeconds = skipButtonSeconds.coerceIn(10, 300),
        volume = volume.coerceIn(0, 100),
        subtitleScale = subtitleScale.takeIf(Float::isFinite)?.coerceIn(0.5f, 2f) ?: 1f,
        captionFont = captionFont.takeIf { it in fontFamilies } ?: "SansSerif",
        buttonColor = validColor(buttonColor, "#87D66A"),
        backgroundColor = validColor(backgroundColor, "#0A0F0A"),
        animeCardColor = validColor(animeCardColor, "#111A11"),
        downloadProgressColor = validColor(downloadProgressColor, "#87D66A"),
        checkboxColor = validColor(checkboxColor, "#87D66A"),
        checkboxTickColor = validColor(checkboxTickColor, "#0A0F0A"),
        sidebarIconBackgroundColor = validColor(sidebarIconBackgroundColor, "#1A261A"),
        themeCss = themeCss.take(20_000),
        sidebarIconShape = sidebarIconShape.takeIf { it in sidebarShapes } ?: "rounded",
        appFont = appFont.takeIf { it in fontFamilies } ?: "SansSerif",
        captionFontFile = captionFontFile.take(2048),
        appFontFile = appFontFile.take(2048),
        downloadDirectory = downloadDirectory?.let { runCatching { Path.of(it).toAbsolutePath().normalize().toString() }.getOrNull() },
        discordApplicationId = discordApplicationId.filter(Char::isDigit).take(20),
    )

    companion object {
        val bufferSizes = listOf("auto", "small", "medium", "large")
        val contentScales = listOf("fit", "crop", "fill")
        val supportedSubtitleLanguages = listOf("en", "ja", "es", "pt", "fr", "de", "it", "ru", "ar", "zh", "none")
        val fontFamilies = listOf("SansSerif", "Serif", "Monospaced", "Cursive")
        val sidebarShapes = listOf("rounded", "circle", "rectangular")
        private fun validColor(value: String, fallback: String): String =
            value.trim().uppercase().takeIf { it.matches(Regex("#[0-9A-F]{6}([0-9A-F]{2})?")) } ?: fallback

        private fun settingsFile(): File {
            val base = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let(::File)
                ?: File(System.getProperty("user.home"), ".local/share")
            return File(base, "AniLife/settings.properties")
        }
    }
}

