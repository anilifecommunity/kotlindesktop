package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.AnimeEpisode
import me.enivzoy.anilife.app.data.AnimeSubtitleSource
import me.enivzoy.anilife.app.data.AnimeVideoSource
import me.enivzoy.anilife.app.player.PlayerSubtitleTrack
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.RemoteImage

@Composable
internal fun PlayerPanelLayer(
    panel: String?,
    onDismiss: () -> Unit,
    sources: List<AnimeVideoSource>,
    audio: String,
    selectedSourceUrl: String?,
    subtitles: List<PlayerSubtitleTrack>,
    subtitleSources: List<AnimeSubtitleSource>,
    episodes: List<AnimeEpisode>,
    currentEpisode: Double?,
    posterUrl: String?,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    screenSize: String,
    onScreenSizeChange: (String) -> Unit,
    autoplayNext: Boolean,
    onAutoplayNextChange: (Boolean) -> Unit,
    onAudioChange: (String) -> Unit,
    onSourceSelect: (AnimeVideoSource) -> Unit,
    onEpisodeSelect: (Double) -> Unit,
    onSubtitleSelect: (Int?, AnimeSubtitleSource?) -> Unit,
) {
    if (panel == null) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.52f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(420.dp)
                .background(Color(0xFF101010), RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .clickable(enabled = false) { }
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NOW PLAYING", color = AniLifeColors.MutedForeground, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                    Text(panelTitle(panel), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close ${panelTitle(panel)}", tint = AniLifeColors.MutedForeground)
                }
            }
            Spacer(Modifier.height(12.dp))
            when (panel) {
                "sources" -> SourcesPanel(sources, audio, selectedSourceUrl, subtitles, onAudioChange, onSourceSelect)
                "episodes" -> EpisodesPanel(episodes, currentEpisode, posterUrl, onEpisodeSelect)
                "subtitles" -> SubtitlesPanel(subtitles, subtitleSources, onSubtitleSelect)
                "settings" -> SettingsPanel(volume, onVolumeChange, speed, onSpeedChange, screenSize, onScreenSizeChange, autoplayNext, onAutoplayNextChange)
            }
        }
    }
}

