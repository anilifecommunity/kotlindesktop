package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.MediaProviderItem
import me.enivzoy.anilife.app.data.MediaProviderManager
import me.enivzoy.anilife.app.data.MediaProviderServer
import me.enivzoy.anilife.app.data.DirectMediaDownloader
import me.enivzoy.anilife.app.data.AppSettings
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.RemoteImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.nio.file.Path

@Composable
fun ProvidersScreen(
    manager: MediaProviderManager,
    settings: AppSettings,
    onPlay: (String, String, Map<String, String>) -> Unit,
) {
    val servers by manager.servers.collectAsState()
    val scope = rememberCoroutineScope()
    var serverName by remember { mutableStateOf("") }
    var serverAddress by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var reconnectId by remember { mutableStateOf<String?>(null) }
    var connectedServer by remember { mutableStateOf<MediaProviderServer?>(null) }
    var currentItems by remember { mutableStateOf<List<MediaProviderItem>>(emptyList()) }
    var breadcrumbs by remember { mutableStateOf<List<MediaProviderItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun connect(serverId: String?) {
        loading = true
        status = null
        scope.launch {
            try {
                val resolved = if (serverId == null) {
                    manager.addAndConnect(serverName, serverAddress, username, password)
                } else {
                    val server = servers.firstOrNull { it.id == serverId }
                        ?: error("That saved media server was removed.")
                    manager.connect(server.id, password)
                    server
                }
                val libraries = manager.libraries(resolved.id)
                connectedServer = resolved
                breadcrumbs = emptyList()
                currentItems = libraries
                status = if (resolved.address.startsWith("http://", true)) {
                    "Connected. This server uses unencrypted HTTP; use HTTPS outside your trusted network."
                } else {
                    "Connected to ${resolved.name}."
                }
                serverName = resolved.name
                serverAddress = resolved.address
                username = resolved.username
                password = ""
                reconnectId = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = error.message ?: "Couldn't connect to the media server."
            } finally {
                loading = false
            }
        }
    }

    fun openContainer(item: MediaProviderItem) {
        val active = connectedServer ?: return
        loading = true
        status = null
        scope.launch {
            try {
                val children = manager.browse(active.id, item.id)
                breadcrumbs = breadcrumbs + item
                currentItems = children
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = error.message ?: "Couldn't load that media folder."
            } finally {
                loading = false
            }
        }
    }

    fun goUp() {
        val active = connectedServer ?: return
        val parentTrail = breadcrumbs.dropLast(1)
        loading = true
        status = null
        scope.launch {
            try {
                currentItems = if (parentTrail.isEmpty()) manager.libraries(active.id)
                else manager.browse(active.id, parentTrail.last().id)
                breadcrumbs = parentTrail
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = error.message ?: "Couldn't load the previous media folder."
            } finally {
                loading = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Cloud, contentDescription = null, tint = AniLifeColors.Foreground, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(13.dp))
            Column {
                Text("Media providers", color = AniLifeColors.Foreground, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Connect to your Jellyfin or Emby media server.", color = AniLifeColors.MutedForeground, fontSize = 13.sp)
            }
        }

        ProviderCard {
            Text(
                "These are your own or authorized media servers. Passwords and access tokens are kept in memory only and are not written to disk.",
                color = AniLifeColors.MutedForeground,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            if (serverAddress.trimStart().startsWith("http://", true)) {
                Text(
                    "HTTP does not encrypt credentials or video traffic. Prefer HTTPS unless this is a trusted local network.",
                    color = AniLifeColors.Score,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = { Text("Server name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = serverAddress,
                onValueChange = { serverAddress = it },
                label = { Text("Server address") },
                placeholder = { Text("https://media.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { connect(reconnectId) },
                enabled = !loading && password.isNotEmpty() &&
                    (reconnectId != null || (serverName.isNotBlank() && serverAddress.isNotBlank() && username.isNotBlank())),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (reconnectId == null) "Add & connect" else "Connect")
            }
        }

        if (servers.isNotEmpty()) {
            ProviderCard {
                Text("Saved servers", color = AniLifeColors.Foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                servers.forEach { server ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AniLifeColors.CardHover)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(server.name, color = AniLifeColors.Foreground, fontWeight = FontWeight.Medium)
                            Text(server.address, color = AniLifeColors.MutedForeground, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (server.username.isBlank()) "Username required" else "User: ${server.username}", color = AniLifeColors.MutedForeground, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                connectedServer?.let { manager.disconnect(it.id) }
                                serverName = server.name
                                serverAddress = server.address
                                username = server.username
                                password = ""
                                reconnectId = server.id
                                connectedServer = null
                            },
                        ) { Text("Connect") }
                        OutlinedButton(onClick = {
                            manager.remove(server.id)
                            if (reconnectId == server.id) {
                                reconnectId = null
                                password = ""
                            }
                            if (connectedServer?.id == server.id) {
                                connectedServer = null
                                currentItems = emptyList()
                            }
                            status = "${server.name} removed."
                        }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove ${server.name}")
                        }
                    }
                }
            }
        }

        status?.let {
            Text(it, color = if (it.contains("failed", true) || it.contains("couldn't", true)) AniLifeColors.Destructive else AniLifeColors.MutedForeground, fontSize = 12.sp)
        }

        connectedServer?.let { active ->
            ProviderCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (breadcrumbs.isNotEmpty()) {
                        OutlinedButton(onClick = ::goUp, enabled = !loading) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            breadcrumbs.lastOrNull()?.name ?: "Libraries",
                            color = AniLifeColors.Foreground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(active.name, color = AniLifeColors.MutedForeground, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = {
                        manager.disconnect(active.id)
                        connectedServer = null
                        currentItems = emptyList()
                        breadcrumbs = emptyList()
                    }) { Text("Disconnect") }
                }
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else if (currentItems.isEmpty()) {
                    Text("No media was found here.", color = AniLifeColors.MutedForeground, fontSize = 12.sp)
                } else {
                    currentItems.forEach { item ->
                        ProviderItemRow(
                            item = item,
                            onOpen = { openContainer(item) },
                            onPlay = {
                                try {
                                    val playback = manager.playback(active.id, item.id)
                                    onPlay(item.name, playback.url, playback.headers)
                                } catch (error: Exception) {
                                    status = error.message ?: "Couldn't start playback."
                                }
                            },
                            onDownload = {
                                loading = true
                                status = "Downloading ${item.name}…"
                                scope.launch {
                                    try {
                                        val downloadRoot = settings.downloadDirectory?.let(Path::of)
                                            ?: DirectMediaDownloader.defaultDownloadDirectory()
                                        val downloaded = manager.download(active.id, item, downloadRoot)
                                        status = "Saved ${downloaded.path.fileName} to Downloads."
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        status = error.message ?: "Couldn't download this video."
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AniLifeColors.Card)
            .border(1.dp, AniLifeColors.Border, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ProviderItemRow(
    item: MediaProviderItem,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    val canPlay = item.type.equals("Movie", true) || item.type.equals("Episode", true) || item.type.equals("Video", true)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AniLifeColors.CardHover)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = item.imageUrl,
            contentDescription = item.name,
            modifier = Modifier.size(width = 78.dp, height = 52.dp).clip(RoundedCornerShape(7.dp)),
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.name, color = AniLifeColors.Foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.episodeNumber?.let { "${item.type} · Episode $it" } ?: item.type,
                color = AniLifeColors.MutedForeground,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.overview?.takeIf(String::isNotBlank)?.let {
                Text(it, color = AniLifeColors.MutedForeground, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (canPlay) {
            OutlinedButton(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${item.name}")
            }
            OutlinedButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, contentDescription = "Download ${item.name}")
            }
        } else {
            OutlinedButton(onClick = onOpen) {
                Icon(Icons.Rounded.Folder, contentDescription = "Open ${item.name}")
            }
        }
    }
}

