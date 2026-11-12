package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import me.enivzoy.anilife.app.data.AniListRepository
import me.enivzoy.anilife.app.data.AppSettings
import me.enivzoy.anilife.app.data.AutoDetectResult
import me.enivzoy.anilife.app.data.DetectedFolder
import me.enivzoy.anilife.app.data.LocalEpisode
import me.enivzoy.anilife.app.data.PortableImportResult
import me.enivzoy.anilife.app.data.ContinueStoreInstance
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.EmptyPanel
import me.enivzoy.anilife.app.ui.ErrorPanel
import me.enivzoy.anilife.app.ui.LoadState
import me.enivzoy.anilife.app.ui.PageScrollbar
import me.enivzoy.anilife.app.ui.RemoteImage
import me.enivzoy.anilife.app.ui.cardMeta
import me.enivzoy.anilife.app.ui.loadState
import me.enivzoy.anilife.app.ui.parseAnimeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.io.File
import java.awt.Desktop
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val DownloadSurface = Color(0xFF1E2024)
private val DownloadSurfaceRaised = Color(0xFF25272C)
private val DownloadOutline = Color(0xFF3A3D43)
private val DownloadText = Color(0xFFE8E9EC)
private val DownloadMuted = Color(0xFFA2A5AC)
private val DownloadAccent = Color(0xFFFF8FC9)

