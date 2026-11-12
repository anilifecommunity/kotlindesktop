package me.enivzoy.anilife.app.data

import kotlinx.coroutines.CancellationException
import me.enivzoy.anilife.core.model.AnimeCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Announces new episodes of the titles in the library.
 *
 * AniList has no push of its own for an unsigned-in viewer, so the schedule is polled and any
 * episode that has aired since the last look becomes a notice. An episode is announced once.
 */
class ReleaseNotifier(
    private val repository: AniListRepository,
    private val store: NoticeStore = NoticeStoreInstance,
) {
    private val mutableLastCheck = MutableStateFlow(0L)
    val lastCheck: StateFlow<Long> = mutableLastCheck.asStateFlow()

    suspend fun check(): Int {
        val library = repository.library.entries.value
        if (library.isEmpty()) {
            mutableLastCheck.value = System.currentTimeMillis()
            return 0
        }
        val airing = try {
            repository.recentlyAired(AIRING_LOOKBACK_HOURS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val byId = airing.associateBy { it.anime.id }
        var added = 0
        library.forEach { anime ->
            val entry = byId[anime.id] ?: return@forEach
            val episode = entry.episode
            if (store.hasAnnounced(anime.id, episode)) return@forEach
            if (entry.airingAt > System.currentTimeMillis() / 1000) return@forEach
            store.add(
                kind = "airing",
                title = anime.title,
                body = "Episode $episode is out",
                animeId = anime.id,
                image = anime.cover,
            )
            added += 1
        }
        mutableLastCheck.value = System.currentTimeMillis()
        return added
    }

    /** Announces a finished download, so the notifications page shows it like any other notice. */
    fun announceDownload(anime: AnimeCard, episodes: Int) {
        store.add(
            kind = "download",
            title = anime.title,
            body = if (episodes == 1) "Downloaded 1 episode" else "Downloaded $episodes episodes",
            animeId = anime.id,
            image = anime.cover,
        )
    }

    companion object {
        /** How far back an episode that has aired is still announced. */
        const val AIRING_LOOKBACK_HOURS = 48
    }
}
