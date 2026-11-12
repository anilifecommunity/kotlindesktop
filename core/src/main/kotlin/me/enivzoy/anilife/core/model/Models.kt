package me.enivzoy.anilife.core.model

import me.enivzoy.anilife.core.error.ErrorEntry

data class NextAiring(val episode: Int, val airingAt: Long)

data class AnimeCard(
    val id: Long,
    val title: String,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val malId: Long? = null,
    val cover: String? = null,
    val color: String? = null,
    val banner: String? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val score: Double? = null,
    val year: Int? = null,
    val season: String? = null,
    val genres: List<String> = emptyList(),
    val nextAiring: NextAiring? = null,
    val isAdult: Boolean = false,
    val aliases: List<String> = emptyList(),
)

data class AnimeDetail(
    val id: Long,
    val malId: Long? = null,
    val titleNative: String? = null,
    val synonyms: List<String> = emptyList(),
    val description: String? = null,
    val duration: Int? = null,
    val source: String? = null,
    val studios: List<String> = emptyList(),
    val startDate: String? = null,
    val endDate: String? = null,
    val trailer: Trailer? = null,
    val relations: List<Relation> = emptyList(),
    val recommendations: List<AnimeCard> = emptyList(),
    val characters: List<Character> = emptyList(),
    val tags: List<String> = emptyList(),
    val logo: String? = null,
     
    val title: String = "",
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val cover: String? = null,
    val color: String? = null,
    val banner: String? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val score: Double? = null,
    val year: Int? = null,
    val season: String? = null,
    val genres: List<String> = emptyList(),
    val nextAiring: NextAiring? = null,
    val isAdult: Boolean = false,
) {
    data class Trailer(val site: String, val id: String)
    data class Relation(val kind: String, val anime: AnimeCard)
    data class Character(val name: String, val image: String?, val role: String?, val voiceActor: String?)
}

data class WatchOrderEntry(
    val anime: AnimeCard,
    val shelf: String,
    val position: Int,
    val chronological: Int,
    val seasonNumber: Int?,
    val relation: String?,
    val isCurrent: Boolean,
)

data class Home(
    val trending: List<AnimeCard> = emptyList(),
    val seasonal: List<AnimeCard> = emptyList(),
    val popular: List<AnimeCard> = emptyList(),
    val topRated: List<AnimeCard> = emptyList(),
    val upcoming: List<AnimeCard> = emptyList(),
    val newest: List<AnimeCard> = emptyList(),
    val movies: List<AnimeCard> = emptyList(),
)

data class HomeRowPage(val cards: List<AnimeCard> = emptyList(), val hasMore: Boolean = false)

data class SearchFilters(
    val query: String? = null,
    val genres: List<String>? = null,
    val year: Int? = null,
    val season: String? = null,
    val format: String? = null,
    val status: String? = null,
    val sort: String? = null,
    val page: Int? = null,
    val tags: List<String>? = null,
    val excludedGenres: List<String>? = null,
    val excludedTags: List<String>? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val country: String? = null,
    val source: String? = null,
    val episodesMin: Int? = null,
    val episodesMax: Int? = null,
    val minimumScore: Double? = null,
    val dubbedOnly: Boolean = false,
    val hideWatched: String? = null,
)

data class Tag(val name: String, val description: String? = null, val category: String? = null, val isAdult: Boolean = false)

data class SearchCorrection(val typed: String, val suggested: String, val applied: Boolean = false)

data class SearchPage(val results: List<AnimeCard> = emptyList(), val hasNext: Boolean = false, val correction: SearchCorrection? = null)

data class AiringEntry(val airingAt: Long, val episode: Int, val anime: AnimeCard)

/** A stretch of an episode in seconds, used for skip markers. */
data class Window(val start: Double, val end: Double) {
    val isValid: Boolean get() = start.isFinite() && end.isFinite() && end > start
}

data class SkipTimes(val intro: Window? = null, val outro: Window? = null, val recap: Window? = null)

data class Episode(
    val number: Int,
    val title: String? = null,
    val synopsis: String? = null,
    val thumbnail: String? = null,
    val airedOn: String? = null,
    val durationSeconds: Double? = null,
    val filler: Boolean = false,
    val skip: SkipTimes? = null,
    val airsAt: Long? = null,
)

data class EpisodeList(
    val episodes: List<Episode> = emptyList(),
    val subCount: Int? = null,
    val dubCount: Int? = null,
)

enum class Audio { SUB, DUB }

