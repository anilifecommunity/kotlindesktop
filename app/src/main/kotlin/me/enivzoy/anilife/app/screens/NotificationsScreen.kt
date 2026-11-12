package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.Notice
import me.enivzoy.anilife.app.data.NoticeStore
import me.enivzoy.anilife.app.data.ReleaseNotifier
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.Chip
import me.enivzoy.anilife.app.ui.EmptyPanel
import me.enivzoy.anilife.app.ui.RemoteImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class NoticeTab(val label: String) {
    All("All"),
    Airing("Airing"),
    Downloads("Downloads"),
}

/**
 * The app's notification log: new episodes of the titles in the library, reminders and finished
 * downloads, newest first and grouped by age.
 */
@Composable
fun NotificationsScreen(
    store: NoticeStore,
    notifier: ReleaseNotifier?,
    onOpenAnime: (Long) -> Unit,
) {
    val notices by store.notices.collectAsState()
    var tab by remember { mutableStateOf(NoticeTab.All) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_EVERY_MS)
            notifier?.let { runCatching { it.check() } }
        }
    }

    val shown = notices.filter { notice ->
        when (tab) {
            NoticeTab.All -> true
            NoticeTab.Airing -> notice.kind == "airing" || notice.kind == "reminder"
            NoticeTab.Downloads -> notice.kind == "download"
        }
    }
    val grouped = remember(shown) { groupByAge(shown) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 30.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Notifications", color = AniLifeColors.Foreground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "New episodes of the titles you follow, and finished downloads",
                    color = AniLifeColors.MutedForeground,
                    fontSize = 14.sp,
                )
            }
            IconAction(
                icon = Icons.Rounded.Refresh,
                label = "Refresh",
                enabled = !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        notifier?.let { runCatching { it.check() } }
                        checking = false
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            IconAction(
                icon = Icons.Rounded.Check,
                label = "Mark all read",
                enabled = notices.any { !it.read },
                onClick = { store.markAllRead() },
            )
            Spacer(Modifier.width(8.dp))
            IconAction(
                icon = Icons.Rounded.DeleteSweep,
                label = "Clear",
                enabled = notices.isNotEmpty(),
                onClick = { store.clear() },
            )
        }

        Row(
            Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NoticeTab.entries.forEach { entry ->
                val count = notices.count { it.unreadFor(entry) }
                Chip(
                    text = if (count > 0) "${entry.label} · $count" else entry.label,
                    container = if (tab == entry) AniLifeColors.Primary else AniLifeColors.Secondary,
                    content = if (tab == entry) AniLifeColors.PrimaryForeground else AniLifeColors.MutedForeground,
                    modifier = Modifier.clickable { tab = entry },
                )
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyPanel(
                    icon = Icons.Rounded.Notifications,
                    title = "Nothing here yet",
                    message = "New episodes of the titles in your library and finished downloads show up here.",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 32.dp,
                    end = 32.dp,
                    bottom = 48.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (name, list) ->
                    item(key = "group-$name") {
                        Text(
                            name.uppercase(),
                            color = AniLifeColors.MutedForeground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        )
                    }
                    items(list, key = { it.id }) { notice ->
                        NoticeCard(notice, onOpenAnime)
                    }
                }
            }
        }
    }
}

private fun Notice.unreadFor(tab: NoticeTab): Boolean = when (tab) {
    NoticeTab.All -> !read
    NoticeTab.Airing -> !read && (kind == "airing" || kind == "reminder")
    NoticeTab.Downloads -> !read && kind == "download"
}

@Composable
private fun NoticeCard(notice: Notice, onOpenAnime: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AniLifeColors.Card)
            .then(
                if (notice.read) {
                    Modifier.border(1.dp, AniLifeColors.Border, RoundedCornerShape(14.dp))
                } else {
                    Modifier.border(1.dp, AniLifeColors.Primary, RoundedCornerShape(14.dp))
                }
            )
            .clickable(enabled = notice.animeId != null) { notice.animeId?.let(onOpenAnime) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!notice.image.isNullOrBlank()) {
            RemoteImage(
                url = notice.image,
                contentDescription = null,
                modifier = Modifier.size(width = 46.dp, height = 66.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                Modifier.size(width = 46.dp, height = 66.dp).clip(RoundedCornerShape(8.dp))
                    .background(AniLifeColors.CardHover),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = AniLifeColors.MutedForeground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                notice.title,
                color = AniLifeColors.Foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            notice.body?.let {
                Text(
                    it,
                    color = AniLifeColors.MutedForeground,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            notice.createdAt.toDateLabel(),
            color = AniLifeColors.MutedForeground,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(AniLifeColors.Card)
            .border(1.dp, AniLifeColors.Border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) AniLifeColors.Foreground else AniLifeColors.MutedForeground.copy(alpha = 0.4f),
            modifier = Modifier.size(17.dp),
        )
    }
}

private fun groupByAge(notices: List<Notice>): List<Pair<String, List<Notice>>> {
    val now = System.currentTimeMillis() / 1000
    val groups = linkedMapOf<String, MutableList<Notice>>()
    notices.forEach { notice ->
        val age = now - notice.createdAt / 1000
        val name = when {
            age < 7 * 86_400 -> "This week"
            age < 30 * 86_400 -> "Last 30 days"
            else -> "Over a month ago"
        }
        groups.getOrPut(name) { mutableListOf() }.add(notice)
    }
    return groups.map { (name, list) -> name to list }
}

private fun Long.toDateLabel(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private const val REFRESH_EVERY_MS = 15 * 60 * 1000L
