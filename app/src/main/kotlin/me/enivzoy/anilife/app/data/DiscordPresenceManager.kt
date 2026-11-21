package me.enivzoy.anilife.app.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import xyz.orbitary.perigee.activity.Activity
import xyz.orbitary.perigee.activity.ActivityType
import xyz.orbitary.perigee.activity.Timestamps
import java.time.Instant
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** How often the worker wakes up to check its rate-limit and reconnect gates. */
private const val TICK_MS = 250L
/** Discord rejects SET_ACTIVITY bursts; never publish twice inside this window. */
private const val MIN_PUBLISH_INTERVAL = 5_000L
/**
 * Discord does not always answer SET_ACTIVITY (unregistered assets, disabled Rich Presence, ...).
 * The IPC future can then stay incomplete forever, so expire an unacknowledged publish ourselves.
 */
private const val ACK_TIMEOUT = 3_000L
/** Backoff after a failed connect or two failed SET_ACTIVITY attempts. */
private const val RECONNECT_INTERVAL = 30_000L
/** Ignore position drift smaller than this; Discord's own timer handles it. */
private const val SEEK_TOLERANCE = 10.0
/** Republish even an unchanged card periodically so a Discord restart can't leave us blank. */
private const val REFRESH_INTERVAL = 5 * 60 * 1_000L
private const val IDLE_IDENTITY = "idle"

data class DiscordPresenceTarget(
    val sessionKey: String,
    val title: String,
    val isMovie: Boolean,
    val season: String?,
    val year: Int?,
    val episode: Double?,
    val seasonNumber: Int? = null,
    val cover: String? = null,
)

/** Metadata supplied by the player; playback state is added in [Presence]. */
data class DiscordPlaybackInfo(
    val animeId: Long,
    val title: String,
    val episode: Double,
    val episodeTitle: String? = null,
    val total: Int? = null,
    val cover: String? = null,
    val embedded: Boolean = false,
)

/** A full Rich Presence report. [identity] deliberately ignores the clock. */
data class Presence(
    val animeId: Long,
    val title: String,
    val episode: Double,
    val episodeTitle: String?,
    val total: Int?,
    val cover: String?,
    val position: Double,
    val duration: Double,
    val paused: Boolean,
    val embedded: Boolean = false,
) {
    /** Use the vertical AniList cover; the wide splashart gets cropped/scaled by Discord. */
    fun imageUrl(): String? = discordImageKey(cover)

    /** Identity of the card itself; ignores playback state so a pause cannot look like a new anime. */
    fun cardKey(): String = "$animeId/$title/$episode/$episodeTitle/${imageUrl() ?: "none"}"

    fun identity(): String = "${cardKey()}/$paused"
}

/** Discord accepts http(s) URLs as asset keys; reject anything it would refuse before publishing. */
internal fun discordImageKey(url: String?): String? {
    val value = url?.trim()?.takeIf(String::isNotBlank) ?: return null
    if (value.length > 512) return null
    return value.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }
}

sealed class PresenceMessage {
    data class Configure(
        val enabled: Boolean,
        val applicationId: String,
        val reconnect: Boolean = false,
    ) : PresenceMessage()

    data class Show(val presence: Presence, val reportedAt: Long, val epoch: Long) : PresenceMessage()
    data class Clear(val playersOnly: Boolean = false) : PresenceMessage()
    data class PublishResult(
        val generation: Long,
        val submission: Long,
        val identity: String,
        val position: Double,
        val failure: Throwable?,
    ) : PresenceMessage()
}

enum class DiscordPresenceStatus {
    Disabled,
    Connecting,
    Connected,
    Unavailable,
}

data class DiscordPresenceState(
    val status: DiscordPresenceStatus = DiscordPresenceStatus.Disabled,
    /** The Discord account the desktop client is signed in as, once the pipe is ready. */
    val accountName: String? = null,
    val username: String? = null,
    val userId: String? = null,
    val avatarHash: String? = null,
) {
    val avatarUrl: String?
        get() = if (!userId.isNullOrBlank() && !avatarHash.isNullOrBlank()) {
            "https://cdn.discordapp.com/avatars/$userId/$avatarHash.png?size=256"
        } else {
            null
        }
}

