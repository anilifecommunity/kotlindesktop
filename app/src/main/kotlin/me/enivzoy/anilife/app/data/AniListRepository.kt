package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.core.model.AnimeCard
import me.enivzoy.anilife.core.model.AnimeDetail
import me.enivzoy.anilife.core.model.Home
import me.enivzoy.anilife.core.model.SearchPage
import me.enivzoy.anilife.core.net.AniListClient
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

 



class AniListRepository(
    private val client: AniListClient = AniListClient(),
    titleLanguage: String = "english",
    private val videoProviders: AnimeVideoProviders = AnimeVideoProviders(),
) {
    private val homeMutex = Mutex()
    private val homeSnapshot = HomeSnapshotStore()
    val library = LocalLibraryStore()
    private val downloads = LocalDownloadsStore()

    val libraryEntries: StateFlow<List<AnimeCard>> = library.entries
    val localEpisodes: StateFlow<List<LocalEpisode>> = downloads.entries

    @Volatile
    private var titleLanguage = titleLanguage

    @Volatile
    private var homeCache: Home? = homeSnapshot.read()?.withTitlePreference(titleLanguage)

    init {
        client.titleLanguage = titleLanguage
    }

    fun setTitleLanguage(language: String) {
        if (titleLanguage == language) return
        titleLanguage = language
        client.titleLanguage = language
        homeCache = homeCache?.withTitlePreference(language)
        library.setTitleLanguage(language)
        downloads.setTitleLanguage(language)
    }

    fun setPreferEnglishTitles(preferEnglish: Boolean) = setTitleLanguage(if (preferEnglish) "english" else "romaji")

    fun cachedHome(): Home? = homeCache

    suspend fun home(force: Boolean = false): Home {
        if (!force) homeCache?.let { return it }
        return homeMutex.withLock {
            if (!force) homeCache?.let { return@withLock it }
            client.home().also {
                homeCache = it
                withContext(Dispatchers.IO) { homeSnapshot.write(it) }
            }
        }
    }

    suspend fun search(query: String, genre: String? = null, page: Int = 1): SearchPage =
        client.search(query = query, genre = genre, page = page)

    suspend fun detail(id: Long): AnimeDetail = client.detail(id)

    suspend fun schedule(): List<me.enivzoy.anilife.core.model.AiringEntry> = client.schedule()

    /** What has aired in the last [hours], used to announce new episodes of library titles. */
    suspend fun recentlyAired(hours: Int): List<me.enivzoy.anilife.core.model.AiringEntry> =
        client.schedule(days = 1, since = System.currentTimeMillis() / 1000 - hours * 3600L)

    suspend fun random(): AnimeCard? = client.random()

    suspend fun detectLocalMedia(root: java.nio.file.Path, audio: String, onProgress: (String, String) -> Unit = { _, _ -> }): AutoDetectResult {
        val result = MediaAutoDetector.scan(root, audio, { query -> search(query).results }, onProgress)
        return result.copy(
            folders = result.folders.map { folder ->
                val anime = folder.matches.firstOrNull()
                val titles = anime?.let { episodeTitles(it, folder.episodes.map(LocalEpisode::episode)) }.orEmpty()
                folder.copy(episodes = folder.episodes.map { episode ->
                    episode.copy(episodeTitle = titles[episode.episode])
                })
            },
        )
    }

    suspend fun episodeTitles(anime: AnimeCard, episodes: Collection<Double>): Map<Double, String> {
        val wholeNumbers = episodes.filter { it > 0 && it % 1.0 == 0.0 }.map(Double::toInt)
        if (wholeNumbers.isEmpty()) return emptyMap()
        val titles = mutableMapOf<Int, String>()
        // Try AniList first, then fall back to MyAnimeList for anything still missing.
        try {
            titles += client.episodeTitles(anime.id).filterKeys { it in wholeNumbers }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            System.err.println("AniList episode-title lookup failed; trying MyAnimeList: ${failure.message}")
        }
        val malId = anime.malId
        val missing = wholeNumbers.filterNot(titles::containsKey)
        if (malId != null && missing.isNotEmpty()) {
            try {
                titles += videoProviders.episodeTitles(malId, missing.max()).filterKeys { it in missing }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                System.err.println("MyAnimeList episode-title lookup failed: ${failure.message}")
            }
        }
        return titles.mapKeys { it.key.toDouble() }
    }

    fun addToLibrary(anime: AnimeCard) = library.add(anime)

    fun addToLibrary(anime: List<AnimeCard>) = library.addAll(anime)

    fun removeFromLibrary(id: Long) = library.remove(id)

    fun addLocalEpisodes(episodes: List<LocalEpisode>) = downloads.addAll(episodes)

    suspend fun downloadDirect(
        url: String,
        animeTitle: String,
        episode: Double,
        audio: String,
        downloadRoot: java.nio.file.Path? = null,
    ): LocalEpisode {
        val matchedAnime = try {
            search(animeTitle).results
                .maxByOrNull { MediaAutoDetector.matchScore(animeTitle, it) }
                ?.takeIf { MediaAutoDetector.matchScore(animeTitle, it) >= 0.55 }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            System.err.println("AniList title match for direct download failed: ${failure.message}")
            null
        }
        val episodeTitle = matchedAnime?.let { episodeTitles(it, listOf(episode))[episode] }
        return DirectMediaDownloader.download(
            url = url,
            animeTitle = matchedAnime?.title ?: animeTitle,
            episode = episode,
            audio = audio,
            downloadRoot = downloadRoot ?: DirectMediaDownloader.defaultDownloadDirectory(),
            anime = matchedAnime,
            episodeTitle = episodeTitle,
        ).also { downloads.addAll(listOf(it)) }
    }

    fun removeLocalEpisode(episode: LocalEpisode) = downloads.remove(episode)

    fun importPortableLibrary(json: java.nio.file.Path, media: java.nio.file.Path): PortableImportResult =
        PortableLibraryImporter.read(json, media).also { result ->
            addToLibrary(result.anime)
            addLocalEpisodes(result.episodes)
        }

    private fun Home.withTitlePreference(language: String) = copy(
        trending = trending.map { it.withTitlePreference(language) },
        seasonal = seasonal.map { it.withTitlePreference(language) },
        popular = popular.map { it.withTitlePreference(language) },
        topRated = topRated.map { it.withTitlePreference(language) },
        upcoming = upcoming.map { it.withTitlePreference(language) },
        newest = newest.map { it.withTitlePreference(language) },
        movies = movies.map { it.withTitlePreference(language) },
    )
}

