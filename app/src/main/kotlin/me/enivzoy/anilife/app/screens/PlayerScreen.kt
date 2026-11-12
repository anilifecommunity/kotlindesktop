package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Slider
import me.enivzoy.anilife.app.data.ContinueStoreInstance
import me.enivzoy.anilife.app.data.AnimeEpisode
import me.enivzoy.anilife.app.data.AnimeVideoSource
import me.enivzoy.anilife.app.data.AnimeSubtitleSource
import me.enivzoy.anilife.app.data.AppSettings
import me.enivzoy.anilife.app.data.MyAnimeListSync
import me.enivzoy.anilife.app.player.MpvPlaybackEngine
import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.AnimeDetail
import me.enivzoy.anilife.core.model.SkipTimes
import me.enivzoy.anilife.core.model.Window
import me.enivzoy.anilife.core.net.AniSkipClient
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

private val HiddenPlayerCursor: PointerIcon by lazy {
    runCatching {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "hidden-player-cursor"))
    }.getOrDefault(PointerIcon.Default)
}

@Composable
fun PlayerScreen(
    title: String,
    showTitle: String = title,
    url: String? = null,
    headers: Map<String, String> = emptyMap(),
    episode: Double? = null,
    episodeTitle: String? = null,
    durationMinutes: Int? = null,
    episodes: List<Double> = emptyList(),
    episodeTitles: Map<Double, String> = emptyMap(),
    episodeThumbnails: Map<Double, String> = emptyMap(),
    alternatives: List<AnimeVideoSource> = emptyList(),
    audioTrack: String = "Sub",
    detail: AnimeDetail? = null,
    settings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {},
    isFullscreen: Boolean = true,
    onToggleFullscreen: () -> Unit = {},
    onSourceSelect: (AnimeVideoSource) -> Unit = {},
    onAudioChange: (String) -> Unit = {},
    onEpisodeChange: (Double) -> Unit = {},
    onBack: () -> Unit,
) {
    val orderedEpisodes = remember(episodes) { episodes.filter { it.isFinite() }.distinct().sorted() }
    val previous = remember(orderedEpisodes, episode) { previousEpisodeNumber(orderedEpisodes, episode) }
    val next = remember(orderedEpisodes, episode) { nextEpisodeNumber(orderedEpisodes, episode) }
    val fallbackDurationSeconds = remember(durationMinutes) {
        ((durationMinutes ?: 24).coerceAtLeast(1) * 60).toDouble()
    }
    val selectedSource = remember(alternatives, url) { alternatives.firstOrNull { it.url == url } ?: alternatives.firstOrNull() }

    val aniskip = remember { AniSkipClient() }
    var skipTimes by remember(title, episode) { mutableStateOf<SkipTimes?>(null) }
    LaunchedEffect(detail?.malId, episode) {
        val malId = detail?.malId ?: return@LaunchedEffect
        val ep = episode?.toInt() ?: return@LaunchedEffect
        if (ep <= 0) return@LaunchedEffect
        val length = ((durationMinutes ?: 24).coerceAtLeast(1) * 60).toDouble()
        skipTimes = runCatching { aniskip.skipTimes(malId, ep, length) }.getOrNull()
    }

    val resumePosition = remember(detail?.id, episode, settings.resumePlayback) {
        val animeId = detail?.id ?: return@remember 0.0
        val ep = episode ?: return@remember 0.0
        if (!settings.resumePlayback) return@remember 0.0
        ContinueStoreInstance.positionFor(animeId, ep) ?: 0.0
    }
    val engine = remember(url, headers, selectedSource?.subtitles, resumePosition) {
        url?.takeIf(String::isNotBlank)?.let { stream ->
            MpvPlaybackEngine(
                url = stream,
                headers = headers,
                fallbackDurationSeconds = fallbackDurationSeconds,
                serverSubtitles = selectedSource?.subtitles.orEmpty(),
                startPositionSeconds = resumePosition,
            )
        }
    }
    LaunchedEffect(engine) {
        if (engine != null) {
            // Let the window paint once before libmpv starts decoding; the player chrome should
            // never wait on native player initialisation.
            withFrameNanos { }
            engine.start()
        }
    }
    DisposableEffect(engine) {
        onDispose { engine?.close() }
    }

    // Until a real stream is available (provider still resolving, or a preview route), keep the
    // chrome honest by advancing the timeline as if a local file were playing. The transport
    // buttons drive the same state that mpv's time-pos/pause properties feed when it is live.
    var simulatedPlaying by remember(title, episode) { mutableStateOf(true) }
    var simulatedPosition by remember(title, episode) { mutableStateOf(0.0) }
    LaunchedEffect(engine, simulatedPlaying, title, episode, fallbackDurationSeconds) {
        if (engine != null) return@LaunchedEffect
        while (simulatedPlaying) {
            delay(1_000L)
            simulatedPosition = (simulatedPosition + 1.0).coerceAtMost(fallbackDurationSeconds)
            if (simulatedPosition >= fallbackDurationSeconds) simulatedPlaying = false
        }
    }

    val positionSeconds = engine?.positionSeconds ?: simulatedPosition
    val durationSeconds = engine?.durationSeconds?.takeIf { it > 0.0 } ?: fallbackDurationSeconds
    val isPlaying = engine?.isPlaying ?: simulatedPlaying

    val animeCard = remember(detail, title, showTitle) {
        detail?.let { d ->
            AnimeCard(
                id = d.id,
                title = d.title.ifBlank { showTitle.ifBlank { title } },
                titleRomaji = d.titleRomaji,
                titleEnglish = d.titleEnglish,
                titleNative = d.titleNative,
                malId = d.malId,
                cover = d.cover,
                color = d.color,
                banner = d.banner,
                format = d.format,
                status = d.status,
                episodes = d.episodes,
                score = d.score,
                year = d.year,
                season = d.season,
                genres = d.genres,
                nextAiring = d.nextAiring,
                isAdult = d.isAdult,
            )
        }
    }
    val currentPos = rememberUpdatedState(positionSeconds)
    val currentDur = rememberUpdatedState(durationSeconds)
    val currentEp = rememberUpdatedState(episode)
    LaunchedEffect(animeCard) {
        if (animeCard == null) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            val ep = currentEp.value ?: continue
            ContinueStoreInstance.upsert(animeCard, ep, currentPos.value, currentDur.value)
        }
    }
    androidx.compose.runtime.DisposableEffect(animeCard) {
        onDispose {
            val a = animeCard ?: return@onDispose
            val ep = currentEp.value ?: return@onDispose
            ContinueStoreInstance.upsert(a, ep, currentPos.value, currentDur.value)
        }
    }

    val episodeLine = remember(episode, episodeTitle) {
        episode?.let { number ->
            val label = formatEpisodeNumber(number)
            episodeTitle?.takeIf(String::isNotBlank)?.let { "Episode $label: $it" } ?: "Episode $label"
        }
    }
    var openPanel by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember(title, episode) { mutableStateOf(true) }
    var pointerActivity by remember(title, episode) { mutableIntStateOf(0) }

    val activeSkip = remember(skipTimes, positionSeconds, durationSeconds) {
        activeSkipWindow(skipTimes, positionSeconds, durationSeconds)
    }
    val activeSkipKey = activeSkip?.let { "${it.kind}-${it.window.start}-${it.window.end}" }
    var handledSkipKeys by remember(title, episode, url) { mutableStateOf(emptySet<String>()) }
    var autoSkipCountdown by remember(title, episode, url) { mutableStateOf<Int?>(null) }
    var skipPopupSince by remember(title, episode, url) { mutableStateOf<Double?>(null) }
    LaunchedEffect(activeSkipKey) {
        if (activeSkipKey != null) skipPopupSince = positionSeconds
    }
    LaunchedEffect(activeSkip, settings.autoSkipIntro, settings.autoSkipOutro, activeSkipKey) {
        val active = activeSkip ?: return@LaunchedEffect
        val autoEnabled = if (active.kind == "outro") settings.autoSkipOutro else settings.autoSkipIntro
        if (!autoEnabled || activeSkipKey in handledSkipKeys) return@LaunchedEffect
        try {
            for (remaining in 3 downTo 1) {
                autoSkipCountdown = remaining
                kotlinx.coroutines.delay(1000)
            }
            autoSkipCountdown = null
            if (activeSkipKey != null) handledSkipKeys = handledSkipKeys + activeSkipKey
            val target = skipTarget(active, durationSeconds)
            if (engine != null) engine.seekTo(target)
            else simulatedPosition = target.coerceIn(0.0, fallbackDurationSeconds)
        } finally {
            autoSkipCountdown = null
        }
    }
    val autoSkipEnabled = activeSkip?.let { window ->
        if (window.kind == "outro") settings.autoSkipOutro else settings.autoSkipIntro
    } ?: false
    val skipOverlayVisible = activeSkip != null && activeSkipKey !in handledSkipKeys &&
        (settings.showSkipButton || autoSkipEnabled) &&
        (controlsVisible || (skipPopupSince != null && positionSeconds - skipPopupSince!! <= 10.0) || autoSkipCountdown != null)
    var playbackSpeed by remember(title, episode) { mutableFloatStateOf(1f) }
    val episodeRows = remember(episodes, episodeTitles, episodeThumbnails) {
        episodes.distinct().sorted().map { AnimeEpisode(it, episodeTitles[it], episodeThumbnails[it]) }
    }

    LaunchedEffect(engine, engine?.hasEnded, settings.autoplayNextEpisode) {
        if (engine?.hasEnded == true && settings.autoplayNextEpisode) next?.let(onEpisodeChange)
    }

    // The next episode is offered in the file's last half-minute, with a countdown that plays it
    // unless the viewer stays. Counted in played time, so a pause holds the count.
    var upNextDismissed by remember(title, episode, url) { mutableStateOf(false) }
    val upNextDue = remember(positionSeconds, durationSeconds, skipTimes, next, settings.autoplayNextEpisode, upNextDismissed) {
        next != null && settings.autoplayNextEpisode && !upNextDismissed &&
            nextEpisodeDue(positionSeconds, durationSeconds, skipTimes?.outro)
    }
    var upNextCountdown by remember(title, episode, url) { mutableStateOf<Int?>(null) }
    val playingNow = rememberUpdatedState(isPlaying)
    val nextEpisode = rememberUpdatedState(next)
    LaunchedEffect(upNextDue) {
        if (!upNextDue) {
            upNextCountdown = null
            return@LaunchedEffect
        }
        var remaining = NEXT_EPISODE_COUNTDOWN_SECONDS
        while (remaining > 0) {
            upNextCountdown = remaining
            // A pause holds the count, so the countdown counts played time.
            if (!playingNow.value) {
                kotlinx.coroutines.delay(250)
                continue
            }
            kotlinx.coroutines.delay(1000)
            remaining -= 1
        }
        upNextCountdown = null
        nextEpisode.value?.let(onEpisodeChange)
    }

    LaunchedEffect(engine, engine?.hasEnded, animeCard?.malId, episode) {
        if (engine?.hasEnded == true) {
            val malId = animeCard?.malId ?: return@LaunchedEffect
            val watched = episode?.toInt() ?: return@LaunchedEffect
            runCatching { MyAnimeListSync.syncCompletedEpisode(malId, watched) }
        }
    }

    LaunchedEffect(engine, settings.volume, engine?.isLoading) {
        if (engine?.isLoading == false) engine.setVolume(settings.volume)
    }
    LaunchedEffect(engine, settings.contentScale, engine?.isLoading) {
        if (engine?.isLoading == false) engine.setContentScale(settings.contentScale)
    }
    LaunchedEffect(engine, playbackSpeed, engine?.isLoading) {
        if (engine?.isLoading == false) engine.setSpeed(playbackSpeed.toDouble())
    }

    LaunchedEffect(pointerActivity) {
        delay(3_000L)
        controlsVisible = false
        openPanel = null
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerHoverIcon(if (controlsVisible) PointerIcon.Default else HiddenPlayerCursor)
            .pointerInput(title, episode) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val active = event.changes.any { change ->
                        change.position != change.previousPosition ||
                            change.positionChangeIgnoreConsumed() != Offset.Zero ||
                            change.pressed != change.previousPressed
                    }
                    if (active) {
                        controlsVisible = true
                        pointerActivity += 1
                    }
                }
            }
        },
    ) {
        engine?.videoFrame?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val notice = engine?.error
            ?: engine?.takeIf { it.isLoading && it.videoFrame == null }?.let { "Buffering…" }
        if (notice != null) {
            Text(
                notice,
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 260.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        val shownSkip = if (skipOverlayVisible) activeSkip else null
        if (shownSkip != null) {
            val skip = shownSkip
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 148.dp)) {
                PlayerSkipAction(
                    label = if (autoSkipCountdown != null) "${skip.label} ($autoSkipCountdown)" else skip.label,
                    onClick = {
                        if (activeSkipKey != null) handledSkipKeys = handledSkipKeys + activeSkipKey
                        autoSkipCountdown = null
                        val target = skipTarget(skip, durationSeconds)
                        if (engine != null) engine.seekTo(target)
                        else simulatedPosition = target.coerceIn(0.0, fallbackDurationSeconds)
                    },
                )
            }
        }

        if (upNextDue) {
            val nextNumber = next
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 148.dp)) {
                PlayerUpNextCard(
                    label = nextNumber?.let { "Episode ${formatEpisodeNumber(it)}" } ?: "Next episode",
                    countdown = upNextCountdown,
                    onPlay = { nextNumber?.let(onEpisodeChange) },
                    onStay = { upNextDismissed = true },
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(260)),
        ) {
        PlayerControlsScaffold(
            title = showTitle,
            subtitle = episodeLine,
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            isPlaying = isPlaying,
            canControl = engine?.error == null,
            canPrevious = previous != null,
            canNext = next != null,
            onBack = onBack,
            onPrevious = { previous?.let(onEpisodeChange) },
            onRewind = {
                if (engine != null) engine.seekRelative(-10.0)
                else simulatedPosition = (simulatedPosition - 10.0).coerceAtLeast(0.0)
            },
            onPlayPause = {
                if (engine != null) {
                    engine.togglePause()
                } else {
                    if (simulatedPosition >= fallbackDurationSeconds) simulatedPosition = 0.0
                    simulatedPlaying = !simulatedPlaying
                }
            },
            onForward = {
                if (engine != null) engine.seekRelative(10.0)
                else simulatedPosition = (simulatedPosition + 10.0).coerceAtMost(fallbackDurationSeconds)
            },
            onNext = { next?.let(onEpisodeChange) },
            onSeek = { seconds ->
                if (engine != null) engine.seekTo(seconds)
                else simulatedPosition = seconds.coerceIn(0.0, fallbackDurationSeconds)
            },
            bottomLeftActions = {
                Icon(Icons.Rounded.VolumeUp, contentDescription = "Volume", tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.padding(start = 4.dp).size(17.dp))
                Slider(
                    value = settings.volume.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(volume = it.toInt())) },
                    valueRange = 0f..100f,
                    modifier = Modifier.width(112.dp),
                )
            },
            bottomActions = { showLabels ->
                if (settings.showSkipButton && skipTimes == null) PlayerActionPill(
                    label = "Skip ${settings.skipButtonSeconds}s",
                    icon = Icons.Rounded.FastForward,
                    onClick = {
                        val delta = settings.skipButtonSeconds.toDouble()
                        if (engine != null) engine.seekRelative(delta)
                        else simulatedPosition = (simulatedPosition + delta).coerceAtMost(fallbackDurationSeconds)
                    },
                    showLabel = showLabels,
                )
                if (alternatives.isNotEmpty()) PlayerActionPill(
                    label = selectedSource?.provider ?: "Sources",
                    icon = Icons.Rounded.Dns,
                    onClick = { openPanel = "sources" },
                    showLabel = true,
                )
                if (episodeRows.isNotEmpty()) PlayerActionPill(
                    label = "Episodes",
                    icon = Icons.Rounded.VideoLibrary,
                    onClick = { openPanel = "episodes" },
                    showLabel = true,
                )
                PlayerActionPill("Subtitles", Icons.Rounded.Subtitles, { openPanel = "subtitles" }, showLabel = true)
                PlayerActionPill("Settings", Icons.Rounded.Settings, { openPanel = "settings" }, showLabel = true)
                PlayerActionPill(
                    if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                    if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    onToggleFullscreen,
                    showLabel = true,
                )
            },
        )
        PlayerPanelLayer(
            panel = openPanel,
            onDismiss = { openPanel = null },
            sources = alternatives,
            audio = audioTrack,
            selectedSourceUrl = url,
            subtitles = engine?.subtitleTracks.orEmpty(),
            subtitleSources = selectedSource?.subtitles.orEmpty(),
            episodes = episodeRows,
            currentEpisode = episode,
            posterUrl = detail?.cover,
            volume = settings.volume.toFloat(),
            onVolumeChange = { onSettingsChange(settings.copy(volume = it.toInt())) },
            speed = playbackSpeed,
            onSpeedChange = { playbackSpeed = it },
            screenSize = settings.contentScale,
            onScreenSizeChange = { onSettingsChange(settings.copy(contentScale = it)) },
            autoplayNext = settings.autoplayNextEpisode,
            onAutoplayNextChange = { onSettingsChange(settings.copy(autoplayNextEpisode = it)) },
            onAudioChange = onAudioChange,
            onSourceSelect = { source -> openPanel = null; onSourceSelect(source) },
            onEpisodeSelect = { selectedEpisode -> openPanel = null; onEpisodeChange(selectedEpisode) },
            onSubtitleSelect = { track, source ->
                if (source != null) engine?.addSubtitle(source) else engine?.selectSubtitleTrack(track)
                openPanel = null
            },
        )
        }
    }
}