/** Publicly hosted AniLife icon used as the presence image when nothing is playing. */
const val ANILIFE_ICON_URL =
    "https://raw.githubusercontent.com/anilifecommunity/kotlindesktop/main/app/src/main/resources/anilife-icon.png"

const val ANILIFE_WEBSITE_URL = "https://anilife.cc/"
const val ANILIFE_REPO_URL = "https://github.com/anilifecommunity/kotlindesktop/"

/** AniList media page for a known AniList id; returns null for local/unknown entries. */
internal fun anilistMediaUrl(animeId: Long): String? =
    animeId.takeIf { it > 0 }?.let { "https://anilist.co/anime/$it" }

/** The idle card, kept for the Settings preview and for "nothing playing" reports. */
internal fun buildDiscordPresenceActivity(
    target: DiscordPresenceTarget?,
    startedAt: Instant,
): Activity {
    val details = target?.title?.trim()?.ifBlank { "Anime" } ?: "Idling"
    val state = target?.let { t ->
        val episode = t.episode
        if (episode != null && episode > 0) {
            val season = (t.seasonNumber ?: 1).coerceAtLeast(1)
            "On S%02dE%02d".format(season, episode.toInt())
        } else {
            "App developed by witherbl_de"
        }
    } ?: "App developed by witherbl_de"

    return Activity.builder()
        .name("AniLife")
        .type(ActivityType.WATCHING)
        .details(details.discordText())
        .state(state.discordText())
        .button("Visit Website", ANILIFE_WEBSITE_URL)
        .button("Star the Repo", ANILIFE_REPO_URL)
        .apply { startedAt(startedAt) }
        .build()
}

/** `Ep: 3/12`, with half-episodes kept as `10.5`. */
internal fun episodeLabel(episode: Double, total: Int?): String {
    val number = episode.episodeLabel()
    return if (total != null && total > 0) "Ep: $number/$total" else "Ep: $number"
}

/** Port of the Rust `activity()`: episode state, pause badge, cover and playback timestamps. */
internal fun buildActivity(p: Presence): Activity {
    val label = episodeLabel(p.episode, p.total)
    val state = p.episodeTitle?.trim()?.takeIf(String::isNotBlank)
        ?.let { "$label - $it" } ?: label

    // Pause state stays in `state`; the card is still a normal "Watching AniLife" presence.
    // The large image is the vertical AniList cover for the anime currently playing.
    val builder = Activity.builder()
        .name("AniLife")
        .type(ActivityType.WATCHING)
        .details(p.title.trim().ifBlank { "Anime" }.discordText())
        .state(state.discordText())

    val largeImage = p.imageUrl()
    largeImage?.let { image ->
        builder.largeImage(image, state.discordText())
        DiscordPresenceLog.d("player card large image: $image")
    } ?: DiscordPresenceLog.d("player card has no valid AniList image; omitting large image")

    anilistMediaUrl(p.animeId)?.let { url ->
        builder.button("View Anime on AniList", url)
    } ?: DiscordPresenceLog.d("no AniList id for ${p.title}; omitting anime button")
    builder.button("View Source", ANILIFE_REPO_URL)

    val now = Instant.now()
    if (!p.paused && p.duration > 0.0) {
        val start = now.minusMillis((p.position.coerceIn(0.0, p.duration) * 1000).toLong())
        val end = start.plusMillis((p.duration * 1000).toLong())
        builder.timestamps(Timestamps(start, end))
    } else if (!p.paused) {
        val start = now.minusMillis((p.position.coerceAtLeast(0.0) * 1000).toLong())
        builder.timestamps(Timestamps(start, null))
    }
    return builder.build()
}