data class Stream(val url: String, val format: String, val quality: String? = null, val height: Int? = null)

data class Subtitle(val url: String, val label: String, val language: String)

data class Server(
    val id: String,
    val provider: String,
    val label: String,
    val name: String,
    val track: String,
    val audio: Audio,
    val headers: Map<String, String> = emptyMap(),
    val streams: List<Stream> = emptyList(),
    val embed: String? = null,
    val subtitles: List<Subtitle> = emptyList(),
    val thumbnails: String? = null,
)

data class StoryboardFrame(val start: Long, val end: Long, val image: String, val rect: Rect? = null) {
    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)
}

data class Sources(val servers: List<Server> = emptyList(), val skip: SkipTimes? = null)

data class WatchInfo(
    val server: Server,
    val servers: List<Server>,
    val skip: SkipTimes?,
    val start: Long,
    val proxied: Boolean = false,
)

class LibraryStatus {
    companion object {
        const val WATCHING = "watching"
        const val PLANNING = "planning"
        const val COMPLETED = "completed"
        const val PAUSED = "paused"
        const val DROPPED = "dropped"
    }
}

data class LibraryEntry(
    val anime: AnimeCard,
    val status: String,
    val updatedAt: Long,
    val synced: Boolean = true,
    val progress: Int? = null,
    val score: Double? = null,
    val accountStatus: String? = null,
)

data class Progress(val animeId: Long, val episode: Int, val position: Long, val duration: Long, val updatedAt: Long)

data class ContinueEntry(val anime: AnimeCard, val progress: Progress)

class PlayerContentScale {
    companion object {
        const val FIT = "fit"
        const val CROP = "crop"
        const val FILL = "fill"
    }
}

data class Settings(
    val audio: Audio = Audio.SUB,
    val subtitleLanguages: List<String> = emptyList(),
    val autoplayNext: Boolean = true,
    val autoSkipIntro: Boolean = true,
    val autoSkipOutro: Boolean = true,
    val resume: Boolean = true,
    val showAdult: Boolean = false,
    val preferredProvider: String? = null,
    val preferredSourceLanguages: List<String> = emptyList(),
    val dohProvider: String = "auto",
    val serverPriority: List<String> = emptyList(),
    val disabledProviders: List<String> = emptyList(),
    val volume: Int = 100,
    val syncAnilist: Boolean = true,
    val hardwareDecoding: Boolean = true,
    val discordPresence: Boolean = false,
    val downloadDir: String? = null,
    val downloadQuality: String = "best",
    val downloadHardSubs: Boolean = false,
    val showSkipButton: Boolean = false,
    val skipButtonSeconds: Int = 10,
    val playbackBufferSize: String = "auto",
    val videoCacheSize: String = "auto",
    val releaseNotifications: Boolean = true,
    val downloadNotifications: Boolean = true,
    val subtitleScale: Int = 100,
    val subtitlesByDefault: Boolean = false,
    val subtitlesWithDub: Boolean = false,
    val captions: Captions = Captions(),
    val upscaler: String = "off",
    val pauseDetailsOverlay: Boolean = false,
    val playerClock: Boolean = false,
    val playerContentScale: String = PlayerContentScale.FIT,
    val checkUpdatesOnLaunch: Boolean = true,
    val appearance: Appearance = Appearance(),
)

data class Captions(
    val font: String = "sans",
    val bold: Boolean = false,
    val textColor: String = "white",
    val textOpacity: Int = 100,
    val backgroundColor: String = "black",
    val backgroundOpacity: Int = 80,
    val edgeStyle: String = "none",
    val position: String = "bottom",
    val edgeMargin: Int = 10,
    val horizontalAnchor: Int = 50,
)

data class Appearance(
    val background: String = "0",
    val accent: String = "0",
    val customSeed: String = "0",
    val style: String = "0",
    val contrast: String = "0",
    val playerAccent: Boolean = false,
    val showColourPages: Boolean = false,
    val backdropSource: String = "0",
    val backdropAnimeUrl: String? = null,
    val backdropAnimeTitle: String? = null,
    val backdropImage: String? = null,
    val backdropDim: String = "0",
    val backdropBlur: Boolean = false,
    val uiScale: String = "1",
    val clockFormat: String = "24h",
    val hiddenPosterElements: List<String> = emptyList(),
    val blurEpisodeImages: Boolean = false,
    val showSeriesLogos: Boolean = false,
    val glassNavigation: Boolean = false,
    val glassHome: Boolean = false,
    val glassDetails: Boolean = false,
    val glassBrowse: Boolean = false,
    val glassPopups: Boolean = false,
    val glassRefresh: Boolean = false,
    val glassPages: Boolean = false,
    val homeLayout: String? = null,
)

