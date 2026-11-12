package me.enivzoy.anilife.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.AniListRepository
import me.enivzoy.anilife.app.data.AnimeVideoProviders
import me.enivzoy.anilife.app.data.AppSettingsStore
import me.enivzoy.anilife.app.data.DEFAULT_DISCORD_APPLICATION_ID
import me.enivzoy.anilife.app.data.DiscordPresenceManager
import me.enivzoy.anilife.app.data.DownloadQueueManager
import me.enivzoy.anilife.app.data.DiscordPresenceTarget
import me.enivzoy.anilife.app.data.ProviderCatalog
import me.enivzoy.anilife.app.screens.BrowseScreen
import me.enivzoy.anilife.app.screens.AnimeWatchScreen
import me.enivzoy.anilife.app.screens.CommandPalette
import me.enivzoy.anilife.app.screens.DetailScreen
import me.enivzoy.anilife.app.screens.DownloadsScreen
import me.enivzoy.anilife.app.screens.HomeScreen
import me.enivzoy.anilife.app.screens.LibraryScreen
import me.enivzoy.anilife.app.screens.PlayerScreen
import me.enivzoy.anilife.app.screens.ProvidersScreen
import me.enivzoy.anilife.app.screens.ScheduleScreen
import me.enivzoy.anilife.app.screens.SettingsScreen
import me.enivzoy.anilife.app.screens.MyAnimeListScreen
import me.enivzoy.anilife.app.screens.NotificationsScreen
import me.enivzoy.anilife.app.data.MyAnimeListSync
import me.enivzoy.anilife.app.data.NoticeStoreInstance
import me.enivzoy.anilife.app.data.ReleaseNotifier
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.AppSidebar
import me.enivzoy.anilife.mpv.Mpv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

 