@Composable
fun DownloadsScreen(
    repository: AniListRepository,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onPlay: (String, String) -> Unit,
    onOpen: (Long) -> Unit = {},
) {
    val episodes by repository.localEpisodes.collectAsState()
    val continueEntries by ContinueStoreInstance.entries.collectAsState()
    val scope = rememberCoroutineScope()
    var mediaRoot by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf<AutoDetectResult?>(null) }
    var scanAnime by remember { mutableStateOf("") }
    var scanEpisode by remember { mutableStateOf("") }
    var selectedDownloadAnime by remember { mutableStateOf<me.enivzoy.anilife.core.model.AnimeCard?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    val candidateIndex = remember { mutableStateMapOf<String, Int>() }

    fun selectMediaFolder(): Path? {
        val chooser = JFileChooser(mediaRoot?.let(::File))
        chooser.dialogTitle = "Choose the folder containing your anime folders"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
    }

    fun beginScan(root: Path) {
        mediaRoot = root.toAbsolutePath().normalize().toString()
        scan = null
        error = null
        scanAnime = ""
        scanEpisode = ""
        scanning = true
        scope.launch {
            try {
                scan = loadState {
                    repository.detectLocalMedia(root, settings.audio) { name, episode ->
                        scanAnime = name
                        scanEpisode = episode
                    }
                }.let { result ->
                    when (result) {
                        LoadState.Loading -> null
                        is LoadState.Failed -> throw IllegalStateException(result.message)
                        is LoadState.Ready -> result.value
                    }
                }
                candidateIndex.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                error = t.message?.takeIf(String::isNotBlank) ?: "Couldn't scan this folder."
            } finally {
                scanning = false
                scanAnime = ""
                scanEpisode = ""
            }
        }
    }

    fun importPortable(json: Path, root: Path) {
        importing = true
        error = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.importPortableLibrary(json, root) }
                message = buildImportMessage(result)
                mediaRoot = root.toAbsolutePath().normalize().toString()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                error = t.message?.takeIf(String::isNotBlank) ?: "Couldn't import the portable library."
            } finally {
                importing = false
            }
        }
    }

    fun selectPortableLibrary() {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Import an AniLife portable library"
        chooser.fileFilter = FileNameExtensionFilter("AniLife library JSON", "json")
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        val json = chooser.selectedFile.toPath()
        val defaultRoot = mediaRoot?.let(Path::of) ?: json.parent
        val folderChooser = JFileChooser(defaultRoot?.toFile())
        folderChooser.dialogTitle = "Choose the folder containing the anime folders"
        folderChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (folderChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            importPortable(json, folderChooser.selectedFile.toPath())
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        error?.let { problem ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                ErrorPanel(problem, onRetry = { error = null })
            }
        }

        val detected = scan
        if (selectedDownloadAnime == null && detected != null) {
            ScanReview(
                result = detected,
                candidateIndex = candidateIndex,
                onCandidate = { folderName, index ->
                    candidateIndex[folderName] = index
                    val selectedFolder = detected.folders.firstOrNull { it.name == folderName }
                    val selectedAnime = selectedFolder?.matches?.getOrNull(index)
                    if (selectedFolder != null && selectedAnime != null) {
                        scope.launch {
                            val titles = repository.episodeTitles(selectedAnime, selectedFolder.episodes.map(LocalEpisode::episode))
                            if (candidateIndex[folderName] != index) return@launch
                            val currentScan = scan ?: return@launch
                            scan = currentScan.copy(
                                folders = currentScan.folders.map { current ->
                                    if (current.name != folderName) current
                                    else current.copy(episodes = current.episodes.map { episode ->
                                        episode.copy(episodeTitle = titles[episode.episode])
                                    })
                                },
                            )
                        }
                    }
                },
                onCancel = { scan = null },
                onImport = {
                    importing = true
                    scope.launch {
                        try {
                            val chosen = detected.folders.flatMap { folder ->
                                val anime = folder.matches[candidateIndex[folder.name] ?: 0]
                                val titles = repository.episodeTitles(anime, folder.episodes.map(LocalEpisode::episode))
                                folder.episodes.map { entry ->
                                    entry.copy(anime = anime, episodeTitle = titles[entry.episode] ?: entry.episodeTitle)
                                }
                            }
                            repository.addLocalEpisodes(chosen)
                            repository.addToLibrary(chosen.map { it.anime }.distinctBy { it.id })
                            message = "Added ${chosen.size} local episode${if (chosen.size == 1) "" else "s"} across ${detected.folders.size} matched anime."
                            scan = null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            error = t.message?.takeIf(String::isNotBlank) ?: "Couldn't save the detected episodes."
                        } finally {
                            importing = false
                        }
                    }
                },
            )
        } else if (selectedDownloadAnime == null && episodes.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyPanel(
                    icon = Icons.Rounded.Download,
                    title = "No local episodes yet",
                    message = "Auto detect scans folders and matches their anime titles. Import a portable library JSON to restore the files listed by your other AniLife install.",
                    action = {
                        OutlinedButton(onClick = { selectMediaFolder()?.let(::beginScan) }, enabled = !scanning && !importing) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Auto detect")
                        }
                    },
                )
            }
        } else {
            EpisodeList(
                episodes = episodes,
                onPlay = onPlay,
                onRemove = repository::removeLocalEpisode,
                onOpen = onOpen,
                selectedAnime = selectedDownloadAnime,
                onSelectAnime = { selectedDownloadAnime = it },
                continueEpisode = continueEntries.firstOrNull { it.anime.id == selectedDownloadAnime?.id }?.progress?.episode,
                onAutoDetect = { selectMediaFolder()?.let(::beginScan) },
                onImportPortable = ::selectPortableLibrary,
                onDirectDownload = { downloadError = null; showDownloadDialog = true },
                actionsEnabled = !scanning && !importing,
                audioTrack = settings.audio,
                onAudioChange = { audio -> onSettingsChange { it.copy(audio = audio) } },
            )
        }
    }

    if (scanning) {
        Box(
            Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AniLifeColors.Card)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Checking | ${scanAnime.ifBlank { "..." }}",
                    color = AniLifeColors.Foreground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (scanEpisode.isNotBlank()) {
                    Text(
                        "Episode $scanEpisode",
                        color = AniLifeColors.MutedForeground,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp).padding(top = 2.dp),
                    strokeWidth = 2.dp,
                    color = AniLifeColors.Primary,
                )
            }
        }
    }
    }

    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            containerColor = AniLifeColors.Card,
            titleContentColor = AniLifeColors.Foreground,
            textContentColor = AniLifeColors.MutedForeground,
            title = { Text("Downloads updated", fontWeight = FontWeight.SemiBold) },
            text = { Text(text, fontSize = 13.sp, lineHeight = 20.sp) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("Done", color = AniLifeColors.Primary) } },
        )
    }

    if (showDownloadDialog) {
        DirectDownloadDialog(
            audio = settings.audio,
            error = downloadError,
            onDismiss = { if (!importing) showDownloadDialog = false },
            onDownload = { title, episode, url ->
                importing = true
                downloadError = null
                error = null
                scope.launch {
                    try {
                        val downloaded = repository.downloadDirect(
                            url,
                            title,
                            episode,
                            settings.audio,
                            settings.downloadDirectory?.let(Path::of),
                        )
                        val episodeTitle = downloaded.episodeTitle?.let { ": $it" }.orEmpty()
                        message = "${downloaded.anime.title} · Episode ${downloaded.episode.episodeLabel()}$episodeTitle is ready offline."
                        showDownloadDialog = false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        downloadError = t.message?.takeIf(String::isNotBlank) ?: "The video download failed."
                    } finally {
                        importing = false
                    }
                }
            },
        )
    }
}

