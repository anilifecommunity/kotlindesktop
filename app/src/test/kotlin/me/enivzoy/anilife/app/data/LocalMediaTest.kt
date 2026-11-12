package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.core.model.AnimeCard
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMediaTest {
    @Test
    fun episodeNumbersCoverAniLifeFilenamePatterns() {
        assertEquals(1.0, MediaAutoDetector.episodeNumber("Show - E01.ts"))
        assertEquals(5.0, MediaAutoDetector.episodeNumber("Show S02E05.mkv"))
        assertEquals(6.0, MediaAutoDetector.episodeNumber("Show - 06.mp4"))
        assertEquals(12.5, MediaAutoDetector.episodeNumber("Show Episode 12.5.webm"))
        assertEquals(null, MediaAutoDetector.episodeNumber("opening-theme.mkv"))
    }

    @Test
    fun matchScoringUsesNativeAndSynonymTitlesIncludingSeason() {
        val folder = "Frieren Beyond Journey’s End Season 2"
        val anime = AnimeCard(
            id = 1,
            title = "Sousou no Frieren 2nd Season",
            aliases = listOf("Frieren: Beyond Journey's End 2nd Season"),
        )
        assertTrue(MediaAutoDetector.matchScore(folder, anime) >= 0.75)
    }

    @Test
    fun autoDetectParsesEpisodesAndLeavesUnmatchedFilesOut() = runBlocking {
        val root = Files.createTempDirectory("AniLife-media-scan")
        try {
            val season = Files.createDirectory(root.resolve("March comes in like a lion Season 2"))
            Files.write(season.resolve("March - S02E01.ts"), byteArrayOf(1, 2))
            Files.write(season.resolve("March - 02.mkv"), byteArrayOf(3, 4, 5))
            Files.write(season.resolve("special-feature.mkv"), byteArrayOf(6))
            Files.createDirectory(root.resolve("Empty folder"))
            val sequel = AnimeCard(id = 100, title = "March comes in like a lion Season 2", year = 2018)
            val original = AnimeCard(id = 101, title = "March comes in like a lion", year = 2016)

            val result = MediaAutoDetector.scan(root, "sub", { listOf(original, sequel) })

            assertEquals(1, result.folders.size)
            assertEquals(100L, result.folders.single().matches.first().id)
            assertEquals(listOf(1.0, 2.0), result.folders.single().episodes.map { it.episode })
            assertEquals(3L, result.folders.single().episodes.last().bytes)
            assertTrue(result.skipped.any { "special-feature" in it })
            assertTrue(result.skipped.any { "Empty folder" in it })
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun portableImportMapsRelativeMediaAndRejectsTraversal() {
        val root = Files.createTempDirectory("AniLife-portable-import")
        try {
            val show = Files.createDirectory(root.resolve("Example Anime"))
            val video = Files.write(show.resolve("Example Anime - E01.mp4"), byteArrayOf(1, 2, 3))
            val json = root.resolve("AniLife-library.json")
            Files.writeString(
                json,
                """
                {
                  "AniLife_sync": 1,
                  "anime": [{"id": 42, "card": {"id": 42, "title": "Example Anime", "cover": "https://example.test/cover.jpg", "genres": ["Drama"]}, "updated_at": 10}],
                  "downloads": [
                    {"anime_id": 42, "episode": 1, "audio": "sub", "title": "Example Anime", "status": "done", "relPath": "Example Anime/Example Anime - E01.mp4"},
                    {"anime_id": 42, "episode": 2, "audio": "sub", "title": "Example Anime", "status": "done", "relPath": "../outside.mp4"},
                    {"anime_id": 42, "episode": 3, "audio": "sub", "title": "Example Anime", "status": "queued", "relPath": "Example Anime/Example Anime - E01.mp4"}
                  ]
                }
                """.trimIndent(),
            )

            val imported = PortableLibraryImporter.read(json, root)

            assertEquals(listOf(42L), imported.anime.map { it.id })
            assertEquals(1, imported.episodes.size)
            assertEquals(video.toRealPath(), imported.episodes.single().path)
            assertEquals(3L, imported.episodes.single().bytes)
            assertEquals(1, imported.missingFiles.size)
            assertTrue("unsafe relative path" in imported.missingFiles.single())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun localDownloadIndexPersistsAndRemovesEpisodes() {
        val root = Files.createTempDirectory("AniLife-download-index")
        try {
            val file = root.resolve("downloads.properties").toFile()
            val path = Files.write(root.resolve("episode.mp4"), byteArrayOf(7, 8))
            val entry = LocalEpisode(
                AnimeCard(id = 7, title = "Example", malId = 70, cover = "https://example.test/cover.jpg"),
                1.0,
                "dub",
                path,
                2,
                episodeTitle = "First Episode",
            )

            LocalDownloadsStore(file).addAll(listOf(entry))
            val reopened = LocalDownloadsStore(file)
            assertEquals(entry, reopened.entries.value.single())
            assertTrue(file.length() > 0L)
            reopened.remove(entry)
            assertTrue(LocalDownloadsStore(file).entries.value.isEmpty())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun directVideoDownloadWritesAPlayableLocalFile() = runBlocking {
        val root = Files.createTempDirectory("AniLife-direct-download")
        val payload = byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/episode.mp4") { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/episode.mp4"
            val anime = AnimeCard(id = 42, title = "Example Anime", malId = 24)
            val episode = DirectMediaDownloader.download(
                url,
                "Example Anime",
                1.0,
                "sub",
                root,
                anime = anime,
                episodeTitle = "The Beginning",
            )
            assertTrue(Files.isRegularFile(episode.path))
            assertTrue(episode.path.fileName.toString().contains("The Beginning"))
            assertEquals(payload.toList(), Files.readAllBytes(episode.path).toList())
            assertEquals(anime, episode.anime)
            assertEquals("The Beginning", episode.episodeTitle)
            assertEquals("sub", episode.audio)
            assertEquals(1.0, episode.episode)
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }
}