/** The episode before [current], or null at the start of the list. */
internal fun previousEpisodeNumber(episodes: List<Double>, current: Double?): Double? {
    if (current == null || !current.isFinite()) return null
    return episodes.asSequence()
        .filter { it.isFinite() && it < current }
        .maxOrNull()
}

/** The episode after [current], or null at the end of the list. */
internal fun nextEpisodeNumber(episodes: List<Double>, current: Double?): Double? {
    if (current == null || !current.isFinite()) return null
    return episodes.asSequence()
        .filter { it.isFinite() && it > current }
        .minOrNull()
}

internal fun formatEpisodeNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

internal fun estimateEpisodeEndTime(now: LocalTime, durationMinutes: Int): String? =
    durationMinutes.takeIf { it > 0 }
        ?.let { now.plusMinutes(it.toLong()).format(DateTimeFormatter.ofPattern("H:mm")) }

internal data class PlayerSkipWindow(
    val kind: String,
    val label: String,
    val window: Window,
)

internal fun activeSkipWindow(skip: SkipTimes?, position: Double, duration: Double): PlayerSkipWindow? {
    skip?.recap?.let { window ->
        if (window.isValid && position >= window.start && position < window.end) {
            return PlayerSkipWindow("recap", "Skip Recap", window)
        }
    }
    skip?.intro?.let { window ->
        if (window.isValid && position >= window.start && position < window.end) {
            return PlayerSkipWindow("intro", "Skip Intro", window)
        }
    }
    skip?.outro?.let { window ->
        if (window.isValid && position >= window.start && position < window.end && outroIsCredits(window, duration)) {
            return PlayerSkipWindow("outro", "Skip Outro", window)
        }
    }
    return null
}

