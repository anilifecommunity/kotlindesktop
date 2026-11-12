package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.AniListRepository
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.Chip
import me.enivzoy.anilife.app.ui.EmptyPanel
import me.enivzoy.anilife.app.ui.ErrorPanel
import me.enivzoy.anilife.app.ui.LoadState
import me.enivzoy.anilife.app.ui.PageScrollbar
import me.enivzoy.anilife.app.ui.RemoteImage
import me.enivzoy.anilife.app.ui.Shimmer
import me.enivzoy.anilife.app.ui.cardMeta
import me.enivzoy.anilife.app.ui.loadState
import me.enivzoy.anilife.app.ui.parseAnimeColor
import me.enivzoy.anilife.core.model.AiringEntry
import me.enivzoy.anilife.core.model.countdown
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

 



@Composable
fun ScheduleScreen(
    repository: AniListRepository,
    onOpen: (Long) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<List<AiringEntry>>>(LoadState.Loading) }

    LaunchedEffect(attempt) {
        state = LoadState.Loading
        state = loadState { repository.schedule() }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 30.dp, bottom = 8.dp)) {
            Text("Schedule", color = AniLifeColors.Foreground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Airing this week on AniList", color = AniLifeColors.MutedForeground, fontSize = 14.sp)
        }

        when (val current = state) {
            LoadState.Loading -> ScheduleSkeleton()
            is LoadState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorPanel(current.message, onRetry = { attempt++ })
            }
            is LoadState.Ready -> {
                if (current.value.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyPanel(
                            icon = Icons.Rounded.CalendarMonth,
                            title = "Nothing airing",
                            message = "AniList has no episodes scheduled for the next seven days.",
                        )
                    }
                } else {
                    ScheduleList(current.value, onOpen)
                }
            }
        }
    }
}

@Composable
private fun ScheduleList(entries: List<AiringEntry>, onOpen: (Long) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val grouped = remember(entries, zone) {
        entries.groupBy { Instant.ofEpochSecond(it.airingAt).atZone(zone).toLocalDate() }
    }
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 48.dp),
        ) {
            grouped.forEach { (date, dayEntries) ->
                item(key = "day-$date") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = dayLabel(date, today),
                            color = AniLifeColors.Foreground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${dayEntries.size} episode${if (dayEntries.size == 1) "" else "s"}",
                            color = AniLifeColors.MutedForeground,
                            fontSize = 12.sp,
                        )
                    }
                }
                items(items = dayEntries, key = { "${it.anime.id}-${it.airingAt}" }) { entry ->
                    ScheduleRow(entry = entry, zone = zone, onOpen = onOpen)
                }
            }
        }
        PageScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun ScheduleRow(entry: AiringEntry, zone: ZoneId, onOpen: (Long) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val time = remember(entry.airingAt, zone) {
        Instant.ofEpochSecond(entry.airingAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val tint = remember(entry.anime.color) { parseAnimeColor(entry.anime.color) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) AniLifeColors.Card else AniLifeColors.Background.copy(alpha = 0f))
            .clickable(interactionSource = interaction, indication = null) { onOpen(entry.anime.id) }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(62.dp)) {
            Text(time, color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(countdown(entry.airingAt), color = AniLifeColors.MutedForeground, fontSize = 11.sp, maxLines = 1)
        }
        RemoteImage(
            url = entry.anime.cover,
            contentDescription = entry.anime.title,
            modifier = Modifier.width(44.dp).height(66.dp).clip(RoundedCornerShape(6.dp)),
            placeholderColor = tint,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = entry.anime.title,
                color = AniLifeColors.Foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.anime.genres.isNotEmpty()) {
                    entry.anime.genres.take(3).joinToString(" · ")
                } else {
                    cardMeta(entry.anime)
                },
                color = AniLifeColors.MutedForeground,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Chip(
            text = "Episode ${entry.episode}",
            container = AniLifeColors.Secondary,
            content = AniLifeColors.Foreground,
            modifier = Modifier.widthIn(min = 72.dp),
        )
    }
}

@Composable
private fun ScheduleSkeleton() {
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Shimmer(Modifier.width(140.dp).height(18.dp), RoundedCornerShape(4.dp))
            }
            items(8) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Shimmer(Modifier.width(52.dp).height(28.dp), RoundedCornerShape(4.dp))
                    Spacer(Modifier.width(10.dp))
                    Shimmer(Modifier.width(44.dp).height(66.dp), RoundedCornerShape(6.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Shimmer(Modifier.fillMaxWidth(0.5f).height(14.dp), RoundedCornerShape(4.dp))
                        Shimmer(Modifier.fillMaxWidth(0.3f).height(10.dp), RoundedCornerShape(4.dp))
                    }
                }
            }
        }
        PageScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd))
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
}