/**
 * Playback-driven Rich Presence. A dedicated worker thread owns the IPC client and the
 * `wanted`/`published` state machine ported from the legacy implementation: reports are coalesced,
 * republished only when the card really changes (identity, >10s seek or a 5 minute refresh) and
 * retried automatically when Discord restarts.
 */
class DiscordPresenceManager : AutoCloseable {
    private data class Wanted(val presence: Presence, val reportedAt: Long)
    private data class Published(val identity: String, val position: Double, val at: Long)

    private val closed = AtomicBoolean(false)
    private val playerEpoch = AtomicLong(0)
    private val messages = Channel<PresenceMessage>(Channel.UNLIMITED)
    private val workerThread: Thread
    private val mutableState = MutableStateFlow(DiscordPresenceState())
    val state: StateFlow<DiscordPresenceState> = mutableState.asStateFlow()

    @Volatile private var enabled: Boolean = false
    @Volatile private var applicationId: String? = null
    @Volatile private var client: DiscordIpcClient? = null
    @Volatile private var connected: Boolean = false
    /** Short guard that stops a late mpv report from resurrecting a card after exit. */
    @Volatile private var suppressPlayerUntil = 0L
    @Volatile private var lastShownCardKey: String? = null
    @Volatile private var lastClearedCardKey: String? = null

    // Worker-owned fields: only touched from workerThread or the event callbacks it registers.
    private var wanted: Wanted? = null
    private var published: Published? = null
    private var nextAttempt: Long = 0L
    private var lastLoggedState: String = "none"
    private var generation: Long = 0L
    private var publishPending = false
    private var pendingSubmission: Long = 0L
    private var submissionCounter: Long = 0L
    private var pendingSince: Long = 0L

    init {
        workerThread = Thread({ runWorker() }, "discord-rich-presence").apply { isDaemon = true }
        workerThread.start()
        DiscordPresenceLog.d("worker started; log file = ${DiscordPresenceLog.logFile()}")
    }

    /** Configure the pipe (and the settings guard). Reconnect forces a fresh IPC connection. */
    fun configure(enabled: Boolean, configuredApplicationId: String, reconnect: Boolean = false) {
        if (closed.get()) return
        val id = configuredApplicationId.trim()
        val active = enabled && id.isNotEmpty() && id.all(Char::isDigit)
        this.enabled = active
        this.applicationId = id.takeIf { active }
        DiscordPresenceLog.d(
            "configure enabled=$enabled id='$configuredApplicationId' active=$active reconnect=$reconnect " +
                "(note: OAuth2 sign-in is unrelated to the local Discord IPC pipe)",
        )
        send(PresenceMessage.Configure(active, id, reconnect))
    }

    /** Backwards-compatible entry point for callers that still have a [DiscordPresenceTarget]. */
    fun update(
        enabled: Boolean,
        configuredApplicationId: String,
        target: DiscordPresenceTarget?,
        reconnect: Boolean = false,
    ) {
        configure(enabled, configuredApplicationId, reconnect)
        if (target != null) {
            show(
                Presence(
                    animeId = target.sessionKey.toLongOrNull() ?: 0L,
                    title = target.title,
                    episode = target.episode?.takeIf { it > 0.0 } ?: 1.0,
                    episodeTitle = null,
                    total = null,
                    cover = target.cover,
                    position = 0.0,
                    duration = 0.0,
                    paused = false,
                ),
            )
        } else if (enabled) {
            send(PresenceMessage.Clear(playersOnly = true))
        }
    }

    /** Called by the player on every meaningful transport update. */
    fun show(presence: Presence) {
        if (closed.get() || !enabled) {
            DiscordPresenceLog.d(
                "show DROPPED (closed=${closed.get()} enabled=$enabled): ${presence.title} " +
                    "ep=${presence.episode} pos=${presence.position.toInt()} paused=${presence.paused}",
            )
            return
        }
        val epoch = playerEpoch.get()
        lastShownCardKey = presence.cardKey()
        DiscordPresenceLog.d(
            "show: ${presence.title} ep=${presence.episode} pos=${presence.position.toInt()}/${presence.duration.toInt()} " +
                "paused=${presence.paused} epoch=$epoch identity=${presence.identity()}",
        )
        send(PresenceMessage.Show(presence, System.currentTimeMillis(), epoch))
    }