@Composable
private fun DirectDownloadDialog(
    audio: String,
    error: String?,
    onDismiss: () -> Unit,
    onDownload: (String, Double, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var episode by remember { mutableStateOf("1") }
    var url by remember { mutableStateOf("") }
    val number = episode.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AniLifeColors.Card,
        titleContentColor = AniLifeColors.Foreground,
        textContentColor = AniLifeColors.MutedForeground,
        title = { Text("Download a direct video URL", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Only downloads direct video files you provide. HLS/DASH playlists can be played from the player instead.", fontSize = 12.sp, lineHeight = 18.sp)
                if (error != null) Text(error, color = AniLifeColors.Destructive, fontSize = 12.sp, lineHeight = 18.sp)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Anime title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = episode, onValueChange = { episode = it }, label = { Text("Episode number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Direct HTTP(S) video URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Audio: ${audio.uppercase()} · saved under AniLife/Downloads", fontSize = 11.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AniLifeColors.MutedForeground) } },
        confirmButton = {
            Button(
                onClick = { if (number != null) onDownload(title.trim(), number, url.trim()) },
                enabled = title.isNotBlank() && number != null && number > 0.0 && url.startsWith("http", ignoreCase = true),
                colors = ButtonDefaults.buttonColors(containerColor = AniLifeColors.Primary, contentColor = AniLifeColors.PrimaryForeground),
            ) { Text("Download", fontWeight = FontWeight.SemiBold) }
        },
    )
}

@Composable
private fun ScanReview(
    result: AutoDetectResult,
    candidateIndex: Map<String, Int>,
    onCandidate: (String, Int) -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${result.folders.size} folders matched · ${result.folders.sumOf { it.episodes.size }} episodes",
                color = AniLifeColors.Foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text("Cancel", color = AniLifeColors.MutedForeground) }
            Button(
                onClick = onImport,
                enabled = result.folders.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AniLifeColors.Primary,
                    contentColor = AniLifeColors.PrimaryForeground,
                ),
            ) { Text("Add episodes", fontWeight = FontWeight.SemiBold) }
        }
        if (result.skipped.isNotEmpty()) {
            Text(
                "Skipped ${result.skipped.size} item(s) · ${result.skipped.take(3).joinToString(" · ")}",
                color = AniLifeColors.MutedForeground,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
            )
        }
        val state = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(result.folders, key = { it.name }) { folder ->
                    MatchRow(folder, candidateIndex[folder.name] ?: 0) { onCandidate(folder.name, it) }
                }
            }
            PageScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun MatchRow(folder: DetectedFolder, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val selected = folder.matches.getOrNull(selectedIndex) ?: folder.matches.first()
    var expanded by remember(folder.name) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AniLifeColors.Card)
            .border(1.dp, AniLifeColors.Border, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = selected.cover,
            contentDescription = selected.title,
            modifier = Modifier.size(width = 48.dp, height = 68.dp).clip(RoundedCornerShape(6.dp)),
            placeholderColor = parseAnimeColor(selected.color),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(folder.name, color = AniLifeColors.MutedForeground, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(selected.title, color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${folder.episodes.size} episodes · ${cardMeta(selected)} · ${"%.0f".format(folder.confidence * 100)}% match",
                color = AniLifeColors.MutedForeground,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val titles = folder.episodes.mapNotNull { entry ->
                entry.episodeTitle?.let { "E${entry.episode.episodeLabel()}: $it" }
            }
            if (titles.isNotEmpty()) {
                Text(
                    titles.take(3).joinToString(" · "),
                    color = AniLifeColors.MutedForeground,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text("Change", color = AniLifeColors.Primary, fontSize = 12.sp) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                folder.matches.forEachIndexed { index, anime ->
                    DropdownMenuItem(
                        text = { Text(anime.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onSelect(index); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<LocalEpisode>,
    onPlay: (String, String) -> Unit,
    onRemove: (LocalEpisode) -> Unit,
    onOpen: (Long) -> Unit,
    selectedAnime: me.enivzoy.anilife.core.model.AnimeCard?,
    onSelectAnime: (me.enivzoy.anilife.core.model.AnimeCard?) -> Unit,
    continueEpisode: Double?,
    onAutoDetect: () -> Unit,
    onImportPortable: () -> Unit,
    onDirectDownload: () -> Unit,
    actionsEnabled: Boolean,
    audioTrack: String,
    onAudioChange: (String) -> Unit,
) {
    val groups = remember(episodes) {
        episodes.groupBy { it.anime.id }
            .values
            .map { entries ->
                entries.first().anime to entries.sortedWith(compareBy({ it.episode }, { it.audio }))
            }
            .sortedBy { it.first.title.lowercase() }
    }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("title") }
    var tab by remember { mutableStateOf("library") }
    var actionsExpanded by remember { mutableStateOf(false) }

    val filteredGroups = remember(groups, searchQuery, sortMode) {
        val q = searchQuery.trim().lowercase()
        val base = if (q.isBlank()) groups else groups.filter { it.first.title.lowercase().contains(q) }
        when (sortMode) {
            "recent" -> base.sortedByDescending { it.second.maxOfOrNull { e -> e.path.toString() } ?: "" }
            "count" -> base.sortedByDescending { it.second.size }
            else -> base.sortedBy { it.first.title.lowercase() }
        }
    }

    val selectedEpisodes = remember(episodes, selectedAnime) {
        selectedAnime?.let { anime ->
            episodes.filter { it.anime.id == anime.id }
                .distinctBy { "${it.anime.id}|${it.episode}|${it.audio}" }
                .sortedWith(compareBy({ it.episode }, { it.audio }))
        } ?: emptyList()
    }

    if (selectedAnime != null) {
        val anime = selectedAnime
        var gridLayout by remember(anime.id) { mutableStateOf(false) }
        var selectedKeys by remember(anime.id) { mutableStateOf(emptySet<String>()) }
        val episodeKeys = selectedEpisodes.map(::downloadEpisodeKey).toSet()
        val allSelected = episodeKeys.isNotEmpty() && selectedKeys.containsAll(episodeKeys)
        val resumeEntry = selectedEpisodes.firstOrNull { it.episode == continueEpisode } ?: selectedEpisodes.firstOrNull()
        Column(Modifier.fillMaxSize()) {
            TextButton(
                onClick = { onSelectAnime(null) },
                colors = ButtonDefaults.textButtonColors(contentColor = DownloadMuted),
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 2.dp),
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("Downloads", fontSize = 13.sp)
            }
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(190.dp)
                    .clip(RoundedCornerShape(18.dp)).background(DownloadSurface),
            ) {
                RemoteImage(
                    url = anime.cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.2f },
                    placeholderColor = DownloadSurface,
                )
                Box(Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to DownloadSurface.copy(alpha = 0.96f),
                        0.62f to DownloadSurface.copy(alpha = 0.84f),
                        1f to DownloadSurface.copy(alpha = 0.55f),
                    ),
                ))
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    RemoteImage(
                        url = anime.cover,
                        contentDescription = anime.title,
                        modifier = Modifier.size(width = 96.dp, height = 144.dp).clip(RoundedCornerShape(10.dp)),
                        placeholderColor = parseAnimeColor(anime.color),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(anime.title, color = DownloadText, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("${selectedEpisodes.size} episodes saved · ${formatBytes(selectedEpisodes.sumOf(LocalEpisode::bytes))}")
                                continueEpisode?.let { append(" · Continue at Episode ${it.episodeLabel()}") }
                            },
                            color = DownloadMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { resumeEntry?.let { onPlay(anime.title, it.path.toUri().toString()) } },
                                enabled = resumeEntry != null,
                                colors = ButtonDefaults.buttonColors(containerColor = DownloadAccent, contentColor = Color(0xFF291522)),
                                shape = RoundedCornerShape(9.dp),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if (continueEpisode != null) "Continue Episode ${continueEpisode.episodeLabel()}" else "Play Episode ${resumeEntry?.episode?.episodeLabel() ?: ""}", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { onOpen(anime.id) },
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.dp, DownloadOutline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DownloadText),
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Title page")
                            }
                            OutlinedButton(
                                onClick = { resumeEntry?.let { showEpisodeFolder(it) } },
                                enabled = resumeEntry != null,
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.dp, DownloadOutline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DownloadText),
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Show in folder")
                            }
                            TextButton(
                                onClick = { selectedEpisodes.forEach(onRemove); selectedKeys = emptySet() },
                                enabled = selectedEpisodes.isNotEmpty(),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = AniLifeColors.Destructive, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Remove all", color = AniLifeColors.Destructive)
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = allSelected, onCheckedChange = { checked -> selectedKeys = if (checked) episodeKeys else emptySet() }, colors = CheckboxDefaults.colors(checkedColor = DownloadAccent, uncheckedColor = DownloadOutline, checkmarkColor = Color(0xFF291522)))
                Text(if (selectedKeys.isEmpty()) "Episodes" else "${selectedKeys.size} selected", color = DownloadText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (selectedKeys.isNotEmpty()) {
                    TextButton(onClick = {
                        selectedEpisodes.filter { downloadEpisodeKey(it) in selectedKeys }.forEach(onRemove)
                        selectedKeys = emptySet()
                    }, colors = ButtonDefaults.textButtonColors(contentColor = DownloadMuted)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = DownloadMuted, modifier = Modifier.size(16.dp))
                        Text("Remove selected", color = DownloadMuted)
                    }
                }
                Row(Modifier.clip(RoundedCornerShape(9.dp)).background(DownloadSurfaceRaised).padding(3.dp)) {
                    IconButton(
                        onClick = { gridLayout = false },
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(if (!gridLayout) DownloadOutline else Color.Transparent),
                    ) {
                        Icon(Icons.Rounded.ViewList, contentDescription = "List view", tint = if (!gridLayout) DownloadText else DownloadMuted, modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = { gridLayout = true },
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(if (gridLayout) DownloadOutline else Color.Transparent),
                    ) {
                        Icon(Icons.Rounded.GridView, contentDescription = "Grid view", tint = if (gridLayout) DownloadText else DownloadMuted, modifier = Modifier.size(17.dp))
                    }
                }
            }
            if (gridLayout) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(selectedEpisodes) { entry ->
                        DownloadEpisodeGridCard(
                            entry = entry,
                            selected = downloadEpisodeKey(entry) in selectedKeys,
                            onToggle = { checked -> selectedKeys = selectedKeys.toMutableSet().apply { if (checked) add(downloadEpisodeKey(entry)) else remove(downloadEpisodeKey(entry)) } },
                            onPlay = onPlay,
                            onShowInFolder = ::showEpisodeFolder,
                            onRemove = onRemove,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(selectedEpisodes) { entry ->
                        DownloadEpisodeRow(
                            entry = entry,
                            selected = downloadEpisodeKey(entry) in selectedKeys,
                            onToggle = { checked -> selectedKeys = selectedKeys.toMutableSet().apply { if (checked) add(downloadEpisodeKey(entry)) else remove(downloadEpisodeKey(entry)) } },
                            onPlay = onPlay,
                            onShowInFolder = ::showEpisodeFolder,
                            onRemove = onRemove,
                        )
                    }
                }
            }
        }
    } else {
    // Library grid view
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${groups.size} anime · ${episodes.size} episode${if (episodes.size == 1) "" else "s"} on this device",
                color = AniLifeColors.Foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("library" to "Library", "queue" to "Queue").forEach { (value, label) ->
                    if (tab == value) {
                        Button(
                            onClick = { tab = value },
                            colors = ButtonDefaults.buttonColors(containerColor = AniLifeColors.Primary, contentColor = AniLifeColors.PrimaryForeground),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text(label, fontSize = 12.sp) }
                    } else {
                        OutlinedButton(onClick = { tab = value }, shape = RoundedCornerShape(8.dp)) { Text(label, fontSize = 12.sp) }
                    }
                }
            }
            Box {
                TextButton(onClick = { actionsExpanded = true }, enabled = actionsEnabled) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Download actions", tint = AniLifeColors.MutedForeground)
                }
                DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Auto detect local episodes") },
                        onClick = { actionsExpanded = false; onAutoDetect() },
                        enabled = actionsEnabled,
                    )
                    DropdownMenuItem(
                        text = { Text("Import portable library") },
                        onClick = { actionsExpanded = false; onImportPortable() },
                        enabled = actionsEnabled,
                    )
                    DropdownMenuItem(
                        text = { Text("Download direct URL") },
                        onClick = { actionsExpanded = false; onDirectDownload() },
                        enabled = actionsEnabled,
                    )
                    DropdownMenuItem(
                        text = { Text(if (audioTrack == "sub") "Audio: Sub ✓" else "Audio: Sub") },
                        onClick = { actionsExpanded = false; onAudioChange("sub") },
                    )
                    DropdownMenuItem(
                        text = { Text(if (audioTrack == "dub") "Audio: Dub ✓" else "Audio: Dub") },
                        onClick = { actionsExpanded = false; onAudioChange("dub") },
                    )
                }
            }
        }
        // Search + sort
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text("Search downloads", color = AniLifeColors.MutedForeground, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            )
            OutlinedButton(onClick = { sortMode = if (sortMode == "title") "count" else if (sortMode == "count") "recent" else "title" }, shape = RoundedCornerShape(8.dp)) {
                Text("Sort: ${sortMode.replaceFirstChar { it.uppercase() }}", fontSize = 12.sp)
            }
        }

        if (tab == "queue") {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No active downloads.", color = AniLifeColors.MutedForeground, fontSize = 13.sp)
            }
        } else if (filteredGroups.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyPanel(icon = Icons.Rounded.Download, title = "No downloads yet", message = "Use Auto detect to register your local anime folders.")
            }
        } else {
            val gridState = rememberLazyGridState()
            Box(Modifier.weight(1f)) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    state = gridState,
                    contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    filteredGroups.forEach { (anime, animeEpisodes) ->
                        item(key = "anime-${anime.id}") {
                            DownloadAnimeCard(
                                anime = anime,
                                downloadedCount = animeEpisodes.size,
                                downloadedBytes = animeEpisodes.sumOf(LocalEpisode::bytes),
                                onClick = { onSelectAnime(anime) },
                            )
                        }
                    }
                }
                PageScrollbar(rememberScrollbarAdapter(gridState), Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

}