data class DownloadRecord(
    val id: Long,
    val animeId: Long,
    val episode: Int,
    val audio: Audio,
    val title: String,
    val cover: String? = null,
    val status: String = "queued",
    val progress: Double = 0.0,
    val bytes: Long = 0L,
    val path: String? = null,
    val error: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val provider: String? = null,
    val maxHeight: Int? = null,
    val matchQuality: Boolean = false,
    val hardSubs: Boolean = false,
    val height: Int? = null,
    val server: String? = null,
    val speed: Double? = null,
    val eta: Long? = null,
)

data class DownloadOptions(
    val provider: String? = null,
    val maxHeight: Int? = null,
    val matchQuality: Boolean = false,
    val hardSubs: Boolean = false,
)

data class QueueState(
    val paused: Boolean = false,
    val pending: Int = 0,
    val folder: String? = null,
    val freeBytes: Long? = null,
    val headroomBytes: Long? = null,
)

data class QualityCheck(
    val server: String,
    val provider: String,
    val offered: List<Int> = emptyList(),
    val delivered: Int? = null,
    val fits: Boolean? = null,
    val alternative: Alternative? = null,
) {
    data class Alternative(val server: String, val provider: String, val height: Int)
}

data class FeedItem(
    val id: Long,
    val kind: String,
    val createdAt: Long,
    val title: String,
    val badge: String? = null,
    val detail: String? = null,
    val episode: Int? = null,
    val mediaId: Long? = null,
    val image: String? = null,
    val banner: String? = null,
    val unread: Boolean = false,
)

data class Notice(
    val id: Long,
    val kind: String,
    val title: String,
    val body: String? = null,
    val route: String? = null,
    val image: String? = null,
    val animeId: Long? = null,
    val createdAt: Long = 0L,
    val read: Boolean = false,
)

data class NotificationsPage(
    val feed: List<FeedItem> = emptyList(),
    val feedError: ErrorEntry? = null,
    val anilist: Boolean = false,
    val notices: List<Notice> = emptyList(),
)

data class Update(val version: String, val changelog: String, val url: String, val sizeBytes: Long, val sha256: String, val publishedAt: Long = 0L)

data class UpdateStatus(val current: String, val update: Update? = null)

data class MalUser(val id: Long, val name: String, val picture: String? = null)

data class SimklAccount(val name: String? = null)

data class SimklCode(val userCode: String, val verificationUri: String, val verificationUriComplete: String, val expiresAt: Long)

data class PluginInfo(val id: String, val name: String, val version: String, val hosts: List<String> = emptyList())

data class Viewer(val id: Long, val name: String, val avatar: String? = null, val showAdult: Boolean = false)

data class ListEntry(
    val anime: AnimeCard,
    val status: String,
    val progress: Int,
    val score: Double,
    val updatedAt: Long,
    val studios: List<String> = emptyList(),
)

data class DicePick(val anime: AnimeCard, val description: String?)

data class HomeRecs(val forYou: List<AnimeCard> = emptyList(), val because: BecauseYouWatched? = null) {
    data class BecauseYouWatched(val seed: AnimeCard, val items: List<AnimeCard>)
}

data class AccountProfile(
    val service: String,
    val id: Long,
    val name: String,
    val avatar: String? = null,
    val banner: String? = null,
    val joined: Long? = null,
    val url: String,
    val titles: Int = 0,
    val episodes: Int = 0,
    val minutes: Int = 0,
    val meanScore: Double? = null,
    val activity: List<Activity> = emptyList(),
) {
    data class Activity(val day: Long, val amount: Int)
}

data class BackupInfo(
    val createdAt: Long,
    val appVersion: String,
    val summary: String,
    val services: List<String> = emptyList(),
)

data class BackupRestored(val summary: String, val warnings: List<String> = emptyList())

data class BackupExported(val path: String, val summary: String)

const val FAILURE_NEXT_NOTHING = "nothing"
const val FAILURE_NEXT_RETRY = "retry"
const val FAILURE_NEXT_OTHER_SERVER = "otherServer"
const val FAILURE_NEXT_SIGN_IN = "signIn"
const val FAILURE_NEXT_DIAGNOSTICS = "diagnostics"
const val FAILURE_NEXT_REINSTALL = "reinstall"

data class CommandError(val code: String, val message: String, val detail: String, val next: String)

