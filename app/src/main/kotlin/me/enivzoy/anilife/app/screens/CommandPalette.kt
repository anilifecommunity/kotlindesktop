package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import me.enivzoy.anilife.app.Screen
import me.enivzoy.anilife.app.data.AniListRepository
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.RemoteImage
import me.enivzoy.anilife.app.ui.cardMeta
import me.enivzoy.anilife.core.model.AnimeCard

/**
 * The command palette: Ctrl+K anywhere jumps to a title by name or to a page. AniList ranks the
 * results, so the palette lists them in that order instead of filtering them itself.
 */
@Composable
fun CommandPalette(
    repository: AniListRepository,
    onOpenAnime: (Long) -> Unit,
    onNavigate: (Screen) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AnimeCard>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val trimmed = query.trim()
    val searchingTitles = trimmed.length >= 2

    LaunchedEffect(trimmed) {
        if (!searchingTitles) {
            results = emptyList()
            searching = false
            failed = false
            return@LaunchedEffect
        }
        delay(300)
        searching = true
        failed = false
        try {
            results = repository.search(query = trimmed).results.take(10)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            results = emptyList()
            failed = true
        } finally {
            searching = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp)) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AniLifeColors.Card)
                    .border(1.dp, AniLifeColors.Border, RoundedCornerShape(16.dp)),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = AniLifeColors.MutedForeground,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (trimmed.isEmpty()) {
                            Text(
                                "Search anime or jump to a page…",
                                color = AniLifeColors.MutedForeground.copy(alpha = 0.7f),
                                fontSize = 15.sp,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = AniLifeColors.Foreground, fontSize = 15.sp),
                            cursorBrush = SolidColor(AniLifeColors.Primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AniLifeColors.Border))
                PaletteBody(
                    searchingTitles = searchingTitles,
                    results = results,
                    searching = searching,
                    failed = failed,
                    onOpenAnime = {
                        onDismiss()
                        onOpenAnime(it)
                    },
                    onNavigate = {
                        onDismiss()
                        onNavigate(it)
                    },
                )
            }
        }
    }
}

@Composable
private fun PaletteBody(
    searchingTitles: Boolean,
    results: List<AnimeCard>,
    searching: Boolean,
    failed: Boolean,
    onOpenAnime: (Long) -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        if (!searchingTitles) {
            Text(
                "PAGES",
                color = AniLifeColors.MutedForeground,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
            )
            PALETTE_PAGES.forEach { entry ->
                PaletteRow(
                    icon = entry.icon,
                    label = entry.label,
                    onClick = { onNavigate(entry.screen) },
                )
            }
            return@Column
        }
        Text(
            "TITLES",
            color = AniLifeColors.MutedForeground,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
        )
        when {
            searching -> Text(
                "Searching…",
                color = AniLifeColors.MutedForeground,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            failed -> Text(
                "Search failed. Check your connection and try again.",
                color = AniLifeColors.MutedForeground,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            results.isEmpty() -> Text(
                "No titles found.",
                color = AniLifeColors.MutedForeground,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            else -> LazyColumn(Modifier.fillMaxWidth()) {
                items(results, key = { it.id }) { anime ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onOpenAnime(anime.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemoteImage(
                            url = anime.cover,
                            contentDescription = null,
                            modifier = Modifier.size(width = 30.dp, height = 42.dp)
                                .clip(RoundedCornerShape(5.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                anime.title,
                                color = AniLifeColors.Foreground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                            Text(
                                cardMeta(anime),
                                color = AniLifeColors.MutedForeground,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = AniLifeColors.MutedForeground, modifier = Modifier.size(17.dp))
        Text(label, color = AniLifeColors.Foreground, fontSize = 14.sp)
    }
}

private data class PalettePage(val label: String, val icon: ImageVector, val screen: Screen)

private val PALETTE_PAGES = listOf(
    PalettePage("Home", Icons.Rounded.Home, Screen.Home),
    PalettePage("Browse", Icons.Rounded.Explore, Screen.Browse),
    PalettePage("Schedule", Icons.Rounded.CalendarMonth, Screen.Schedule),
    PalettePage("Library", Icons.Rounded.VideoLibrary, Screen.Library),
    PalettePage("Downloads", Icons.Rounded.Download, Screen.Downloads),
    PalettePage("Media providers", Icons.Rounded.Cloud, Screen.Providers),
    PalettePage("Notifications", Icons.Rounded.Notifications, Screen.Notifications),
    PalettePage("Profile", Icons.Rounded.Person, Screen.Profile),
    PalettePage("Settings", Icons.Rounded.Settings, Screen.Settings),
)
