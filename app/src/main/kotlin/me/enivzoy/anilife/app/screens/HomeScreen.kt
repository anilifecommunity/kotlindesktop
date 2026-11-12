package me.enivzoy.anilife.app.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.enivzoy.anilife.app.data.AniListRepository
import me.enivzoy.anilife.app.data.ContinueStoreInstance
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.AnimePosterCard
import me.enivzoy.anilife.app.ui.AnimeRow
import me.enivzoy.anilife.app.ui.ContinueCard
import me.enivzoy.anilife.app.ui.ErrorPanel
import me.enivzoy.anilife.app.ui.Hero
import me.enivzoy.anilife.app.ui.LoadState
import me.enivzoy.anilife.app.ui.PageScrollbar
import me.enivzoy.anilife.app.ui.PosterSkeleton
import me.enivzoy.anilife.app.ui.Shimmer
import me.enivzoy.anilife.app.ui.loadState
import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.AnimeDetail
import me.enivzoy.anilife.core.model.Home

@Composable
fun HomeScreen(
    repository: AniListRepository,
    onOpen: (Long) -> Unit,
    onContentReady: () -> Unit = {},
) {
    var attempt by remember { mutableStateOf(0) }
    var state by remember(repository) {
        mutableStateOf<LoadState<Home>>(repository.cachedHome()?.let { LoadState.Ready(it) } ?: LoadState.Loading)
    }

    LaunchedEffect(attempt) {
        val cached = repository.cachedHome()
        if (cached == null || attempt > 0) state = LoadState.Loading
        when (val result = loadState { repository.home(force = cached != null || attempt > 0) }) {
            is LoadState.Ready -> state = result
            is LoadState.Failed -> if (cached == null || attempt > 0) state = result
            LoadState.Loading -> Unit
        }
    }

    LaunchedEffect(state) {
        if (state is LoadState.Ready || state is LoadState.Failed) onContentReady()
    }

    when (val current = state) {
        LoadState.Loading -> HomeSkeleton()
        is LoadState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorPanel(message = current.message, onRetry = { attempt++ })
        }
        is LoadState.Ready -> HomeContent(current.value, repository, onOpen)
    }
}

@Composable
private fun HomeContent(home: Home, repository: AniListRepository, onOpen: (Long) -> Unit) {
    val continueEntries by ContinueStoreInstance.entries.collectAsState()
    val libraryEntries by repository.library.entries.collectAsState()
    val featured = remember(home) {
        home.trending.filter { !it.banner.isNullOrBlank() }
            .ifEmpty { home.trending }
            .take(6)
    }
    val spotlightItems = remember(home) { home.trending.take(12).ifEmpty { home.newest.take(12) } }
    val rows = remember(home) {
        listOf(
            "New episodes" to home.newest,
            "This season" to home.seasonal,
            "All-time popular" to home.popular,
            "Top rated" to home.topRated,
            "Movies" to home.movies,
            "Coming soon" to home.upcoming,
        ).filter { it.second.isNotEmpty() }
    }

    var heroAnime by remember { mutableStateOf<AnimeCard?>(spotlightItems.firstOrNull() ?: featured.firstOrNull()) }
    val spotlightState = rememberLazyListState()
    LaunchedEffect(spotlightState, spotlightItems) {
        snapshotFlow { spotlightState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val item = spotlightItems.getOrNull(index)
                if (item != null) heroAnime = item
            }
    }

    var heroDetail by remember { mutableStateOf<AnimeDetail?>(null) }
    LaunchedEffect(heroAnime) {
        val anime = heroAnime ?: return@LaunchedEffect
        heroDetail = runCatching { repository.detail(anime.id) }.getOrNull()
    }

    var becauseSeed by remember { mutableStateOf<AnimeCard?>(null) }
    var becauseItems by remember { mutableStateOf<List<AnimeCard>>(emptyList()) }
    LaunchedEffect(libraryEntries) {
        val seed = libraryEntries.firstOrNull()
        becauseSeed = seed
        becauseItems = if (seed != null) {
            runCatching { repository.detail(seed.id).recommendations.take(12) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "home-hero") {
                Hero(
                    items = heroAnime?.let { listOf(it) } ?: featured.take(1),
                    loading = false,
                    onOpen = onOpen,
                    synopsis = heroDetail?.description?.replace(Regex("<[^>]*>"), "")?.trim(),
                )
            }
            item(key = "hero-gap") { Spacer(Modifier.height(16.dp)) }

            if (continueEntries.isNotEmpty()) {
                item(key = "continue") {
                    AnimeRow(
                        title = "Continue watching",
                        subtitle = "Right-click a title to move it on your list",
                        items = emptyList(),
                        onOpen = onOpen,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        items(continueEntries, key = { it.anime.id }) { entry ->
                            ContinueCard(
                                entry = entry,
                                onResume = { e -> onOpen(e.anime.id) },
                                onDetails = onOpen,
                                onRemove = { id -> ContinueStoreInstance.remove(id) },
                            )
                        }
                    }
                    Spacer(Modifier.height(38.dp))
                }
            }

            if (libraryEntries.isNotEmpty()) {
                item(key = "library") {
                    AnimeRow(title = "My library", items = libraryEntries, onOpen = onOpen)
                    Spacer(Modifier.height(38.dp))
                }
            }

            if (becauseSeed != null && becauseItems.isNotEmpty()) {
                item(key = "because") {
                    AnimeRow(title = "Because you watched ${becauseSeed!!.title}", items = becauseItems, onOpen = onOpen)
                    Spacer(Modifier.height(38.dp))
                }
            }

            item(key = "spotlight") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = null,
                            tint = AniLifeColors.MutedForeground.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Trending now",
                            color = AniLifeColors.Foreground,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    LazyRow(
                        state = spotlightState,
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(spotlightItems, key = { it.id }) { anime ->
                            AnimePosterCard(anime = anime, onClick = { onOpen(anime.id); heroAnime = anime })
                        }
                    }
                }
                Spacer(Modifier.height(38.dp))
            }

            items(items = rows, key = { it.first }) { (title, cards) ->
                AnimeRow(title = title, items = cards, onOpen = onOpen)
                Spacer(Modifier.height(38.dp))
            }
            item(key = "footer") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    OutlinedButton(onClick = { }) {
                        androidx.compose.material3.Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Arrange Home", fontSize = 13.sp)
                    }
                }
            }
        }
        PageScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun HomeSkeleton() {
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item {
                Shimmer(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    shape = RoundedCornerShape(0.dp),
                )
            }
            item { Spacer(Modifier.height(36.dp)) }
            items(3) { SkeletonShelf() }
        }
        PageScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun SkeletonShelf() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Shimmer(
                modifier = Modifier.width(150.dp).height(18.dp),
                shape = RoundedCornerShape(4.dp),
            )
            Spacer(Modifier.weight(1f))
            Shimmer(Modifier.width(60.dp).height(22.dp), CircleShape)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(8) { PosterSkeleton() }
        }
        Spacer(Modifier.height(26.dp))
    }
}
