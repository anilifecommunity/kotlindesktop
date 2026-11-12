package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.AnimeEpisode
import me.enivzoy.anilife.app.data.AnimeVideoProviders
import me.enivzoy.anilife.app.data.AnimeVideoSource
import me.enivzoy.anilife.app.data.AniListRepository
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.RemoteImage
import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.AnimeDetail
import kotlinx.coroutines.CancellationException

@Composable
fun AnimeWatchScreen(
    detail: AnimeDetail,
    repository: AniListRepository,
    providers: AnimeVideoProviders,
    initialEpisode: Double? = null,
    onBack: () -> Unit,
    onPlay: (String, String, Map<String, String>, List<AnimeVideoSource>, Double, String?, List<Double>, Map<Double, String>, Map<Double, String>) -> Unit,
) {
    val anilistId = detail.id
    val title = detail.title
    var episodes by remember(anilistId) { mutableStateOf<List<AnimeEpisode>>(emptyList()) }
    var loading by remember(anilistId) { mutableStateOf(true) }
    var error by remember(anilistId) { mutableStateOf<String?>(null) }
    var resolving by remember(anilistId) { mutableStateOf<Double?>(null) }
    var selectedEpisode by remember(anilistId) { mutableStateOf(initialEpisode) }
    var sources by remember(anilistId) { mutableStateOf<List<AnimeVideoSource>>(emptyList()) }
    var resolveAttempt by remember(anilistId) { mutableStateOf(0) }
    var dub by remember(anilistId) { mutableStateOf(false) }

    LaunchedEffect(anilistId) {
        loading = true
        error = null
        try {
            episodes = providers.episodes(anilistId, detail.episodes, detail.malId)
            val missingTitles = episodes.filter { it.title.isNullOrBlank() }.map { it.number }
            if (missingTitles.isNotEmpty()) {
                val metadataTitles = repository.episodeTitles(
                    AnimeCard(
                        id = detail.id,
                        title = detail.title,
                        titleRomaji = detail.titleRomaji,
                        titleEnglish = detail.titleEnglish,
                        titleNative = detail.titleNative,
                        malId = detail.malId,
                    ),
                    missingTitles,
                )
                episodes = episodes.map { episode ->
                    episode.copy(title = episode.title?.takeIf(String::isNotBlank) ?: metadataTitles[episode.number])
                }
            }
            if (episodes.isEmpty()) error = "No provider episodes were found for this title."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            error = failure.message ?: "Couldn't load video providers."
        } finally {
            loading = false
        }
    }

    val activeEpisode = selectedEpisode
    LaunchedEffect(anilistId, activeEpisode, resolveAttempt, episodes.isNotEmpty()) {
        if (activeEpisode == null || episodes.isEmpty()) return@LaunchedEffect
        try {
            val resolved = providers.sources(
                anilistId = anilistId,
                malId = detail.malId,
                titles = listOfNotNull(detail.title, detail.titleRomaji, detail.titleNative) + detail.synonyms,
                episode = activeEpisode,
                dub = dub,
            )
            sources = resolved
            if (resolved.isEmpty()) {
                error = "No ${if (dub) "dub" else "sub"} streams were returned. Try another episode or audio track."
            } else {
                error = null
                val best = resolved.first()
                val ep = episodeNumber(activeEpisode)
                onPlay(
                    "$title · Episode $ep · ${best.provider} ${best.quality ?: best.format.uppercase()}",
                    best.url,
                    best.headers,
                    resolved,
                    activeEpisode,
                    episodes.firstOrNull { it.number == activeEpisode }?.title?.takeIf(String::isNotBlank),
                    episodes.map(AnimeEpisode::number),
                    episodes.mapNotNull { ep -> ep.title?.takeIf(String::isNotBlank)?.let { ep.number to it } }.toMap(),
                    episodes.mapNotNull { ep -> ep.thumbnail?.takeIf(String::isNotBlank)?.let { ep.number to it } }.toMap(),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            sources = emptyList()
            error = failure.message ?: "Couldn't resolve episode streams."
        } finally {
            resolving = null
        }
    }

    Column(
        Modifier.fillMaxSize().background(AniLifeColors.Background).padding(horizontal = 26.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WatchHero(detail = detail, onBack = onBack, episodeCount = episodes.size)

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.weight(0.56f).fillMaxSize().clip(RoundedCornerShape(18.dp))
                    .background(AniLifeColors.Card).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("EPISODES", color = AniLifeColors.MutedForeground, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Choose where to start", color = AniLifeColors.Foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (episodes.isNotEmpty()) MetaPill("${episodes.size} episodes")
                }
                Spacer(Modifier.height(12.dp))
                when {
                    loading -> LoadingState("Finding episodes")
                    episodes.isEmpty() -> EmptyState(error ?: "No episodes are available.")
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(episodes, key = { it.number }) { episode ->
                            EpisodeRow(
                                episode = episode,
                                selected = selectedEpisode == episode.number,
                                loading = resolving == episode.number,
                                onClick = {
                                    selectedEpisode = episode.number
                                    sources = emptyList()
                                    error = null
                                    resolving = episode.number
                                    resolveAttempt++
                                },
                            )
                        }
                    }
                }
            }

            Column(
                Modifier.weight(0.44f).fillMaxSize().clip(RoundedCornerShape(18.dp))
                    .background(AniLifeColors.Card).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NOW PLAYING", color = AniLifeColors.MutedForeground, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(
                            activeEpisode?.let { "Episode ${episodeNumber(it)}" } ?: "Select an episode",
                            color = AniLifeColors.Foreground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color.Black.copy(alpha = 0.28f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AudioChoice("Sub", selected = !dub, modifier = Modifier.weight(1f)) {
                        if (dub) {
                            dub = false
                            refreshSources(selectedEpisode) { sources = emptyList(); error = null; resolving = selectedEpisode; resolveAttempt++ }
                        }
                    }
                    AudioChoice("Dub", selected = dub, modifier = Modifier.weight(1f)) {
                        if (!dub) {
                            dub = true
                            refreshSources(selectedEpisode) { sources = emptyList(); error = null; resolving = selectedEpisode; resolveAttempt++ }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("SERVER", color = AniLifeColors.MutedForeground, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(7.dp))
                when {
                    activeEpisode == null -> EmptyState("Pick an episode to see available servers.")
                    resolving == activeEpisode && sources.isEmpty() -> LoadingState("Checking servers")
                    sources.isEmpty() -> EmptyState(error ?: "No servers are available for this episode.")
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sources, key = { "${it.provider}:${it.audio}:${it.server}:${it.url}" }) { source ->
                            ServerRow(source = source) {
                                val ep = episodeNumber(activeEpisode)
                                onPlay(
                                    "$title · Episode $ep · ${source.provider} ${source.quality ?: source.format.uppercase()}",
                                    source.url,
                                    source.headers,
                                    sources,
                                    activeEpisode,
                                    episodes.firstOrNull { it.number == activeEpisode }?.title?.takeIf(String::isNotBlank),
                                    episodes.map(AnimeEpisode::number),
                                    episodes.mapNotNull { ep -> ep.title?.takeIf(String::isNotBlank)?.let { ep.number to it } }.toMap(),
                                    episodes.mapNotNull { ep -> ep.thumbnail?.takeIf(String::isNotBlank)?.let { ep.number to it } }.toMap(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchHero(detail: AnimeDetail, onBack: () -> Unit, episodeCount: Int) {
    Box(Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(20.dp)).background(AniLifeColors.Card)) {
        RemoteImage(
            url = detail.banner ?: detail.cover,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            placeholderColor = AniLifeColors.Card,
            tint = Color.Black.copy(alpha = 0.38f),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent, Color.Black.copy(alpha = 0.18f))),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, AniLifeColors.Background.copy(alpha = 0.85f)))))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", modifier = Modifier.size(17.dp))
            Text("Back to anime", modifier = Modifier.padding(start = 6.dp), fontSize = 12.sp)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 22.dp, vertical = 18.dp)) {
            Text("WATCH ANIME", color = Color.White.copy(alpha = 0.66f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
            Text(detail.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                detail.format?.let { MetaPill(it.replace('_', ' ')) }
                if (detail.year != null) MetaPill(detail.year.toString())
                if (episodeCount > 0) MetaPill("$episodeCount episodes")
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: AnimeEpisode, selected: Boolean, loading: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.035f))
            .border(if (selected) 1.dp else 1.dp, if (selected) Color.White.copy(alpha = 0.45f) else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) Color.White else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(episodeNumber(episode.number), color = if (selected) AniLifeColors.Background else Color.White.copy(alpha = 0.82f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(episode.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(episode.title?.takeIf(String::isNotBlank) ?: "Episode ${episodeNumber(episode.number)}", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            loading -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            selected -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(19.dp))
            else -> Icon(Icons.Rounded.PlayArrow, contentDescription = "Select episode", tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun ServerRow(source: AnimeVideoSource, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(13.dp)).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(source.provider, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(source.server.takeIf(String::isNotBlank), source.quality, source.format.uppercase(), source.audio).joinToString(" · "),
                color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Button(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
            Text("Play", modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AudioChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) AniLifeColors.Background else Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetaPill(text: String) {
    Text(
        text,
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 9.dp, vertical = 4.dp),
        color = Color.White.copy(alpha = 0.84f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LoadingState(label: String) {
    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        Text(label, color = AniLifeColors.MutedForeground, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun EmptyState(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 18.dp), color = AniLifeColors.MutedForeground, fontSize = 12.sp)
}

private fun episodeNumber(number: Double): String = if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

private inline fun refreshSources(episode: Double?, update: () -> Unit) {
    if (episode != null) update()
}

