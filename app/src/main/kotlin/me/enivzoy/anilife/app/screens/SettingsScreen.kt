package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.AppSettings
import me.enivzoy.anilife.app.data.AppSettingsStore
import me.enivzoy.anilife.app.data.DirectMediaDownloader
import me.enivzoy.anilife.app.data.DiscordPresenceState
import me.enivzoy.anilife.app.data.DiscordPresenceStatus
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.Chip
import me.enivzoy.anilife.app.ui.ThemeEditor
import java.io.File
import javax.swing.JFileChooser

/**
 * Settings, laid out as a sidebar of pages and a scrolling panel of rows. Every control maps to a
 * real [AppSettings] field; rows for engines that are not ported yet stay out of the list.
 */
@Composable
fun SettingsScreen(
    mpvLibrary: String?,
    settings: AppSettings,
    discordState: DiscordPresenceState,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDiscordConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(SettingsPage.Player) }
    var query by remember { mutableStateOf("") }

    val results = remember(query) {
        val needle = query.trim()
        if (needle.isEmpty()) emptyList()
        else settingSearchEntries.filter { entry ->
            "${entry.label} ${entry.page.title} ${entry.keywords}".contains(needle, ignoreCase = true)
        }
    }

    Row(modifier.fillMaxSize().background(AniLifeColors.SettingsBackground)) {
        SettingsSidebar(
            page = page,
            query = query,
            onQueryChange = { query = it },
            onSelect = {
                page = it
                query = ""
            },
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(AniLifeColors.SettingsDivider))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SettingsHeader(
                title = if (query.isBlank()) page.title else "Search results",
                subtitle = if (query.isBlank()) {
                    page.subtitle
                } else {
                    "${results.size} setting${if (results.size == 1) "" else "s"} matching “${query.trim()}”"
                },
            )
            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(AniLifeColors.SettingsPanel)
                    .border(1.dp, AniLifeColors.SettingsBorder, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp, vertical = 16.dp),
            ) {
                if (query.isNotBlank()) {
                    SearchResults(results) {
                        page = it
                        query = ""
                    }
                } else {
                    when (page) {
                        SettingsPage.Player -> PlayerSettings(settings, onSettingsChange)
                        SettingsPage.Subtitles -> SubtitleSettings(settings, onSettingsChange)
                        SettingsPage.Downloads -> DownloadSettings(settings, onSettingsChange)
                        SettingsPage.Servers -> ServerSettings()
                        SettingsPage.Accounts -> AccountSettings(
                            settings = settings,
                            discordState = discordState,
                            onSettingsChange = onSettingsChange,
                            onDiscordConnect = onDiscordConnect,
                        )
                        SettingsPage.Notifications -> NotificationSettings()
                        SettingsPage.Content -> ContentSettings(settings, onSettingsChange)
                        SettingsPage.Appearance -> AppearanceSettings(settings, onSettingsChange)
                        SettingsPage.Data -> DataSettings(settings, onSettingsChange)
                        SettingsPage.App -> AppSettingsPage(settings, onSettingsChange)
                        SettingsPage.About -> AboutSettings(mpvLibrary)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private enum class SettingsPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val keywords: String,
) {
    Player("Player", "Playback, skipping, and how the player looks", Icons.Rounded.PlayCircle, "audio dubbed sub resume autoplay skip volume picture"),
    Subtitles("Subtitles", "Captions, language, and text size", Icons.Rounded.Subtitles, "captions subtitles language text size"),
    Downloads("Downloads", "Where episodes are saved", Icons.Rounded.Download, "folder directory storage path"),
    Servers("Servers", "Your Jellyfin and Emby connections", Icons.Rounded.Dns, "jellyfin emby providers streaming"),
    Accounts("Accounts & sync", "Discord presence and list sync", Icons.Rounded.Sync, "discord rich presence myanimelist mal account"),
    Notifications("Notifications", "Release and episode alerts", Icons.Rounded.Notifications, "alerts reminders episodes releases"),
    Content("Content", "Titles and what the app opens with", Icons.Rounded.VideoLibrary, "title language romaji english native"),
    Appearance("Appearance", "Theme and type", Icons.Rounded.Palette, "css theme colors palette font typeface editor"),
    Data("Data", "Buffers, cache, and storage", Icons.Rounded.Storage, "buffer memory cache storage"),
    App("App", "Decoding, performance, and startup", Icons.Rounded.Apps, "hardware decoding gpu startup intro splash"),
    About("About", "Version and credits", Icons.Rounded.Info, "app version metadata player providers credits"),
}

private data class SettingSearchEntry(
    val label: String,
    val page: SettingsPage,
    val keywords: String,
)

private val settingSearchEntries = listOf(
    SettingSearchEntry("Playback version", SettingsPage.Player, "audio sub dubbed language track"),
    SettingSearchEntry("Resume where you left off", SettingsPage.Player, "continue progress resume"),
    SettingSearchEntry("Autoplay next episode", SettingsPage.Player, "next episode countdown autoplay"),
    SettingSearchEntry("Skip button", SettingsPage.Player, "opening ending intro outro forward"),
    SettingSearchEntry("Skip button length", SettingsPage.Player, "seconds duration skip"),
    SettingSearchEntry("Skip intro and recap automatically", SettingsPage.Player, "intro outro recap opening skip auto"),
    SettingSearchEntry("Skip outro automatically", SettingsPage.Player, "intro outro recap ending skip auto"),
    SettingSearchEntry("Screen size", SettingsPage.Player, "fit crop fill scaling display"),
    SettingSearchEntry("Player volume", SettingsPage.Player, "audio sound volume"),
    SettingSearchEntry("Show captions by default", SettingsPage.Subtitles, "captions subtitles text default"),
    SettingSearchEntry("Preferred caption language", SettingsPage.Subtitles, "captions subtitles language"),
    SettingSearchEntry("Caption size", SettingsPage.Subtitles, "captions subtitles text scale size"),
    SettingSearchEntry("Download folder", SettingsPage.Downloads, "directory location path storage"),
    SettingSearchEntry("Media servers", SettingsPage.Servers, "jellyfin emby providers servers streaming"),
    SettingSearchEntry("Discord Rich Presence", SettingsPage.Accounts, "discord activity status watching idle connect"),
    SettingSearchEntry("Discord Application ID", SettingsPage.Accounts, "discord developer application id"),
    SettingSearchEntry("Title display language", SettingsPage.Content, "anime name title romaji english native"),
    SettingSearchEntry("App font", SettingsPage.Appearance, "font typeface text sans serif mono"),
    SettingSearchEntry("Theme colors", SettingsPage.Appearance, "css theme colors palette editor custom look style"),
    SettingSearchEntry("Playback buffer", SettingsPage.Data, "stream buffering memory"),
    SettingSearchEntry("Play startup intro", SettingsPage.App, "animation splash opening startup"),
    SettingSearchEntry("Hardware decoding", SettingsPage.App, "gpu acceleration decoder cpu"),
    SettingSearchEntry("About AniLife", SettingsPage.About, "app version metadata player credits"),
)

@Composable
private fun SettingsSidebar(
    page: SettingsPage,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (SettingsPage) -> Unit,
) {
    Column(
        Modifier.width(300.dp)
            .fillMaxHeight()
            .background(AniLifeColors.SettingsBackground)
            .padding(top = 18.dp),
    ) {
        Text(
            "Settings",
            color = AniLifeColors.Foreground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        SidebarSearch(query = query, onQueryChange = onQueryChange)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SettingsPage.entries.forEach { entry ->
                val selected = entry == page && query.isBlank()
                Row(
                    Modifier.fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) AniLifeColors.SettingsSelected else Color.Transparent)
                        .clickable { onSelect(entry) }
                        .padding(start = 17.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        tint = if (selected) AniLifeColors.SettingsSelectedForeground else AniLifeColors.MutedForeground,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(13.dp))
                    Text(
                        entry.title,
                        color = if (selected) AniLifeColors.SettingsSelectedForeground else AniLifeColors.MutedForeground,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarSearch(query: String, onQueryChange: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AniLifeColors.SettingsControl)
            .border(1.dp, AniLifeColors.SettingsControlBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = "Search settings",
            tint = AniLifeColors.MutedForeground,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = AniLifeColors.Foreground, fontSize = 13.sp),
            cursorBrush = SolidColor(AniLifeColors.Foreground),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isBlank()) {
                        Text("Search settings", color = AniLifeColors.MutedForeground, fontSize = 13.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun SettingsHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(start = 26.dp, top = 20.dp, bottom = 14.dp)) {
        Text(title, color = AniLifeColors.Foreground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = AniLifeColors.MutedForeground, fontSize = 13.sp)
    }
}

@Composable
private fun SearchResults(results: List<SettingSearchEntry>, onOpen: (SettingsPage) -> Unit) {
    if (results.isEmpty()) {
        Text(
            "No matching settings. Try captions, skipping, volume, Discord, or theme.",
            color = AniLifeColors.MutedForeground,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        return
    }
    results.forEach { result ->
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpen(result.page) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(result.label, color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(result.page.title, color = AniLifeColors.MutedForeground, fontSize = 12.sp)
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = AniLifeColors.MutedForeground,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PlayerSettings(settings: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsRow(
        "Playback",
        "Which version to start with. You can switch per episode.",
    ) {
        SettingsSegmented(
            choices = listOf("sub" to "Subtitled", "dub" to "Dubbed"),
            selected = settings.audio,
            onSelect = { value -> onSettingsChange { it.copy(audio = value) } },
        )
    }
    SettingsRow(
        "Resume where you left off",
        "Start unfinished episodes from the saved position.",
    ) {
        SettingsSwitch(settings.resumePlayback) { enabled -> onSettingsChange { it.copy(resumePlayback = enabled) } }
    }
    SettingsRow(
        "Autoplay next episode",
        "Offer the next episode in the last half-minute, and play it after a short countdown.",
    ) {
        SettingsSwitch(settings.autoplayNextEpisode) { enabled -> onSettingsChange { it.copy(autoplayNextEpisode = enabled) } }
    }
    SettingsSection("Skipping") {
        SettingsRow(
            "Skip button",
            "A one-press jump in the player when a show has no skip timings.",
        ) {
            SettingsSwitch(settings.showSkipButton) { enabled -> onSettingsChange { it.copy(showSkipButton = enabled) } }
        }
        if (settings.showSkipButton) {
            SettingsRow(
                "Skip button length",
                "How far one press jumps. A TV anime opening is 85 seconds.",
            ) {
                SettingsStepper(
                    value = settings.skipButtonSeconds,
                    valueLabel = "${settings.skipButtonSeconds} seconds",
                    range = 10..180,
                    step = 5,
                    onChange = { value -> onSettingsChange { it.copy(skipButtonSeconds = value) } },
                )
            }
        }
        SettingsRow(
            "Skip intro and recap automatically",
            "Jump over the recap and opening once they start, after a short countdown.",
        ) {
            SettingsSwitch(settings.autoSkipIntro) { enabled -> onSettingsChange { it.copy(autoSkipIntro = enabled) } }
        }
        SettingsRow(
            "Skip outro automatically",
            "Jump to the end of the episode when the credits reach the last quarter.",
        ) {
            SettingsSwitch(settings.autoSkipOutro) { enabled -> onSettingsChange { it.copy(autoSkipOutro = enabled) } }
        }
    }
    SettingsSection("Picture") {
        SettingsRow(
            "Screen size",
            "Fit shows the whole picture; Crop fills the window and cuts the edges; Fill stretches it.",
        ) {
            SettingsSegmented(
                choices = listOf("fit" to "Fit", "crop" to "Crop", "fill" to "Fill"),
                selected = settings.contentScale,
                onSelect = { value -> onSettingsChange { it.copy(contentScale = value) } },
            )
        }
        SettingsRow(
            "Volume",
            "Default player volume.",
        ) {
            SettingsSliderRow(
                value = settings.volume.toFloat(),
                valueRange = 0f..100f,
                valueLabel = "${settings.volume}%",
                onChange = { value -> onSettingsChange { it.copy(volume = value.toInt()) } },
            )
        }
    }
}

@Composable
private fun SubtitleSettings(settings: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsRow(
        "Show captions by default",
        "Start playback with text captions enabled.",
    ) {
        SettingsSwitch(settings.subtitlesByDefault) { enabled -> onSettingsChange { it.copy(subtitlesByDefault = enabled) } }
    }
    SettingsRow(
        "Preferred caption language",
        "Ask the player to choose this language when a matching track is available.",
    ) {
        SettingsDropdown(
            choices = listOf(
                "en" to "English",
                "ja" to "Japanese",
                "es" to "Spanish",
                "pt" to "Portuguese",
                "fr" to "French",
                "de" to "German",
                "it" to "Italian",
                "ru" to "Russian",
                "ar" to "Arabic",
                "zh" to "Chinese",
                "none" to "No preference",
            ),
            selected = settings.subtitleLanguage,
            onSelect = { value -> onSettingsChange { it.copy(subtitleLanguage = value) } },
        )
    }
    SettingsRow(
        "Caption size",
        "Text subtitle scaling.",
    ) {
        SettingsSliderRow(
            value = settings.subtitleScale,
            valueRange = 0.5f..2f,
            valueLabel = "${(settings.subtitleScale * 100).toInt()}%",
            onChange = { value -> onSettingsChange { it.copy(subtitleScale = value) } },
        )
    }
}

@Composable
private fun DownloadSettings(settings: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    val defaultDirectory = DirectMediaDownloader.defaultDownloadDirectory().toFile()
    val selectedDirectory = settings.downloadDirectory?.let(::File) ?: defaultDirectory
    SettingsRow(
        "Download folder",
        selectedDirectory.absolutePath,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton("Choose folder") {
                val chooser = JFileChooser(selectedDirectory)
                chooser.dialogTitle = "Choose the download folder"
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.isAcceptAllFileFilterUsed = false
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    onSettingsChange {
                        it.copy(downloadDirectory = chooser.selectedFile.toPath().toAbsolutePath().normalize().toString())
                    }
                }
            }
            if (settings.downloadDirectory != null) {
                SettingsButton("Use default") { onSettingsChange { it.copy(downloadDirectory = null) } }
            }
        }
    }
    SettingsNote("Direct video URLs and authorized Jellyfin or Emby server items can be downloaded.")
}

@Composable
private fun ServerSettings() {
    SettingsNote(
        "Jellyfin and Emby servers are added from the Media providers screen in the sidebar. " +
            "AniLife keeps those addresses and credentials on this computer.",
    )
}

@Composable
private fun AccountSettings(
    settings: AppSettings,
    discordState: DiscordPresenceState,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDiscordConnect: () -> Unit,
) {
    SettingsRow(
        "Discord Rich Presence",
        "Show “Idling” or the anime open in the player on your Discord profile.",
    ) {
        SettingsSwitch(settings.discordPresenceEnabled) { enabled ->
            onSettingsChange { it.copy(discordPresenceEnabled = enabled) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        DiscordConnectionRow(
            state = discordState,
            enabled = settings.discordPresenceEnabled,
            onConnect = onDiscordConnect,
        )
    }
    SettingsRow(
        "Discord Application ID",
        "The bundled AniLife application ID is ready to use. Only change this for your own Discord Developer Portal application; no client secret is needed.",
    ) {
        OutlinedTextField(
            value = settings.discordApplicationId,
            onValueChange = { value ->
                onSettingsChange { it.copy(discordApplicationId = value.filter(Char::isDigit).take(20)) }
            },
            modifier = Modifier.width(220.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp),
            label = { Text("Application ID", fontSize = 11.sp) },
        )
    }
    SettingsNote("MyAnimeList sync is available from Profile after signing in.")
}

@Composable
private fun NotificationSettings() {
    SettingsNote(
        "New episodes for titles you follow, release reminders and AniList activity will appear in " +
            "Notifications once accounts are connected.",
    )
}

@Composable
private fun ContentSettings(settings: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsRow(
        "Title display language",
        "Choose which AniList title to show across browse, search, library and the player.",
    ) {
        SettingsSegmented(
            choices = listOf("english" to "English", "romaji" to "Romaji", "native" to "Native"),
            selected = settings.titleLanguage,
            onSelect = { value -> onSettingsChange { it.copy(titleLanguage = value) } },
        )
    }
}

@Composable
private fun AppearanceSettings(settings: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsRow(
        "App font",
        "Typeface used across the interface.",
    ) {
        SettingsDropdown(
            choices = AppSettingsStore.fontFamilies.map { it to fontLabel(it) },
            selected = settings.appFont,
            onSelect = { value -> onSettingsChange { it.copy(appFont = value) } },
        )
    }
    SettingsSection("Theme colors") {
        ThemeEditor(
            css = settings.themeCss,
            onChange = { css -> onSettingsChange { it.copy(themeCss = css) } },
        )
    }
}

@Composable
private fun DataSettings(settings: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsRow(
        "Playback buffer",
        "Larger buffers use more memory and can help unstable connections.",
    ) {
        SettingsSegmented(
            choices = listOf("auto" to "Auto", "small" to "Small", "medium" to "Medium", "large" to "Large"),
            selected = settings.playbackBufferSize,
            onSelect = { value -> onSettingsChange { it.copy(playbackBufferSize = value) } },
        )
    }
    SettingsNote("Downloads are stored in the download folder chosen on the Downloads page.")
}

@Composable
private fun AppSettingsPage(settings: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsRow(
        "Play startup intro",
        "Show the AniLife animation when the app opens.",
    ) {
        SettingsSwitch(settings.playIntro) { enabled -> onSettingsChange { it.copy(playIntro = enabled) } }
    }
    SettingsRow(
        "Hardware decoding",
        "Reduce CPU use. Turn it off if playback has visual artifacts.",
    ) {
        SettingsSwitch(settings.hardwareDecoding) { enabled -> onSettingsChange { it.copy(hardwareDecoding = enabled) } }
    }
}

@Composable
private fun AboutSettings(mpvLibrary: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AniLife", color = AniLifeColors.Foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "An anime watching app derived from the ruined reputation of AniLife and made with love.",
            color = AniLifeColors.MutedForeground,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        AboutLine("Metadata", "AniList")
        AboutLine(
            "Player",
            if (mpvLibrary != null) "libmpv · $mpvLibrary" else "libmpv not found (set -DAniLife.mpv.library=<path>)",
        )
        AboutLine("Providers", "Connect your own Jellyfin or Emby server from Media providers.")
        AboutLine("Interface", "Compose Desktop 1.7.3 · Kotlin 2.2.20")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(text = "Made with love", container = AniLifeColors.Secondary, content = AniLifeColors.Foreground)
            Chip(text = "No telemetry", container = AniLifeColors.Secondary, content = AniLifeColors.Foreground)
        }
    }
}

private fun fontLabel(value: String): String = when (value) {
    "Serif" -> "Serif"
    "Monospaced" -> "Mono"
    "Cursive" -> "Cursive"
    else -> "Sans"
}

@Composable
private fun SettingsRow(
    title: String,
    description: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).padding(end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(description, color = AniLifeColors.MutedForeground, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        control()
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = AniLifeColors.Foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
        )
        content()
    }
}

@Composable
private fun SettingsNote(text: String) {
    Text(
        text,
        color = AniLifeColors.MutedForeground,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(vertical = 10.dp),
    )
}

@Composable
private fun SettingsSegmented(
    choices: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(9.dp))
            .background(AniLifeColors.SettingsControl)
            .border(1.dp, AniLifeColors.SettingsControlBorder, RoundedCornerShape(9.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        choices.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier.clip(RoundedCornerShape(7.dp))
                    .background(if (active) AniLifeColors.SettingsControlActive else Color.Transparent)
                    .then(if (active) Modifier else Modifier.clickable { onSelect(value) })
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    color = if (active) AniLifeColors.SettingsControlActiveForeground else AniLifeColors.MutedForeground,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.width(34.dp)
            .height(20.dp)
            .clip(CircleShape)
            .background(if (checked) AniLifeColors.SettingsControlActive else AniLifeColors.SettingsSwitchTrack)
            .clickable { onChange(!checked) },
    ) {
        Box(
            Modifier.offset(x = if (checked) 16.dp else 2.dp, y = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) AniLifeColors.SettingsControlActiveForeground else AniLifeColors.SettingsSwitchKnob),
        )
    }
}

@Composable
private fun SettingsStepper(
    value: Int,
    valueLabel: String,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(valueLabel, color = AniLifeColors.Foreground, fontSize = 13.sp)
        StepperButton("−") { onChange((value - step).coerceIn(range.first, range.last)) }
        StepperButton("+") { onChange((value + step).coerceIn(range.first, range.last)) }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AniLifeColors.SettingsControl)
            .border(1.dp, AniLifeColors.SettingsControlBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = AniLifeColors.Foreground, fontSize = 15.sp)
    }
}

@Composable
private fun SettingsSliderRow(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            modifier = Modifier.width(180.dp),
            colors = SliderDefaults.colors(
                thumbColor = AniLifeColors.SettingsControlActive,
                activeTrackColor = AniLifeColors.SettingsControlActive,
                inactiveTrackColor = AniLifeColors.SettingsSwitchTrack,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text(valueLabel, color = AniLifeColors.MutedForeground, fontSize = 12.sp, modifier = Modifier.width(46.dp))
    }
}

@Composable
private fun SettingsDropdown(
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val selectedLabel = choices.firstOrNull { it.first == selected }?.second ?: selected
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(AniLifeColors.SettingsControl)
                .border(1.dp, AniLifeColors.SettingsControlBorder, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(start = 12.dp, end = 9.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selectedLabel, color = AniLifeColors.Foreground, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = AniLifeColors.MutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 13.sp) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(AniLifeColors.SettingsControl)
            .border(1.dp, AniLifeColors.SettingsControlBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = AniLifeColors.Foreground, fontSize = 12.sp)
    }
}

@Composable
private fun DiscordConnectionRow(
    state: DiscordPresenceState,
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    val dot: Color
    val label: String
    when {
        !enabled -> {
            dot = AniLifeColors.MutedForeground
            label = "Presence is off"
        }
        state.status == DiscordPresenceStatus.Connected -> {
            dot = AniLifeColors.Success
            label = state.accountName?.let { "Connected as $it" } ?: "Connected to Discord"
        }
        state.status == DiscordPresenceStatus.Connecting -> {
            dot = AniLifeColors.Warning
            label = "Connecting to Discord…"
        }
        else -> {
            dot = AniLifeColors.Destructive
            label = "Discord isn't running"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(AniLifeColors.SettingsControl)
            .border(1.dp, AniLifeColors.SettingsControlBorder, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(label, color = AniLifeColors.MutedForeground, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        SettingsButton(if (state.status == DiscordPresenceStatus.Connected) "Reconnect" else "Connect") {
            onConnect()
        }
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(),
            color = AniLifeColors.MutedForeground.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(value, color = AniLifeColors.Foreground, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
