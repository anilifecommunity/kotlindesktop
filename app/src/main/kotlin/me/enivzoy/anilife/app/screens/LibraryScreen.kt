package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.AniListRepository
import me.enivzoy.anilife.app.ui.AniLifeColors
import me.enivzoy.anilife.app.ui.AnimePosterCard
import me.enivzoy.anilife.app.ui.EmptyPanel
import me.enivzoy.anilife.app.ui.PageScrollbar

@Composable
fun LibraryScreen(
    repository: AniListRepository,
    onOpen: (Long) -> Unit,
    onBrowse: () -> Unit,
) {
    val entries by repository.libraryEntries.collectAsState()

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyPanel(
                icon = Icons.Rounded.VideoLibrary,
                title = "Your library is ready",
                message = "Save anime from its detail page and your collection will appear here, complete with posters.",
                action = {
                    Button(
                        onClick = onBrowse,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AniLifeColors.Primary,
                            contentColor = AniLifeColors.PrimaryForeground,
                        ),
                    ) {
                        Text("Explore anime", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }
    } else {
        val gridState = rememberLazyGridState()
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 30.dp, bottom = 16.dp),
            ) {
                Text("Library", color = AniLifeColors.Foreground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${entries.size} saved title${if (entries.size == 1) "" else "s"} · stored on this device",
                    color = AniLifeColors.MutedForeground,
                    fontSize = 14.sp,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    items(items = entries, key = { it.id }) { anime ->
                        AnimePosterCard(
                            anime = anime,
                            onClick = { onOpen(anime.id) },
                            modifier = Modifier.fillMaxWidth(),
                            width = null,
                        )
                    }
                }
                PageScrollbar(rememberScrollbarAdapter(gridState), Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