    /** Playback stopped: drop the player card and fall back to the idle card. */
    fun clearPlayer() {
        if (closed.get()) return
        val epoch = playerEpoch.incrementAndGet()
        lastClearedCardKey = lastShownCardKey
        suppressPlayerUntil = System.currentTimeMillis() + 750L
        DiscordPresenceLog.d("clearPlayer (epoch=$epoch, suppressed card=$lastClearedCardKey for 750ms)")
        send(PresenceMessage.Clear(playersOnly = true))
    }

    /** Presence disabled: clear the card and shut the pipe down. */
    fun clear() {
        if (closed.get()) return
        DiscordPresenceLog.d("clear (disable presence)")
        enabled = false
        applicationId = null
        playerEpoch.incrementAndGet()
        send(PresenceMessage.Clear(playersOnly = false))
    }

    private fun send(message: PresenceMessage) {
        if (closed.get()) return
        messages.trySend(message)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        messages.close()
    }

    private fun runWorker() = runBlocking {
        while (!closed.get()) {
            // Drain bursts (mpv emits time-pos very often) so only the newest report matters.
            while (true) {
                val message = messages.tryReceive().getOrNull() ?: break
                handleMessage(message)
            }
            if (enabled) pump()
            delay(TICK_MS)
        }
        clearActivity()
        closeClient()
        publishState(DiscordPresenceState(DiscordPresenceStatus.Disabled))
    }

    private fun handleMessage(message: PresenceMessage) {
        when (message) {
            is PresenceMessage.Configure -> applyConfiguration(message)
            is PresenceMessage.Show -> {
                when {
                    message.epoch != playerEpoch.get() -> DiscordPresenceLog.d(
                        "worker DROPPED stale Show epoch=${message.epoch} current=${playerEpoch.get()}",
                    )
                    message.reportedAt < suppressPlayerUntil &&
                        message.presence.cardKey() == lastClearedCardKey -> DiscordPresenceLog.d(
                        "worker DROPPED post-clear Show for ${message.presence.cardKey()}",
                    )
                    !enabled -> DiscordPresenceLog.w("worker DROPPED Show (worker enabled=false)")
                    else -> {
                        val incomingIdentity = message.presence.identity()
                        if (wanted?.presence?.identity() != incomingIdentity &&
                            published?.identity != incomingIdentity
                        ) {
                            // A different anime must not wait out the previous card's 5s gate.
                            nextAttempt = System.currentTimeMillis()
                            DiscordPresenceLog.d("worker accelerating new identity=$incomingIdentity")
                        }
                        wanted = Wanted(message.presence, message.reportedAt)
                        DiscordPresenceLog.d("worker accepted Show -> wanted (epoch=${message.epoch})")
                    }
                }
            }
            is PresenceMessage.PublishResult -> {
                if (message.generation != generation || message.submission != pendingSubmission) {
                    DiscordPresenceLog.d(
                        "ignoring stale SET_ACTIVITY result (gen ${message.generation}/${generation}, " +
                            "sub ${message.submission}/$pendingSubmission)",
                    )
                    return
                }
                if (message.submission != submissionCounter) {
                    // A newer card was already submitted; its result supersedes this one.
                    DiscordPresenceLog.d("ignoring superseded SET_ACTIVITY result #${message.submission}")
                    return
                }
                // This is the in-flight publish, regardless of whether Discord acknowledged it.
                // Forgetting this is what made every card change wait out ACK_TIMEOUT.
                publishPending = false
                if (message.failure == null) {
                    DiscordPresenceLog.d("SET_ACTIVITY acknowledged identity=${message.identity}")
                } else if (message.failure is TimeoutException) {
                    // Discord often applies the card without replying. Do not claim it failed.
                    DiscordPresenceLog.w(
                        "SET_ACTIVITY not acknowledged identity=${message.identity} " +
                            "(presence may still be visible)",
                    )
                } else {
                    DiscordPresenceLog.e(
                        "SET_ACTIVITY failed identity=${message.identity}: ${message.failure}",
                        message.failure,
                    )
                    published = null
                    connected = false
                    closeClient()
                    nextAttempt = System.currentTimeMillis() + RECONNECT_INTERVAL
                    publishState(DiscordPresenceState(DiscordPresenceStatus.Unavailable))
                }
            }
            is PresenceMessage.Clear -> {
                wanted = null
                if (message.playersOnly) {
                    DiscordPresenceLog.d("worker Clear(playersOnly) -> clearing activity")
                    clearActivity()
                    published = null
                    // Drop any in-flight SET_ACTIVITY and publish the idle card on the next tick.
                    publishPending = false
                    pendingSubmission = ++submissionCounter
                    nextAttempt = System.currentTimeMillis()
                } else {
                    DiscordPresenceLog.d("worker Clear(all) -> disabling")
                    enabled = false
                    applicationId = null
                    closeClient()
                    published = null
                    publishState(DiscordPresenceState(DiscordPresenceStatus.Disabled))
                }
            }
        }
    }

