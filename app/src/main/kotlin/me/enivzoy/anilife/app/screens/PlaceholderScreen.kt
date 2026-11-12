package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.Screen
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.Chip

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
