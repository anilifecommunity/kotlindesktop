package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.Screen
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.BrandMark
import me.enivzoy.anilife.app.ui.Chip
import me.enivzoy.anilife.app.ui.ThemeEditor
import me.enivzoy.anilife.app.data.AppSettings
import me.enivzoy.anilife.app.data.DirectMediaDownloader
import me.enivzoy.anilife.app.data.DiscordPresenceState
import me.enivzoy.anilife.app.data.DiscordPresenceStatus
import java.io.File
import javax.swing.JFileChooser

/**
 * The sections whose engines are still being ported (list sync, downloads, notifications, the
 * signed-in profile). Each says plainly what it will hold rather than pretending to be empty.
 */
@Composable
fun PlaceholderScreen(screen: Screen, modifier: Modifier = Modifier) {
    val info = describe(screen) ?: return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AniLifeColors.Card)
                .border(1.dp, AniLifeColors.Border, RoundedCornerShape(18.dp))
                .padding(horizontal = 36.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(AniLifeColors.CardHover),
                contentAlignment = Alignment.Center,
            ) {
                Icon(info.icon, contentDescription = null, tint = AniLifeColors.MutedForeground, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(info.title, color = AniLifeColors.Foreground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = info.description,
                color = AniLifeColors.MutedForeground,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Chip(text = info.badge, container = AniLifeColors.Secondary, content = AniLifeColors.MutedForeground)
        }
    }
}

private data class PlaceholderInfo(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val badge: String,
)

private fun describe(screen: Screen): PlaceholderInfo? = when (screen) {
    Screen.Library -> PlaceholderInfo(
        icon = Icons.Rounded.VideoLibrary,
        title = "Library",
        description = "Your AniList list, watching progress and local history will live here, with the same shelves, sorting and filters as the original.",
        badge = "Sign-in and list sync are part of the engine phase",
    )
    Screen.Downloads -> PlaceholderInfo(
        icon = Icons.Rounded.Download,
        title = "Downloads",
        description = "Episode downloads, queued and finished transfers, and quality choices will appear here once the provider engine is ported.",
        badge = "Download engine not ported yet",
    )
    Screen.Notifications -> PlaceholderInfo(
        icon = Icons.Rounded.Notifications,
        title = "Notifications",
        description = "New episodes for titles you follow, release reminders and AniList activity will arrive here.",
        badge = "Notifications need the signed-in account",
    )
    Screen.Profile -> PlaceholderInfo(
        icon = Icons.Rounded.Person,
        title = "Profile",
        description = "AniList and MyAnimeList accounts, statistics and viewing history. Signing in is part of the next phase.",
        badge = "Accounts not connected yet",
    )
    else -> null
}