    private fun applyConfiguration(message: PresenceMessage.Configure) {
        val previousId = applicationId
        enabled = message.enabled
        applicationId = message.applicationId.takeIf { enabled }
        DiscordPresenceLog.d(
            "worker Configure enabled=$enabled appId=$applicationId previousId=$previousId reconnect=${message.reconnect}",
        )
        if (!enabled || applicationId.isNullOrBlank()) {
            wanted = null
            published = null
            closeClient()
            publishState(DiscordPresenceState(DiscordPresenceStatus.Disabled))
            return
        }
        if (message.reconnect || previousId != applicationId) {
            DiscordPresenceLog.d("worker forcing fresh IPC client")
            closeClient()
            published = null
            nextAttempt = System.currentTimeMillis()
        }
        ensureConnected()
    }

    /** Why the card is stale, or null when it is already current. */
    private fun due(now: Long): String? {
        val current = wanted ?: run {
            DiscordPresenceLog.d("due: no wanted presence")
            return null
        }
        val p = current.presence
        val reason = when (val pub = published) {
            null -> "never published"
            else -> {
                val drifted = if (!p.paused) {
                    val elapsed = (now - pub.at) / 1000.0
                    abs(p.position - (pub.position + elapsed)) > SEEK_TOLERANCE
                } else false
                when {
                    pub.identity != p.identity() -> "identity changed (${pub.identity} -> ${p.identity()})"
                    drifted -> "seek drift > ${SEEK_TOLERANCE.toInt()}s"
                    now - pub.at >= REFRESH_INTERVAL -> "refresh"
                    else -> null
                }
            }
        }
        if (reason == null) {
            DiscordPresenceLog.d("due: no (unchanged card)")
            return null
        }
        DiscordPresenceLog.d("due: YES ($reason), gate at ${nextAttempt - now}ms")
        return reason
    }

