package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.Home
import me.enivzoy.anilife.core.model.NextAiring
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeSnapshotStoreTest {
    @Test
    fun homepageSnapshotRoundTripsAndIgnoresInvalidCache() {
        val root = Files.createTempDirectory("AniLife-home")
        try {
            val file = root.resolve("home.json").toFile()
            val store = HomeSnapshotStore(file)
            val card = AnimeCard(
                id = 42,
                title = "Example",
                titleRomaji = "Example Romaji",
                titleEnglish = "Example English",
                cover = "https://example.test/cover.jpg",
                genres = listOf("Action", "Drama"),
                nextAiring = NextAiring(4, 1_800_000_000),
                aliases = listOf("Alias"),
            )
            val home = Home(trending = listOf(card), newest = listOf(card.copy(title = "Newest")))

            store.write(home)

            assertEquals(home, store.read())
            file.writeText("{invalid")
            assertNull(store.read())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}