@Composable
private fun DownloadAnimeCard(
    anime: me.enivzoy.anilife.core.model.AnimeCard,
    downloadedCount: Int,
    downloadedBytes: Long,
    onClick: () -> Unit,
) {
    val totalEpisodes = anime.episodes?.takeIf { it > 0 }
    val downloadFraction = totalEpisodes?.let { (downloadedCount.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (hovered) 1.06f else 1f, androidx.compose.animation.core.tween(200), label = "card-scale")
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(DownloadSurface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(parseAnimeColor(anime.color)),
        ) {
            RemoteImage(
                url = anime.cover,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                placeholderColor = parseAnimeColor(anime.color),
            )
            Text(
                text = totalEpisodes?.let { "$downloadedCount / $it" } ?: "$downloadedCount",
                color = AniLifeColors.Foreground,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(AniLifeColors.Background.copy(alpha = 0.82f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 2.dp).height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AniLifeColors.Border),
        ) {
            Box(
                Modifier.fillMaxWidth(downloadFraction).height(3.dp)
                    .background(AniLifeColors.Primary),
            )
        }
        Text(
            text = anime.title,
            color = AniLifeColors.Foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp, start = 8.dp, end = 8.dp),
        )
        Text(
            text = buildString {
                append(formatBytes(downloadedBytes))
                append(" · ")
                if (totalEpisodes != null) append("$downloadedCount of $totalEpisodes downloaded")
                else append("$downloadedCount downloaded")
            },
            color = AniLifeColors.MutedForeground,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun DownloadEpisodeRow(
    entry: LocalEpisode,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onPlay: (String, String) -> Unit,
    onShowInFolder: (LocalEpisode) -> Unit,
    onRemove: (LocalEpisode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(DownloadSurfaceRaised)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = onToggle, colors = CheckboxDefaults.colors(checkedColor = DownloadAccent, uncheckedColor = DownloadOutline, checkmarkColor = Color(0xFF291522)))
        RemoteImage(
            url = entry.anime.cover,
            contentDescription = "Episode ${entry.episode.episodeLabel()} thumbnail",
            modifier = Modifier.size(width = 140.dp, height = 78.dp).clip(RoundedCornerShape(8.dp)),
            placeholderColor = parseAnimeColor(entry.anime.color),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Episode ${entry.episode.episodeLabel()}", color = DownloadText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.episodeTitle?.takeIf(String::isNotBlank) ?: "${entry.audio.uppercase()} audio", color = DownloadMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(entry.bytes), color = DownloadMuted.copy(alpha = 0.75f), fontSize = 11.sp)
        }
        IconButton(onClick = { onPlay(entry.anime.title, entry.path.toUri().toString()) }) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play episode", tint = DownloadText)
        }
        IconButton(onClick = { onShowInFolder(entry) }) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = "Show episode in folder", tint = DownloadMuted)
        }
        IconButton(onClick = { onRemove(entry) }) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove episode", tint = DownloadMuted)
        }
    }
}