    private fun pump() {
        val now = System.currentTimeMillis()
        val state = client?.state()?.name ?: "none"
        if (state != lastLoggedState) {
            DiscordPresenceLog.d("IPC state -> $state (connected=$connected)")
            lastLoggedState = state
        }
        if (publishPending) {
            if (now - pendingSince >= ACK_TIMEOUT) {
                // Discord may have applied the card without replying; just stop treating the
                // future as in-flight so a later update is not blocked. Do not clear `published`.
                DiscordPresenceLog.w(
                    "SET_ACTIVITY still unacknowledged after ${ACK_TIMEOUT}ms; allowing next update",
                )
                publishPending = false
                pendingSubmission = ++submissionCounter
            }
            return
        }
        if (client == null || !connected) {
            if (!ensureConnected()) return
        }

        val current = wanted
        if (current == null) {
            val pub = published
            if (now < nextAttempt) return
            if (pub == null || pub.identity != IDLE_IDENTITY || now - pub.at >= REFRESH_INTERVAL) {
                DiscordPresenceLog.d("pump: publishing idle card")
                publishActivity(buildDiscordPresenceActivity(null, Instant.ofEpochMilli(now)), IDLE_IDENTITY, 0.0)
            }
            return
        }

        val p = current.presence
        val reason = due(now) ?: return
        // A genuinely different card must not wait out the previous card's rate-limit gate.
        if (reason.startsWith("identity changed")) nextAttempt = now
        if (now < nextAttempt) return
        // Bring the clock up to now: the report may have waited out the rate-limit window.
        val position = if (p.paused) p.position
        else p.position + (now - current.reportedAt) / 1000.0
        val card = p.copy(position = position.coerceAtLeast(0.0))
        DiscordPresenceLog.d(
            "pump: publishing player card identity=${card.identity()} " +
                "position=${card.position.toInt()} image=${card.imageUrl() ?: "none"}",
        )
        publishActivity(buildActivity(card), card.identity(), card.position)
    }

    /**
     * Start (or reuse) the IPC client. `connect()` is deliberately non-blocking: the client keeps
     * retrying in the background until Discord's pipe appears. Blocking on a 5s timeout and then
     * closing the client killed that retry loop, which is why presence never appeared when Discord
     * was launched after the app or took a moment to expose the pipe.
     */
    private fun ensureConnected(): Boolean {
        val id = applicationId
        if (id.isNullOrBlank()) {
            DiscordPresenceLog.w("ensureConnected: no valid numeric application id")
            return false
        }
        val current = client
        if (current != null) {
            if (current.isReady()) {
                if (!connected) DiscordPresenceLog.d("existing client became Ready")
                connected = true
                published = null
                nextAttempt = System.currentTimeMillis()
                if (mutableState.value.status != DiscordPresenceStatus.Connected) {
                    publishState(DiscordPresenceState(DiscordPresenceStatus.Connected))
                }
                return true
            }
            // Already connecting/backing off; the Ready handler will wake us.
            return false
        }
        val now = System.currentTimeMillis()
        if (now < nextAttempt) {
            DiscordPresenceLog.d("ensureConnected: backoff ${nextAttempt - now}ms")
            return false
        }

        DiscordPresenceLog.d("ensureConnected: building DiscordIpcClient appId=$id")
        publishState(DiscordPresenceState(DiscordPresenceStatus.Connecting))
        // The generation token identifies this connection; closeClient() advances it so callbacks
        // from a discarded client can never mutate the current state.
        val token = ++generation
        val newClient = try {
            DiscordIpcClient(
                applicationId = id,
                onReady = { user ->
                    if (generation == token && !closed.get()) {
                        DiscordPresenceLog.d("Ready: Discord IPC is live")
                        connected = true
                        published = null
                        nextAttempt = System.currentTimeMillis()
                        val displayName = user.globalName?.takeIf(String::isNotBlank)
                            ?: user.username?.takeIf(String::isNotBlank)
                        publishState(
                            DiscordPresenceState(
                                status = DiscordPresenceStatus.Connected,
                                accountName = displayName,
                                username = user.username?.takeIf(String::isNotBlank),
                                userId = user.id?.takeIf(String::isNotBlank),
                                avatarHash = user.avatar?.takeIf(String::isNotBlank),
                            ),
                        )
                    }
                },
                onDisconnected = { cause ->
                    if (generation == token && !closed.get()) {
                        connected = false
                        published = null
                        nextAttempt = System.currentTimeMillis() + RECONNECT_INTERVAL
                        publishState(DiscordPresenceState(DiscordPresenceStatus.Unavailable))
                        DiscordPresenceLog.w(
                            "Disconnected (retrying): ${cause?.message ?: "unknown cause"}",
                        )
                    }
                },
            )
        } catch (failure: Exception) {
            DiscordPresenceLog.e("could not build DiscordIpcClient", failure)
            connected = false
            nextAttempt = now + RECONNECT_INTERVAL
            publishState(DiscordPresenceState(DiscordPresenceStatus.Unavailable))
            return false
        }

        client = newClient
        lastLoggedState = "none"
        try {
            // No timeout wait: the IPC client owns reconnection. A Ready event flips `connected`
            // and the next pump publishes the card that is already waiting in `wanted`.
            newClient.connect()
            DiscordPresenceLog.d("connect() started; state=${newClient.state()}")
        } catch (failure: Exception) {
            DiscordPresenceLog.e("connect() failed immediately", failure)
            closeClient()
            nextAttempt = System.currentTimeMillis() + RECONNECT_INTERVAL
            publishState(DiscordPresenceState(DiscordPresenceStatus.Unavailable))
            return false
        }
        if (newClient.isReady()) {
            connected = true
            published = null
            nextAttempt = System.currentTimeMillis()
            return true
        }
        return false
    }

