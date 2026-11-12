package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.enivzoy.anilife.app.data.MyAnimeListEntry
import me.enivzoy.anilife.app.data.MyAnimeListSync
import me.enivzoy.anilife.app.ui.AniLifeColors

@Composable
fun MyAnimeListScreen() {
    val user by MyAnimeListSync.user.collectAsState()
    val configured by MyAnimeListSync.configured.collectAsState()
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<MyAnimeListEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refreshList() {
        loading = true
        error = null
        try {
            entries = MyAnimeListSync.list()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            error = failure.message ?: "Couldn't sync your MyAnimeList account."
        } finally {
            loading = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(user?.id) {
        if (user != null) refreshList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp)) {
        Text("MyAnimeList", color = AniLifeColors.Foreground, fontSize = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("Sync your anime list and watched episode progress.", color = AniLifeColors.MutedForeground, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AniLifeColors.Card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (user == null) {
                Text("Connect your account", color = AniLifeColors.Foreground, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(
                    if (configured) "Client ID loaded from the local .env file."
                    else "Add MAL_CLIENT_ID to the local .env file to enable sign-in.",
                    color = AniLifeColors.MutedForeground,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Button(
                    onClick = {
                        MyAnimeListSync.configure()
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                MyAnimeListSync.connect()
                                refreshList()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Throwable) {
                                error = failure.message ?: "MyAnimeList sign-in failed."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = configured && !loading,
                ) {
                    if (loading) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) "Connecting…" else "Connect MyAnimeList")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = AniLifeColors.Primary)
                    Column(Modifier.weight(1f)) {
                        Text(user?.name.orEmpty(), color = AniLifeColors.Foreground, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text("Connected to MyAnimeList", color = AniLifeColors.MutedForeground, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { scope.launch { refreshList() } }, enabled = !loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Sync now")
                    }
                    TextButton(onClick = { scope.launch { MyAnimeListSync.disconnect(); entries = emptyList() } }) { Text("Disconnect") }
                }
                Text("Completed episodes sync to your MAL list after playback finishes.", color = AniLifeColors.MutedForeground, fontSize = 12.sp)
            }
            error?.let { Text(it, color = AniLifeColors.Destructive, fontSize = 12.sp) }
        }
        if (user != null) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Sync, contentDescription = null, tint = AniLifeColors.MutedForeground)
                Text("My list", color = AniLifeColors.Foreground, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text("${entries.size} titles", color = AniLifeColors.MutedForeground, fontSize = 12.sp)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AniLifeColors.Card.copy(alpha = 0.65f)).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.title, color = AniLifeColors.Foreground, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${entry.status.replace('_', ' ')} · ${entry.watched} watched", color = AniLifeColors.MutedForeground, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