@Composable
private fun DownloadEpisodeGridCard(
    entry: LocalEpisode,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onPlay: (String, String) -> Unit,
    onShowInFolder: (LocalEpisode) -> Unit,
    onRemove: (LocalEpisode) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(DownloadSurfaceRaised)) {
        Box {
            RemoteImage(
                url = entry.anime.cover,
                contentDescription = "Episode ${entry.episode.episodeLabel()} thumbnail",
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                placeholderColor = parseAnimeColor(entry.anime.color),
            )
            Checkbox(checked = selected, onCheckedChange = onToggle, modifier = Modifier.align(Alignment.TopStart), colors = CheckboxDefaults.colors(checkedColor = DownloadAccent, uncheckedColor = Color(0xFFE0E1E4).copy(alpha = 0.8f), checkmarkColor = Color(0xFF291522)))
        }
        Text("Episode ${entry.episode.episodeLabel()}", color = DownloadText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(entry.episodeTitle?.takeIf(String::isNotBlank) ?: formatBytes(entry.bytes), color = DownloadMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { onPlay(entry.anime.title, entry.path.toUri().toString()) }) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play episode", tint = DownloadText) }
            IconButton(onClick = { onShowInFolder(entry) }) { Icon(Icons.Rounded.FolderOpen, contentDescription = "Show episode in folder", tint = DownloadMuted) }
            IconButton(onClick = { onRemove(entry) }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove episode", tint = DownloadMuted) }
        }
    }
}

private fun downloadEpisodeKey(entry: LocalEpisode): String = "${entry.anime.id}|${entry.episode}|${entry.audio}"

private fun showEpisodeFolder(entry: LocalEpisode) {
    val parent = entry.path.parent?.toFile() ?: return
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(parent)
    }
}

private fun buildImportMessage(result: PortableImportResult): String = buildString {
    append("Imported ${result.anime.size} anime and ${result.episodes.size} local episodes.")
    if (result.missingFiles.isNotEmpty()) {
        append("\n\n${result.missingFiles.size} listed episode file(s) were not found and were skipped:")
        result.missingFiles.take(8).forEach { append("\n• ").append(it) }
        if (result.missingFiles.size > 8) append("\n… and ${result.missingFiles.size - 8} more")
    }
}

private fun Double.episodeLabel(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024))
    else -> "%.0f KB".format(bytes / 1024.0)
}