internal fun outroIsCredits(window: Window, duration: Double): Boolean =
    duration > 0.0 && window.end >= duration * 0.75

internal fun skipTarget(window: PlayerSkipWindow, duration: Double): Double =
    if (window.kind == "outro" && duration > 0.0) minOf(window.window.end, duration) else window.window.end

/** The stretch of a file in which the next episode is offered. */
internal const val NEXT_EPISODE_TAIL_SECONDS = 30

/** Offered here even over credits that haven't finished. */
internal const val NEXT_EPISODE_FINAL_SECONDS = 5

/** How long the up-next card counts before it plays the next episode itself. */
internal const val NEXT_EPISODE_COUNTDOWN_SECONDS = 8

/**
 * Whether the next episode should be offered now: only in the file's last half-minute, and while
 * highlighted credits still roll, not until they finish (or five seconds are all that's left), so
 * a preview or post-credits scene after them still plays.
 */
internal fun nextEpisodeDue(position: Double, duration: Double, outro: Window?): Boolean {
    if (duration <= 0.0 || position < 0.0) return false
    val remaining = duration - position
    if (remaining < 0.0 || remaining > NEXT_EPISODE_TAIL_SECONDS) return false
    if (outro == null || !outroIsCredits(outro, duration)) return true
    return !(position < outro.end && remaining > NEXT_EPISODE_FINAL_SECONDS)
}

@Composable
internal fun PlayerSkipAction(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * The next episode, offered in the file's last half-minute with a countdown that plays it unless
 * the viewer stays.
 */
@Composable
internal fun PlayerUpNextCard(
    label: String,
    countdown: Int?,
    onPlay: () -> Unit,
    onStay: () -> Unit,
) {
    Column(
        Modifier
            .width(320.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.86f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Up next", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.24f))
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    if (countdown != null) "Play now · ${countdown}s" else "Play now",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onStay)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Stay", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