@Composable
fun AniLifeApp(settingsStore: AppSettingsStore, onPlayerFullscreen: (Boolean) -> Unit = {}) {
    val animeVideoProviders = remember { AnimeVideoProviders() }
    val repository = remember {
        AniListRepository(
            titleLanguage = settingsStore.settings.value.titleLanguage,
            videoProviders = animeVideoProviders,
        )
    }
    val mediaProviders = remember { ProviderCatalog() }
    val discordPresence = remember { DiscordPresenceManager() }
    val releaseNotifier = remember(repository) { ReleaseNotifier(repository) }
    val downloadScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val downloadQueue = remember(repository) { DownloadQueueManager(repository, downloadScope, 3) }
    val settings by settingsStore.settings.collectAsState()
    val discordState by discordPresence.state.collectAsState()
    val backStack = remember { mutableStateListOf<Screen>() }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var homeLoaded by remember { mutableStateOf(false) }
    var playerFullscreen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val presenceTarget = (screen as? Screen.Player)?.let { player ->
        val detail = player.detail
        val showTitle = detail?.title?.takeIf(String::isNotBlank)
            ?: player.title.substringBefore(" · Episode ")
        DiscordPresenceTarget(
            sessionKey = detail?.id?.toString() ?: showTitle,
            title = showTitle,
            isMovie = detail?.format.equals("MOVIE", ignoreCase = true),
            season = detail?.season,
            year = detail?.year,
            episode = player.episode,
            cover = detail?.cover,
        )
    }

    LaunchedEffect(Unit) {
        MyAnimeListSync.configure()
        MyAnimeListSync.restore()
    }

    LaunchedEffect(settings.discordPresenceEnabled, settings.discordApplicationId, presenceTarget) {
        discordPresence.update(
            enabled = settings.discordPresenceEnabled,
            configuredApplicationId = settings.discordApplicationId,
            target = presenceTarget,
        )
    }
    DisposableEffect(discordPresence) {
        onDispose { discordPresence.close() }
    }

    fun updateSettings(transform: (me.enivzoy.anilife.app.data.AppSettings) -> me.enivzoy.anilife.app.data.AppSettings) {
        settingsStore.update(transform)
        repository.setTitleLanguage(settingsStore.settings.value.titleLanguage)
    }

    fun connectDiscord() {
        val id = settings.discordApplicationId.ifBlank { DEFAULT_DISCORD_APPLICATION_ID }
        if (!settings.discordPresenceEnabled || settings.discordApplicationId != id) {
            updateSettings { it.copy(discordPresenceEnabled = true, discordApplicationId = id) }
        }
        discordPresence.update(
            enabled = true,
            configuredApplicationId = id,
            target = presenceTarget,
            reconnect = true,
        )
    }

    var picking by remember { mutableStateOf(false) }
    var pickError by remember { mutableStateOf<String?>(null) }
    val mpvLibrary = remember { runCatching { Mpv.defaultLibraryPath() }.getOrNull() }
    var showIntro by remember { mutableStateOf(settings.playIntro) }

    LaunchedEffect(screen is Screen.Player) { playerFullscreen = screen is Screen.Player }
    LaunchedEffect(playerFullscreen) { onPlayerFullscreen(playerFullscreen) }

    fun navigateTo(next: Screen) {
        if (next == screen) return
        backStack.add(screen)
        screen = next
    }

    fun goBack() {
        screen = backStack.removeLastOrNull() ?: Screen.Home
    }

    fun pickRandom() {
        if (picking) return
        picking = true
        scope.launch {
            try {
                val pick = repository.random()
                if (pick != null) {
                    navigateTo(Screen.Detail(pick.id))
                } else {
                    pickError = "AniList returned no title. Try the dice again."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                pickError = t.message?.takeIf { it.isNotBlank() } ?: "Couldn't reach AniList."
            } finally {
                picking = false
            }
        }
    }

    suspend fun resolvePlayerEpisode(target: Screen.Player, episode: Double, audio: String) {
        val anime = target.detail ?: return
        val rows = animeVideoProviders.episodes(anime.id, anime.episodes, anime.malId)
        val resolved = animeVideoProviders.sources(
            anilistId = anime.id,
            malId = anime.malId,
            titles = listOfNotNull(anime.title, anime.titleRomaji, anime.titleNative) + anime.synonyms,
            episode = episode,
            dub = audio.equals("Dub", ignoreCase = true),
        )
        val selected = resolved.firstOrNull() ?: return
        val current = screen as? Screen.Player ?: return
        if (current.detail?.id != anime.id) return
        val label = formatPlayerEpisode(episode)
        screen = current.copy(
            title = "${anime.title} · Episode $label · ${selected.provider} ${selected.quality ?: selected.format.uppercase()}",
            url = selected.url,
            headers = selected.headers,
            alternatives = resolved,
            audioTrack = audio,
            episode = episode,
            episodeTitle = rows.firstOrNull { it.number == episode }?.title,
            episodes = rows.map { it.number },
            episodeTitles = rows.mapNotNull { row -> row.title?.takeIf(String::isNotBlank)?.let { row.number to it } }.toMap(),
            episodeThumbnails = rows.mapNotNull { row -> row.thumbnail?.takeIf(String::isNotBlank)?.let { row.number to it } }.toMap(),
        )
    }

    var paletteOpen by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.isCtrlPressed || event.isMetaPressed) &&
                    event.key == Key.K
                ) {
                    paletteOpen = !paletteOpen
                    true
                } else {
                    false
                }
            },
    ) {
    Row(Modifier.fillMaxSize().background(AniLifeColors.Background)) {
        if (screen !is Screen.Player) {
            AppSidebar(
                current = screen,
                onNavigate = ::navigateTo,
                onRandom = ::pickRandom,
                onSearch = { paletteOpen = true },
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape &&
                        (screen is Screen.Detail || screen is Screen.Watch || screen is Screen.Player)
                    ) {
                        goBack()
                        true
                    } else {
                        false
                    }
                },
        ) {
            key(settings.titleLanguage) {
            Crossfade(
                targetState = screen,
                animationSpec = tween(180),
                label = "screen",
            ) { target ->
                when (target) {
                    Screen.Home -> HomeScreen(
                        repository = repository,
                        onOpen = { navigateTo(Screen.Detail(it)) },
                        onContentReady = { homeLoaded = true },
                    )
                    Screen.Browse -> BrowseScreen(repository = repository, onOpen = { navigateTo(Screen.Detail(it)) })
                    Screen.Schedule -> ScheduleScreen(repository = repository, onOpen = { navigateTo(Screen.Detail(it)) })
                    Screen.Library -> LibraryScreen(
                        repository = repository,
                        onOpen = { navigateTo(Screen.Detail(it)) },
                        onBrowse = { navigateTo(Screen.Browse) },
                    )
                    Screen.Settings -> SettingsScreen(
                        mpvLibrary = mpvLibrary,
                        settings = settings,
                        discordState = discordState,
                        onSettingsChange = ::updateSettings,
                        onDiscordConnect = ::connectDiscord,
                    )
                    Screen.Providers -> ProvidersScreen(catalog = mediaProviders)
                    Screen.Notifications -> NotificationsScreen(
                        store = NoticeStoreInstance,
                        notifier = releaseNotifier,
                        onOpenAnime = { navigateTo(Screen.Detail(it)) },
                    )
                    Screen.Profile -> MyAnimeListScreen()
                    Screen.Downloads -> DownloadsScreen(
                        repository = repository,
                        settings = settings,
                        onSettingsChange = ::updateSettings,
                        onPlay = { title, url -> navigateTo(Screen.Player(title, url)) },
                        onOpen = { id -> navigateTo(Screen.Detail(id)) },
                        queueManager = downloadQueue,
                    )
                    is Screen.Detail -> DetailScreen(
                        repository = repository,
                        id = target.id,
                        onBack = ::goBack,
                        onOpen = { navigateTo(Screen.Detail(it)) },
                        onWatch = { detail -> navigateTo(Screen.Watch(detail)) },
                    )
                    is Screen.Watch -> AnimeWatchScreen(
                        detail = target.detail,
                        repository = repository,
                        providers = animeVideoProviders,
                        initialEpisode = target.initialEpisode,
                        onBack = ::goBack,
                        onPlay = { title, url, headers, alternatives, episode, episodeTitle, episodes, episodeTitles, episodeThumbnails ->
                            navigateTo(
                                Screen.Player(
                                    title = title,
                                    url = url,
                                    headers = headers,
                                    alternatives = alternatives,
                                    audioTrack = alternatives.firstOrNull { it.url == url }?.audio ?: "Sub",
                                    detail = target.detail,
                                    episode = episode,
                                    episodeTitle = episodeTitle,
                                    episodes = episodes,
                                    episodeTitles = episodeTitles,
                                    episodeThumbnails = episodeThumbnails,
                                ),
                            )
                        },
                    )
                    is Screen.Player -> PlayerScreen(
                        title = target.title,
                        showTitle = target.detail?.title?.takeIf(String::isNotBlank) ?: target.title.substringBefore(" · Episode "),
                        url = target.url,
                        headers = target.headers,
                        alternatives = target.alternatives,
                        audioTrack = target.audioTrack,
                        episode = target.episode,
                        episodeTitle = target.episodeTitle,
                        durationMinutes = target.detail?.duration,
                        episodes = target.episodes,
                        episodeTitles = target.episodeTitles,
                        episodeThumbnails = target.episodeThumbnails,
                        detail = target.detail,
                        settings = settings,
                        onSettingsChange = { updated -> updateSettings { updated } },
                        isFullscreen = playerFullscreen,
                        onToggleFullscreen = { playerFullscreen = !playerFullscreen },
                        onSourceSelect = { source ->
                            screen = target.copy(
                                title = "${target.detail?.title ?: target.title.substringBefore(" · Episode ")} · Episode ${formatPlayerEpisode(target.episode ?: 1.0)} · ${source.provider} ${source.quality ?: source.format.uppercase()}",
                                url = source.url,
                                headers = source.headers,
                                audioTrack = source.audio,
                            )
                        },
                        onAudioChange = { audio ->
                            val activeEpisode = target.episode
                            if (activeEpisode != null) scope.launch { resolvePlayerEpisode(target, activeEpisode, audio) }
                        },
                        onEpisodeChange = { episode ->
                            if (target.detail != null) scope.launch { resolvePlayerEpisode(target, episode, target.audioTrack) }
                            else screen = target.copy(episode = episode, episodeTitle = target.episodeTitles[episode])
                        },
                        onBack = ::goBack,
                    )
                }
                }
            }
        }
    }
        if (showIntro) IntroOverlay(homeReady = homeLoaded, onFinished = { showIntro = false })
    }

    if (picking) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AniLifeColors.Scrim.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(AniLifeColors.Card)
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AniLifeColors.Foreground,
                    strokeWidth = 2.dp,
                )
                Text("Rolling the dice…", color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (paletteOpen) {
        CommandPalette(
            repository = repository,
            onOpenAnime = { navigateTo(Screen.Detail(it)) },
            onNavigate = ::navigateTo,
            onDismiss = { paletteOpen = false },
        )
    }

    pickError?.let { message ->
        AlertDialog(
            onDismissRequest = { pickError = null },
            containerColor = AniLifeColors.Card,
            titleContentColor = AniLifeColors.Foreground,
            textContentColor = AniLifeColors.MutedForeground,
            title = { Text("Random pick failed", fontWeight = FontWeight.SemiBold) },
            text = { Text(message, fontSize = 14.sp, lineHeight = 21.sp) },
            confirmButton = {
                TextButton(onClick = { pickError = null }) {
                    Text("Close", color = AniLifeColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

private fun formatPlayerEpisode(number: Double): String =
    if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