@Composable
private fun SourcesPanel(
    sources: List<AnimeVideoSource>,
    audio: String,
    selectedUrl: String?,
    subtitles: List<PlayerSubtitleTrack>,
    onAudioChange: (String) -> Unit,
    onSelect: (AnimeVideoSource) -> Unit,
) {
    Text("AUDIO", color = AniLifeColors.MutedForeground, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFF1A1A1A)).padding(4.dp)) {
        listOf("Sub", "Dub").forEach { option ->
            val value = if (option == "Dub") "dub" else "sub"
            Box(
                Modifier.weight(1f).clip(CircleShape)
                    .background(if (audio.equals(value, true)) Color(0xFF343434) else Color.Transparent)
                    .clickable { onAudioChange(value) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(option, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("SERVER", color = AniLifeColors.MutedForeground, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(8.dp))
    if (sources.isEmpty()) EmptyPanelMessage("No servers are available for this episode.")
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(sources, key = { "${it.provider}:${it.server}:${it.audio}:${it.url}" }) { source ->
            val selected = source.url == selectedUrl
            PanelChoice(
                title = listOf(source.provider, source.server).filter(String::isNotBlank).joinToString(" · "),
                subtitle = buildString {
                    if (selected) append("Playing now · ")
                    val available = source.subtitles.map(AnimeSubtitleSource::label).distinct().ifEmpty { subtitles.map(PlayerSubtitleTrack::label).distinct() }
                    append(if (available.isEmpty()) "Subtitle tracks load with the video" else "Subtitles you can switch · ${available.joinToString(", ")}")
                    source.quality?.let { append(" · $it") }
                },
                selected = selected,
                onClick = { onSelect(source) },
            )
        }
    }
}

@Composable
private fun EpisodesPanel(
    episodes: List<AnimeEpisode>,
    current: Double?,
    posterUrl: String?,
    onSelect: (Double) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(episodes, current) {
        val index = episodes.indexOfFirst { it.number == current }
        if (index >= 0) listState.scrollToItem(index)
    }
    if (episodes.isEmpty()) EmptyPanelMessage("No episode list is available.")
    else LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(episodes, key = { it.number }) { episode ->
            val selected = episode.number == current
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0xFF303034) else Color(0xFF1A1A1A))
                    .border(if (selected) 1.dp else 0.dp, if (selected) Color.White.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onSelect(episode.number) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatEpisodeNumber(episode.number), Modifier.width(28.dp), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.size(width = 80.dp, height = 46.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2B2B2B))) {
                    RemoteImage(episode.thumbnail ?: posterUrl.orEmpty(), null, Modifier.fillMaxSize(), placeholderColor = Color(0xFF2B2B2B))
                    if (selected) Icon(Icons.Rounded.Check, null, Modifier.align(Alignment.Center), tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(episode.title?.takeIf(String::isNotBlank) ?: "Episode ${formatEpisodeNumber(episode.number)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (selected) Text("Now playing", color = AniLifeColors.MutedForeground, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SubtitlesPanel(
    tracks: List<PlayerSubtitleTrack>,
    serverSubtitles: List<AnimeSubtitleSource>,
    onSelect: (Int?, AnimeSubtitleSource?) -> Unit,
) {
    Text("SUBTITLES", color = AniLifeColors.MutedForeground, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(8.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "subtitles-off") {
            PanelChoice("Off", "Turn subtitles off", selected = tracks.none(PlayerSubtitleTrack::selected), onClick = { onSelect(null, null) })
        }
        items(tracks, key = PlayerSubtitleTrack::id) { track ->
            val fromServer = serverSubtitles.any { it.label.equals(track.label, ignoreCase = true) }
            PanelChoice(
                title = track.label,
                subtitle = buildString {
                    append(when {
                        fromServer -> "Downloaded from the current server"
                        track.external -> "From this video or its subtitle file"
                        else -> "Embedded in the video"
                    })
                    track.language?.let { append(" · $it") }
                },
                selected = track.selected,
                onClick = { onSelect(track.id, null) },
            )
        }
        val unlisted = serverSubtitles.filterNot { source -> tracks.any { it.label.equals(source.label, ignoreCase = true) } }
        items(unlisted, key = { "provider:${it.url}" }) { source ->
            PanelChoice(
                title = source.label,
                subtitle = "Available from this server · ${source.language}",
                selected = false,
                onClick = { onSelect(null, source) },
            )
        }
        if (tracks.isEmpty()) item {
            EmptyPanelMessage("This video has not exposed any subtitle tracks yet. Tracks embedded in the stream or loaded from its subtitle files will appear here.")
        }
    }
}

@Composable
private fun SettingsPanel(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    screenSize: String,
    onScreenSizeChange: (String) -> Unit,
    autoplayNext: Boolean,
    onAutoplayNextChange: (Boolean) -> Unit,
) {
    PanelSection("VOLUME", "${volume.toInt()}%")
    Slider(value = volume, onValueChange = onVolumeChange, valueRange = 0f..100f)
    Spacer(Modifier.height(12.dp))
    PanelSection("PLAYBACK SPEED", "${speed}×")
    Slider(value = speed, onValueChange = onSpeedChange, valueRange = 0.5f..2.5f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
            Text("${value}×", Modifier.clip(CircleShape).clickable { onSpeedChange(value) }.padding(horizontal = 7.dp, vertical = 5.dp), color = if (speed == value) Color.White else AniLifeColors.MutedForeground, fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(20.dp))
    PanelSection("SCREEN SIZE")
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFF1A1A1A)).padding(4.dp)) {
        listOf("fit" to "Fit", "crop" to "Crop", "fill" to "Fill").forEach { (value, label) ->
            Box(
                Modifier.weight(1f).clip(CircleShape).background(if (screenSize == value) Color(0xFF343434) else Color.Transparent)
                    .clickable { onScreenSizeChange(value) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Auto-play next episode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Continue when this episode ends", color = AniLifeColors.MutedForeground, fontSize = 10.sp)
        }
        Switch(checked = autoplayNext, onCheckedChange = onAutoplayNextChange)
    }
}

@Composable
private fun PanelSection(label: String, value: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AniLifeColors.MutedForeground, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        value?.let { Text(it, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PanelChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF303034) else Color(0xFF191919))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = AniLifeColors.MutedForeground, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(18.dp).border(1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.36f), CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun EmptyPanelMessage(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(vertical = 12.dp), color = AniLifeColors.MutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
}

private fun panelTitle(panel: String): String = when (panel) {
    "sources" -> "Sources"
    "episodes" -> "Episodes"
    "subtitles" -> "Subtitles"
    else -> "Settings"
}