/** Settings → About: what this build is and where its pieces come from. */
@Composable
fun SettingsScreen(
    mpvLibrary: String?,
    settings: AppSettings,
    discordState: DiscordPresenceState,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDiscordConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeSection by remember { mutableStateOf("About") }
    var searchQuery by remember { mutableStateOf("") }
    val searchableSettings = listOf(
        SettingSearchEntry("Theme colors", "Appearance", "css theme colors palette editor custom look style"),
        SettingSearchEntry("About AniLife", "About", "app version metadata player"),
        SettingSearchEntry("Media providers", "About", "providers servers streaming jellyfin emby self hosted"),
        SettingSearchEntry("Default audio", "Playback", "sub dubbed audio track language"),
        SettingSearchEntry("Show captions by default", "Playback", "captions subtitles text"),
        SettingSearchEntry("Preferred caption language", "Playback", "captions subtitles language"),
        SettingSearchEntry("Resume playback", "Playback", "continue watch later progress"),
        SettingSearchEntry("Caption size", "Playback", "captions subtitles text"),
        SettingSearchEntry("Screen size", "Playback", "display scaling"),
        SettingSearchEntry("Hardware decoding", "Playback", "gpu acceleration decoder"),
        SettingSearchEntry("Playback buffer", "Playback", "stream buffering"),
        SettingSearchEntry("Player volume", "Playback", "audio sound"),
        SettingSearchEntry("Skip button", "Playback", "opening ending intro outro"),
        SettingSearchEntry("Skip length", "Playback", "seconds duration"),
        SettingSearchEntry("Download folder", "Downloads", "directory location path storage"),
        SettingSearchEntry("Startup intro", "About", "animation splash opening"),
        SettingSearchEntry("Title display language", "About", "anime name title romaji english native language"),
        SettingSearchEntry("Discord Rich Presence", "Discord", "Discord activity status watching idle connect"),
    )
    val matchingSettings = searchableSettings.filter {
        searchQuery.isNotBlank() &&
            "${it.label} ${it.section} ${it.keywords}".contains(searchQuery.trim(), ignoreCase = true)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 30.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 34.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Settings", color = AniLifeColors.Foreground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("AniLife preferences", color = AniLifeColors.MutedForeground, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(28.dp))

        Column(
            Modifier.widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AniLifeColors.Card)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = "Search settings", tint = AniLifeColors.MutedForeground)
                Spacer(Modifier.width(9.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = AniLifeColors.Foreground, fontSize = 14.sp),
                    cursorBrush = SolidColor(AniLifeColors.Foreground),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (searchQuery.isBlank()) {
                                Text("Search settings", color = AniLifeColors.MutedForeground, fontSize = 14.sp)
                            }
                            inner()
                        }
                    },
                )
            }
            if (searchQuery.isNotBlank() && matchingSettings.isEmpty()) {
                SettingsGroupCard("No matching settings") {
                    Text(
                        "Try a term such as captions, audio, volume, playback, or download folder.",
                        color = AniLifeColors.MutedForeground,
                        fontSize = 12.sp,
                    )
                }
            }
            if (matchingSettings.isNotEmpty()) {
                SettingsGroupCard("Search results") {
                    matchingSettings.forEach { result ->
                        OutlinedButton(
                            onClick = {
                                activeSection = result.section
                                searchQuery = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${result.label} · ${result.section}", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("About", "Discord", "Playback", "Downloads", "Appearance").forEach { section ->
                    if (activeSection == section) {
                        Button(
                            onClick = { activeSection = section },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AniLifeColors.Primary,
                                contentColor = AniLifeColors.PrimaryForeground,
                            ),
                        ) { Text(section) }
                    } else {
                        OutlinedButton(onClick = { activeSection = section }) { Text(section) }
                    }
                }
            }
            if (activeSection == "Playback") SettingsGroupCard("Playback") {
                SettingsChoice(
                    label = "Default audio",
                    description = "Used by Auto detect and direct downloads.",
                    choices = listOf("sub" to "Subtitled", "dub" to "Dubbed"),
                    selected = settings.audio,
                    onSelect = { value -> onSettingsChange { it.copy(audio = value) } },
                )
                SettingsSwitch(
                    "Show captions by default",
                    "Start playback with text captions enabled.",
                    settings.subtitlesByDefault,
                ) { enabled -> onSettingsChange { it.copy(subtitlesByDefault = enabled) } }
                SettingsDropdown(
                    label = "Preferred caption language",
                    description = "Ask the player to choose this language when a matching track is available.",
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
                SettingsSwitch(
                    "Resume playback",
                    "Save local and direct-stream positions and continue from them next time.",
                    settings.resumePlayback,
                ) { enabled -> onSettingsChange { it.copy(resumePlayback = enabled) } }
                SettingsSlider(
                    label = "Caption size",
                    description = "Text subtitle scaling.",
                    value = settings.subtitleScale,
                    valueRange = 0.5f..2f,
                    valueLabel = "${(settings.subtitleScale * 100).toInt()}%",
                ) { value -> onSettingsChange { it.copy(subtitleScale = value) } }
                SettingsChoice(
                    label = "Screen size",
                    description = "Fit preserves the whole picture; Crop fills the player; Fill stretches the picture.",
                    choices = listOf("fit" to "Fit", "crop" to "Crop", "fill" to "Fill"),
                    selected = settings.contentScale,
                    onSelect = { value -> onSettingsChange { it.copy(contentScale = value) } },
                )
                SettingsSwitch(
                    "Hardware decoding",
                    "Reduce CPU use. Turn it off if playback has visual artifacts.",
                    settings.hardwareDecoding,
                ) { enabled -> onSettingsChange { it.copy(hardwareDecoding = enabled) } }
                SettingsChoice(
                    label = "Playback buffer",
                    description = "Larger buffers use more memory and can help unstable connections.",
                    choices = listOf("auto" to "Auto", "small" to "Small", "medium" to "Medium", "large" to "Large"),
                    selected = settings.playbackBufferSize,
                    onSelect = { value -> onSettingsChange { it.copy(playbackBufferSize = value) } },
                )
                SettingsSlider(
                    label = "Player volume",
                    description = "Default player volume.",
                    value = settings.volume.toFloat(),
                    valueRange = 0f..100f,
                    valueLabel = "${settings.volume}%",
                ) { value -> onSettingsChange { it.copy(volume = value.toInt()) } }
                SettingsSwitch(
                    "Show skip button",
                    "Show a one-press forward jump in the player controls.",
                    settings.showSkipButton,
                ) { enabled -> onSettingsChange { it.copy(showSkipButton = enabled) } }
                if (settings.showSkipButton) {
                    SettingsSlider(
                        label = "Skip length",
                        description = "The number of seconds one press skips.",
                        value = settings.skipButtonSeconds.toFloat(),
                        valueRange = 10f..180f,
                        valueLabel = "${settings.skipButtonSeconds} seconds",
                    ) { value -> onSettingsChange { it.copy(skipButtonSeconds = value.toInt()) } }
                }
            }

            if (activeSection == "Downloads") SettingsGroupCard("Downloads") {
                val defaultDirectory = DirectMediaDownloader.defaultDownloadDirectory()
                val selectedDirectory = settings.downloadDirectory?.let(::File) ?: defaultDirectory.toFile()
                AboutLine("Download folder", selectedDirectory.absolutePath)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val chooser = JFileChooser(selectedDirectory)
                            chooser.dialogTitle = "Choose the download folder"
                            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            chooser.isAcceptAllFileFilterUsed = false
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                onSettingsChange {
                                    it.copy(downloadDirectory = chooser.selectedFile.toPath().toAbsolutePath().normalize().toString())
                                }
                            }
                        },
                    ) {
                        Text("Choose folder")
                    }
                    if (settings.downloadDirectory != null) {
                        OutlinedButton(onClick = { onSettingsChange { it.copy(downloadDirectory = null) } }) {
                            Text("Use default")
                        }
                    }
                }
                Text(
                    "Direct video URLs and authorized Jellyfin or Emby server items can be downloaded.",
                    color = AniLifeColors.MutedForeground,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }

            if (activeSection == "About") SettingsGroupCard("About") {
                Text("AniLife", color = AniLifeColors.Foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "An anime watching app derived from the ruined reputation of AniLife and made with love.",
                    color = AniLifeColors.MutedForeground,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
                AboutLine("Metadata", "AniList")
                AboutLine("Player", if (mpvLibrary != null) "libmpv · $mpvLibrary" else "libmpv not found (set -DAniLife.mpv.library=<path>)")
                AboutLine("Providers", "Connect your own Jellyfin or Emby server from the Providers section.")
                AboutLine("Interface", "Compose Desktop 1.7.3 · Kotlin 2.2.20")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(text = "Made with love", container = AniLifeColors.Secondary, content = AniLifeColors.Foreground)
                    Chip(text = "No telemetry", container = AniLifeColors.Secondary, content = AniLifeColors.Foreground)
                }
                SettingsSwitch(
                    "Play startup intro",
                    "Show the AniLife animation when the app opens.",
                    settings.playIntro,
                ) { enabled -> onSettingsChange { it.copy(playIntro = enabled) } }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Title display language", color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Choose which AniList title to show across browse, search, library and the player.", color = AniLifeColors.MutedForeground, fontSize = 12.sp, lineHeight = 17.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("english" to "English", "romaji" to "Romaji", "native" to "Native").forEach { (value, label) ->
                            val selected = settings.titleLanguage == value
                            if (selected) {
                                Button(
                                    onClick = { onSettingsChange { it.copy(titleLanguage = value) } },
                                    colors = ButtonDefaults.buttonColors(containerColor = AniLifeColors.Primary, contentColor = AniLifeColors.PrimaryForeground),
                                    shape = RoundedCornerShape(8.dp),
                                ) { Text(label, fontSize = 13.sp) }
                            } else {
                                OutlinedButton(
                                    onClick = { onSettingsChange { it.copy(titleLanguage = value) } },
                                    shape = RoundedCornerShape(8.dp),
                                ) { Text(label, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }

            if (activeSection == "Appearance") SettingsGroupCard("Theme colors") {
                ThemeEditor(
                    css = settings.themeCss,
                    onChange = { css -> onSettingsChange { it.copy(themeCss = css) } },
                )
            }

            if (activeSection == "Discord") SettingsGroupCard("Discord Rich Presence") {
                SettingsSwitch(
                    "Discord Rich Presence",
                    "Show “Idling” or the anime open in the player on your Discord profile.",
                    settings.discordPresenceEnabled,
                ) { enabled -> onSettingsChange { it.copy(discordPresenceEnabled = enabled) } }
                DiscordConnectionRow(
                    state = discordState,
                    enabled = settings.discordPresenceEnabled,
                    onConnect = onDiscordConnect,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Discord Application ID", color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "The bundled AniLife application ID is ready to use. Only change this for your own Discord Developer Portal application; no client secret is needed.",
                        color = AniLifeColors.MutedForeground,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    OutlinedTextField(
                        value = settings.discordApplicationId,
                        onValueChange = { value ->
                            onSettingsChange { it.copy(discordApplicationId = value.filter(Char::isDigit).take(20)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Application ID") },
                    )
                }
            }
        }
    }
}

private data class SettingSearchEntry(
    val label: String,
    val section: String,
    val keywords: String,
)

@Composable
private fun SettingsGroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AniLifeColors.Card)
            .border(1.dp, AniLifeColors.Border, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = AniLifeColors.Foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, color = AniLifeColors.MutedForeground, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DiscordConnectionRow(
    state: DiscordPresenceState,
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    val dot: androidx.compose.ui.graphics.Color
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
            .clip(RoundedCornerShape(10.dp))
            .background(AniLifeColors.Secondary.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(label, color = AniLifeColors.MutedForeground, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onConnect,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (state.status == DiscordPresenceStatus.Connected) "Reconnect" else "Connect to Discord",
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SettingsChoice(
    label: String,
    description: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(description, color = AniLifeColors.MutedForeground, fontSize = 12.sp, lineHeight = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            choices.forEach { (value, title) ->
                if (selected == value) {
                    Button(
                        onClick = { onSelect(value) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AniLifeColors.Primary,
                            contentColor = AniLifeColors.PrimaryForeground,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    ) { Text(title, fontSize = 12.sp) }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(value) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    ) { Text(title, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun SettingsDropdown(
    label: String,
    description: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val selectedLabel = choices.firstOrNull { it.first == selected }?.second ?: selected
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(description, color = AniLifeColors.MutedForeground, fontSize = 12.sp, lineHeight = 17.sp)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(selectedLabel) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { (value, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueLabel, color = AniLifeColors.MutedForeground, fontSize = 12.sp)
        }
        Text(description, color = AniLifeColors.MutedForeground, fontSize = 12.sp, lineHeight = 17.sp)
        Slider(value = value, onValueChange = onChange, valueRange = valueRange)
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), color = AniLifeColors.MutedForeground.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = AniLifeColors.Foreground, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

