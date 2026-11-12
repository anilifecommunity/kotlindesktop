package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.AniListRepository
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.AnimePosterCard
import me.enivzoy.anilife.app.ui.AnimeRow
import me.enivzoy.anilife.app.ui.Chip
import me.enivzoy.anilife.app.ui.ErrorPanel
import me.enivzoy.anilife.app.ui.LoadState
import me.enivzoy.anilife.app.ui.PageScrollbar
import me.enivzoy.anilife.app.ui.PosterSkeleton
import me.enivzoy.anilife.app.ui.RemoteImage
import me.enivzoy.anilife.app.ui.Shimmer
import me.enivzoy.anilife.app.ui.cardMeta
import me.enivzoy.anilife.app.ui.loadState
import me.enivzoy.anilife.app.ui.parseAnimeColor
import me.enivzoy.anilife.core.model.AnimeDetail
import me.enivzoy.anilife.core.model.countdown
import me.enivzoy.anilife.core.model.formatLabel
import me.enivzoy.anilife.core.model.seasonLabel
import me.enivzoy.anilife.core.model.statusLabel
import java.io.IOException

 



@Composable
fun DetailScreen(
    repository: AniListRepository,
    id: Long,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onWatch: (AnimeDetail) -> Unit,
) {
    var attempt by remember(id) { mutableIntStateOf(0) }
    var state by remember(id) { mutableStateOf<LoadState<AnimeDetail>>(LoadState.Loading) }
    val library by repository.libraryEntries.collectAsState()

    LaunchedEffect(id, attempt) {
        state = LoadState.Loading
        state = loadState { repository.detail(id) }
    }

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            LoadState.Loading -> DetailSkeleton()
            is LoadState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorPanel(current.message, onRetry = { attempt++ })
            }
            is LoadState.Ready -> DetailContent(
                detail = current.value,
                onOpen = onOpen,
                onWatch = { onWatch(current.value) },
                inLibrary = library.any { it.id == current.value.id },
                onToggleLibrary = {
                    try {
                        if (library.any { it.id == current.value.id }) {
                            repository.removeFromLibrary(current.value.id)
                        } else {
                            repository.addToLibrary(current.value.toCard())
                        }
                        null
                    } catch (error: IOException) {
                        error.message ?: "Check that AniLife can write to your app data folder."
                    }
                },
            )
        }

        BackButton(onBack, Modifier.align(Alignment.TopStart).padding(16.dp))
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xCC171717))
            .border(1.dp, AniLifeColors.Border, CircleShape)
            .clickable(onClick = onBack)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = AniLifeColors.Foreground,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    detail: AnimeDetail,
    onOpen: (Long) -> Unit,
    onWatch: () -> Unit,
    inLibrary: Boolean,
    onToggleLibrary: () -> String?,
) {
    var expanded by remember(detail.id) { mutableStateOf(false) }
    var notice by remember(detail.id) { mutableStateOf<Pair<String, String>?>(null) }
    val tint = remember(detail.color) { parseAnimeColor(detail.color) }

    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Box(Modifier.fillMaxWidth()) {
             
             
            Box(Modifier.fillMaxWidth().height(288.dp).background(tint)) {
                RemoteImage(
                    url = detail.banner,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    placeholderColor = tint,
                    tint = Color(0x4D000000),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to AniLifeColors.Background.copy(alpha = 0.35f),
                                1f to AniLifeColors.Background,
                            ),
                        ),
                )
            }

            Column(Modifier.padding(top = 128.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                    Box(
                        modifier = Modifier
                            .width(208.dp)
                            .aspectRatio(2f / 3f)
                            .shadow(24.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(tint),
                    ) {
                        RemoteImage(
                            url = detail.cover,
                            contentDescription = detail.title,
                            modifier = Modifier.fillMaxSize(),
                            placeholderColor = tint,
                        )
                    }

                    Spacer(Modifier.width(32.dp))

                    Column(
                        modifier = Modifier.weight(1f).padding(top = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = detail.title,
                            color = AniLifeColors.Foreground,
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        val romaji = detail.titleRomaji
                        if (!romaji.isNullOrBlank() && romaji != detail.title) {
                            Text(
                                text = romaji,
                                color = AniLifeColors.MutedForeground,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            detail.score?.let {
                                Chip(
                                    text = "${it.toInt()}%",
                                    container = AniLifeColors.Primary,
                                    content = AniLifeColors.PrimaryForeground,
                                )
                            }
                            listOfNotNull(
                                formatLabel(detail.format),
                                statusLabel(detail.status),
                                detail.episodes?.let { "$it eps" },
                                seasonLabel(detail.season, detail.year),
                                detail.duration?.takeIf { it > 0 }?.let { "$it min" },
                            ).forEach { text ->
                                Chip(text = text, container = AniLifeColors.Secondary, content = AniLifeColors.Foreground)
                            }
                            detail.nextAiring?.let { next ->
                                Chip(
                                    text = (if (detail.format == "MOVIE") "Out" else "Episode ${next.episode}") + " " + countdown(next.airingAt),
                                    container = Color.Transparent,
                                    content = AniLifeColors.MutedForeground,
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onWatch,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(44.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AniLifeColors.Primary,
                                    contentColor = AniLifeColors.PrimaryForeground,
                                ),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Watch now", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    onToggleLibrary()?.let { message ->
                                        notice = "Couldn't update library" to message
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(44.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AniLifeColors.Secondary,
                                    contentColor = AniLifeColors.Foreground,
                                ),
                            ) {
                                Icon(
                                    Icons.Rounded.BookmarkAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (inLibrary) "Remove from library" else "Add to library",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        if (detail.genres.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                detail.genres.forEach { genre ->
                                    Chip(text = genre, container = Color.Transparent, content = AniLifeColors.MutedForeground)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Synopsis", color = AniLifeColors.Foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        val synopsis = detail.description
                        if (synopsis.isNullOrBlank()) {
                            Text("AniList has no synopsis for this title.", color = AniLifeColors.MutedForeground, fontSize = 14.sp)
                        } else {
                            Text(
                                text = synopsis,
                                color = AniLifeColors.MutedForeground,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 9,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (synopsis.length > 460) {
                                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                                    Text(if (expanded) "Show less" else "Read more", color = AniLifeColors.Primary, fontSize = 13.sp)
                                }
                            }
                        }

                        if (detail.tags.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                detail.tags.take(14).forEach { tag ->
                                    Chip(text = tag, container = AniLifeColors.Card, content = AniLifeColors.MutedForeground)
                                }
                            }
                        }
                    }

                    DetailsCard(detail)
                }

                if (detail.characters.isNotEmpty()) {
                    Spacer(Modifier.height(34.dp))
                    Text(
                        text = "Characters",
                        color = AniLifeColors.Foreground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(
                            items = detail.characters,
                            key = { index, character -> "${character.name}-$index" },
                        ) { _, character ->
                            CharacterCard(character)
                        }
                    }
                }

                val relationCards = detail.relations.map { it.anime }.distinctBy { it.id }
                if (relationCards.isNotEmpty()) {
                    Spacer(Modifier.height(38.dp))
                    AnimeRow(title = "Related", items = relationCards, onOpen = onOpen)
                }

                val relatedIds = relationCards.map { it.id }.toSet()
                val suggestions = detail.recommendations.filter { it.id !in relatedIds && it.id != detail.id }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(38.dp))
                    AnimeRow(title = "More like this", items = suggestions, onOpen = onOpen)
                }

                Spacer(Modifier.height(56.dp))
            }
        }
        }
        PageScrollbar(rememberScrollbarAdapter(scrollState), Modifier.align(Alignment.CenterEnd))
    }

    if (notice != null) {
        AlertDialog(
            onDismissRequest = { notice = null },
            containerColor = AniLifeColors.Card,
            titleContentColor = AniLifeColors.Foreground,
            textContentColor = AniLifeColors.MutedForeground,
            title = { Text(notice!!.first, fontWeight = FontWeight.SemiBold) },
            text = { Text(notice!!.second, fontSize = 14.sp, lineHeight = 21.sp) },
            confirmButton = {
                TextButton(onClick = { notice = null }) {
                    Text("Close", color = AniLifeColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

private fun AnimeDetail.toCard() = me.enivzoy.anilife.core.model.AnimeCard(
    id = id,
    title = title,
    titleRomaji = titleRomaji,
    titleEnglish = titleEnglish,
    titleNative = titleNative,
    malId = malId,
    cover = cover,
    color = color,
    banner = banner,
    format = format,
    status = status,
    episodes = episodes,
    score = score,
    year = year,
    season = season,
    genres = genres,
    nextAiring = nextAiring,
    isAdult = isAdult,
)

@Composable
private fun DetailsCard(detail: AnimeDetail) {
    Column(
        modifier = Modifier
            .widthIn(min = 250.dp, max = 300.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AniLifeColors.Card)
            .border(1.dp, AniLifeColors.Border, RoundedCornerShape(14.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Details", color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        InfoLine("Format", formatLabel(detail.format))
        InfoLine("Episodes", detail.episodes?.toString())
        InfoLine("Status", statusLabel(detail.status))
        InfoLine("Aired", detail.startDate ?: detail.year?.toString())
        InfoLine("Studios", detail.studios.joinToString(", ").takeIf { it.isNotBlank() })
        InfoLine("Source", detail.source?.lowercase()?.replaceFirstChar { it.uppercase() })
        InfoLine("Duration", detail.duration?.let { "$it min" })
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            color = AniLifeColors.MutedForeground,
            fontSize = 12.sp,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            color = AniLifeColors.Foreground,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CharacterCard(character: AnimeDetail.Character) {
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteImage(
            url = character.image,
            contentDescription = character.name,
            modifier = Modifier.size(78.dp).clip(CircleShape),
            placeholderColor = AniLifeColors.CardHover,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = character.name,
            color = AniLifeColors.Foreground,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val voiceActor = character.voiceActor
        if (!voiceActor.isNullOrBlank()) {
            Text(
                text = voiceActor,
                color = AniLifeColors.MutedForeground,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.fillMaxSize()) {
        Shimmer(
            modifier = Modifier.fillMaxWidth().height(288.dp),
            shape = RoundedCornerShape(0.dp),
        )
        Row(Modifier.padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            PosterSkeleton(width = 208.dp)
            Column(
                modifier = Modifier.weight(1f).padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Shimmer(Modifier.fillMaxWidth(0.7f).height(30.dp), RoundedCornerShape(6.dp))
                Shimmer(Modifier.fillMaxWidth(0.4f).height(16.dp), RoundedCornerShape(4.dp))
                Shimmer(Modifier.fillMaxWidth(0.55f).height(24.dp), CircleShape)
                Shimmer(Modifier.width(220.dp).height(44.dp), RoundedCornerShape(10.dp))
            }
        }
    }
}