    /**
     * Submit SET_ACTIVITY without blocking the worker. Discord can take up to ~10s to acknowledge
     * the first activity; waiting on the future used to stall the worker (and closing a client
     * from that path could hang on the Windows pipe read). The completion is posted back through
     * the worker channel so all state transitions stay single-threaded.
     */
    private fun publishActivity(activity: Activity, identity: String, position: Double) {
        if (publishPending) return
        if (!ensureConnected()) return
        val activeClient = client ?: return
        val currentGeneration = generation
        val submission = ++submissionCounter
        publishPending = true
        pendingSubmission = submission
        pendingSince = System.currentTimeMillis()
        // Optimistic: Discord frequently applies the card without sending an IPC reply, and
        // waiting for one used to freeze the state machine in "waiting for acknowledgement".
        published = Published(identity, position, pendingSince)
        nextAttempt = pendingSince + MIN_PUBLISH_INTERVAL
        DiscordPresenceLog.d("SET_ACTIVITY submitted #$submission identity=$identity position=${position.toInt()}")
        activeClient.setActivity(activity).whenComplete { _, failure ->
            val cause = failure?.let { if (it is java.util.concurrent.CompletionException) it.cause ?: it else it }
            send(
                PresenceMessage.PublishResult(
                    generation = currentGeneration,
                    submission = submission,
                    identity = identity,
                    position = position,
                    failure = cause,
                ),
            )
        }
    }

    private fun clearActivity() {
        val activeClient = client
        if (activeClient == null || !connected) return
        DiscordPresenceLog.d("CLEAR_ACTIVITY submitted")
        activeClient.clearActivity().whenComplete { _, failure ->
            if (failure == null) DiscordPresenceLog.d("CLEAR_ACTIVITY accepted")
            else DiscordPresenceLog.e("CLEAR_ACTIVITY failed", failure)
        }
    }

    private fun closeClient() {
        val current = client
        generation++
        publishPending = false
        pendingSubmission = ++submissionCounter
        client = null
        connected = false
        if (current != null) DiscordPresenceLog.d("closing IPC client")
        current?.let { runCatching { it.close() } }
    }

    private fun publishState(next: DiscordPresenceState) {
        if (!closed.get()) {
            DiscordPresenceLog.d("state -> ${next.status}")
            mutableState.value = next
        }
    }
}

private fun Double.episodeLabel(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun String.discordText(maxCodePoints: Int = 128): String {
    val count = codePointCount(0, length)
    if (count <= maxCodePoints) return this
    val end = offsetByCodePoints(0, maxCodePoints - 1)
    return substring(0, end) + "…"
}
