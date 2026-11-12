package me.enivzoy.anilife.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.orbitary.perigee.PerigeeClient
import xyz.orbitary.perigee.activity.Activity
import xyz.orbitary.perigee.activity.ActivityType
import xyz.orbitary.perigee.activity.StatusDisplayType
import xyz.orbitary.perigee.event.Disconnected
import xyz.orbitary.perigee.event.Ready
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

data class DiscordPresenceTarget(
    val sessionKey: String,
    val title: String,
    val isMovie: Boolean,
    val season: String?,
    val year: Int?,
    val episode: Double?,
    val cover: String? = null,
)

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
)

internal fun buildDiscordPresenceActivity(
    target: DiscordPresenceTarget?,
    startedAt: Instant,
): Activity {
    val details = target?.let(::watchingDetails) ?: "Idling"

    return Activity.builder()
        .name("AniLife")
        .type(ActivityType.WATCHING)
        // The member list says "Watching Idling" or "Watching <series>" instead of the app name.
        .statusDisplay(StatusDisplayType.DETAILS)
        .details(details.discordText())
        .state("friend witherbl_de")
        .apply {
            if (target != null) {
                startedAt(startedAt)
                target.cover?.takeIf(String::isNotBlank)?.let { cover ->
                    largeImage(cover.trim(), target.title.discordText())
                }
            }
        }
        .build()
}

/** `Title - Fall 2026 Episode 8`; movies only show their title. */
private fun watchingDetails(target: DiscordPresenceTarget): String {
    val title = target.title.trim().ifBlank { "Anime" }
    if (target.isMovie) return title

    val season = listOfNotNull(
        target.season?.trim()?.takeIf(String::isNotBlank)?.lowercase()?.replaceFirstChar(Char::titlecase),
        target.year?.toString(),
    ).joinToString(" ")
    val episode = target.episode?.let { "Episode ${it.episodeLabel()}" }
    val metadata = listOfNotNull(season.takeIf(String::isNotBlank), episode)
    return if (metadata.isEmpty()) title else "$title - ${metadata.joinToString(" ")}"
}

/**
 * Keeps a Discord Rich Presence card in step with the app over the desktop client's local IPC
 * pipe. The pipe only needs the application id; the client reconnects on its own if Discord starts
 * late, and [update] can force a fresh connection for the Settings button.
 */
class DiscordPresenceManager : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "discord-rich-presence").apply { isDaemon = true }
    }

    private val mutableState = MutableStateFlow(DiscordPresenceState())
    val state: StateFlow<DiscordPresenceState> = mutableState.asStateFlow()

    @Volatile
    private var client: PerigeeClient? = null
    private var applicationId: String? = null
    private var enabled = false
    private var wantedTarget: DiscordPresenceTarget? = null
    private var hasPublished = false
    private var lastPublishedTarget: DiscordPresenceTarget? = null
    private var activeSessionKey: String? = null
    private var sessionStartedAt = Instant.now()

    fun update(
        enabled: Boolean,
        configuredApplicationId: String,
        target: DiscordPresenceTarget?,
        reconnect: Boolean = false,
    ) {
        if (closed.get()) return
        try {
            executor.execute {
                if (closed.get()) return@execute

                val id = configuredApplicationId.trim()
                val active = enabled && id.isNotEmpty() && id.all(Char::isDigit)
                this.enabled = active
                wantedTarget = target

                if (!active) {
                    closeClient()
                    applicationId = null
                    hasPublished = false
                    lastPublishedTarget = null
                    publishState(DiscordPresenceState(DiscordPresenceStatus.Disabled))
                    return@execute
                }

                if (target?.sessionKey != activeSessionKey) {
                    activeSessionKey = target?.sessionKey
                    sessionStartedAt = Instant.now()
                }

                if (reconnect || applicationId != id) closeClient()
                applicationId = id
                ensureClient(id)

                if (reconnect || !hasPublished || target != lastPublishedTarget) {
                    publishCurrent()
                }
            }
        } catch (_: RejectedExecutionException) {
            // Closing raced this update; there is no presence left to configure.
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            executor.execute {
                closeClient()
                publishState(DiscordPresenceState(DiscordPresenceStatus.Disabled))
            }
        } catch (_: RejectedExecutionException) {
            closeClient()
        }
        executor.shutdown()
    }

    private fun ensureClient(id: String) {
        if (client != null) return
        publishState(DiscordPresenceState(DiscordPresenceStatus.Connecting))

        val newClient = PerigeeClient.builder(id)
            .autoReconnect(true)
            .reconnectDelay(Duration.ofSeconds(2), Duration.ofSeconds(30))
            .build()

        newClient.on(Ready::class.java) { ready ->
            if (client === newClient) {
                val name = ready.user()?.globalName()?.takeIf(String::isNotBlank)
                    ?: ready.user()?.username()?.takeIf(String::isNotBlank)
                publishState(DiscordPresenceState(DiscordPresenceStatus.Connected, name))
            }
        }
        newClient.on(Disconnected::class.java) { event ->
            if (client === newClient) {
                // Perigee keeps retrying in the background; the UI just needs to say Discord is
                // absent until a Ready event arrives.
                publishState(DiscordPresenceState(DiscordPresenceStatus.Unavailable))
                if (!closed.get()) {
                    val suffix = if (event.reconnecting()) " (retrying)" else ""
                    System.err.println("Discord Rich Presence disconnected$suffix: ${event.cause()?.message ?: "unknown"}")
                }
            }
        }

        client = newClient
        // Perigee's auto-reconnect keeps the plain connect future pending while Discord is absent,
        // which would leave the Settings card on "Connecting" forever. Bound the initial wait so
        // the card can say Discord isn't running; a later Ready event still flips it to Connected.
        newClient.connect(Duration.ofSeconds(5)).whenComplete { _, failure ->
            if (failure != null) {
                if (client === newClient) {
                    publishState(DiscordPresenceState(DiscordPresenceStatus.Unavailable))
                }
                if (!closed.get()) {
                    System.err.println("Discord Rich Presence connection unavailable: ${failure.message}")
                }
            } else if (client === newClient &&
                mutableState.value.status != DiscordPresenceStatus.Connected
            ) {
                publishState(DiscordPresenceState(DiscordPresenceStatus.Connected))
            }
        }
    }

    private fun publishCurrent() {
        val activeClient = client ?: return
        val target = wantedTarget
        val activity = buildDiscordPresenceActivity(target, sessionStartedAt)
        lastPublishedTarget = target
        hasPublished = true
        activeClient.setActivity(activity).whenComplete { _, failure ->
            if (failure != null && !closed.get()) {
                System.err.println("Discord Rich Presence update failed: ${failure.message}")
            }
        }
    }

    private fun closeClient() {
        val current = client
        client = null
        if (current != null) {
            runCatching { current.close() }
                .onFailure { failure ->
                    System.err.println("Discord Rich Presence shutdown failed: ${failure.message}")
                }
        }
    }

    private fun publishState(next: DiscordPresenceState) {
        if (!closed.get()) mutableState.value = next
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

