package me.enivzoy.anilife.app.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import me.enivzoy.anilife.mpv.EndReason
import me.enivzoy.anilife.mpv.Event
import me.enivzoy.anilife.mpv.Mpv
import me.enivzoy.anilife.mpv.PropertyValue
import me.enivzoy.anilife.mpv.defaultPlayerOptions
import me.enivzoy.anilife.app.data.AnimeSubtitleSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

data class PlayerSubtitleTrack(
    val id: Int,
    val label: String,
    val language: String?,
    val external: Boolean,
    val selected: Boolean,
)

/**
 * Drives libmpv for the player screen.
 *
 * mpv decodes into a software render target (see [Mpv.SoftwareVideoSurface]); every decoded
 * frame is turned into an [ImageBitmap] so Compose can draw it under the controls. Transport
 * state comes back through mpv property observation, so the seek bar, play/pause icon and end
 * time follow the real playback instead of a simulated clock.
 */
class MpvPlaybackEngine(
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    fallbackDurationSeconds: Double = 0.0,
    private val serverSubtitles: List<AnimeSubtitleSource> = emptyList(),
    private val startPositionSeconds: Double = 0.0,
    private val renderWidth: Int = 1280,
    private val renderHeight: Int = 720,
    private val renderFormat: String = "bgra",
) : AutoCloseable {

    var positionSeconds by mutableStateOf(0.0)
        private set
    var durationSeconds by mutableStateOf(fallbackDurationSeconds)
        private set
    var isPlaying by mutableStateOf(true)
        private set
    var videoFrame by mutableStateOf<ImageBitmap?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var hasEnded by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var subtitleTracks by mutableStateOf<List<PlayerSubtitleTrack>>(emptyList())
        private set
    var isMuted by mutableStateOf(false)
        private set
    var subtitleDelaySeconds by mutableStateOf(0.0)
        private set

    private val closed = AtomicBoolean(false)
    @Volatile private var player: Mpv? = null
    @Volatile private var surface: Mpv.SoftwareVideoSurface? = null
    @Volatile private var worker: Thread? = null

    fun start() {
        if (worker != null || closed.get()) return
        worker = thread(name = "AniLife-playback", isDaemon = true) { runPlayback() }
    }

    private fun runPlayback() {
        try {
            val mpv = Mpv(initialOptions = playbackOptions(headers))
            player = mpv
            mpv.observeDouble("time-pos")
            mpv.observeDouble("duration")
            mpv.observeFlag("pause")
            mpv.startEvents(::onEvent)
            val target = mpv.createSoftwareVideoSurface(renderWidth, renderHeight, renderFormat)
            surface = target
            mpv.loadfile(url)

            while (!closed.get()) {
                if (target.needsRender()) {
                    val bytes = target.render() ?: continue
                    videoFrame = SkiaImage.makeRaster(
                        ImageInfo(renderWidth, renderHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
                        bytes,
                        renderWidth * 4,
                    ).toComposeImageBitmap()
                } else {
                    Thread.sleep(8L)
                }
            }
        } catch (failure: Throwable) {
            error = failure.message?.takeIf(String::isNotBlank)
                ?: failure::class.simpleName
                ?: "Playback failed"
            isLoading = false
        } finally {
            release()
        }
    }

    private fun onEvent(event: Event) {
        when (event) {
            is Event.PropertyChange -> when (event.name) {
                "time-pos" -> (event.value as? PropertyValue.DoubleValue)?.value?.let {
                    positionSeconds = it.coerceAtLeast(0.0)
                }
                "duration" -> (event.value as? PropertyValue.DoubleValue)?.value?.let {
                    if (it.isFinite() && it > 0.0) durationSeconds = it
                }
                "pause" -> (event.value as? PropertyValue.Flag)?.value?.let { isPlaying = !it }
            }
            Event.FileLoaded -> {
                isLoading = false
                player?.getFlagProperty("pause")?.let { isPlaying = !it }
                player?.getDoubleProperty("duration")
                    ?.takeIf { it.isFinite() && it > 0.0 }
                    ?.let { durationSeconds = it }
                serverSubtitles.distinctBy(AnimeSubtitleSource::url).forEach { subtitle ->
                    runCatching { player?.addSubtitle(subtitle.url, subtitle.label, subtitle.language) }
                }
                refreshSubtitleTracks()
                // Resume where the episode was left off, a few seconds back so the viewer hears the
                // line before the one they missed.
                if (startPositionSeconds > 0.0) {
                    val target = startPositionSeconds
                    positionSeconds = target
                    runCatching { player?.seekTo(target) }
                }
            }
            is Event.EndFile -> {
                if (event.reason == EndReason.EOF) {
                    isPlaying = false
                    positionSeconds = durationSeconds
                    hasEnded = true
                } else if (event.reason == EndReason.ERROR) {
                    error = event.error ?: "mpv could not play this stream"
                }
                isLoading = false
            }
            is Event.Log -> {
                val level = event.level
                val msg = event.text
                if (level == "error" || level == "fatal" || level == "warn") {
                    System.err.println("mpv[$level]: $msg")
                }
            }
            else -> Unit
        }
    }

    fun togglePause() {
        val playing = !isPlaying
        isPlaying = playing
        player?.setPaused(!playing)
    }

    fun seekTo(seconds: Double) {
        val target = if (durationSeconds > 0.0) {
            seconds.coerceIn(0.0, durationSeconds)
        } else {
            seconds.coerceAtLeast(0.0)
        }
        positionSeconds = target
        player?.seekTo(target)
    }

    fun seekRelative(deltaSeconds: Double) {
        player?.seekRelative(deltaSeconds)
        positionSeconds = (positionSeconds + deltaSeconds).coerceAtLeast(0.0)
    }

    fun selectSubtitleTrack(id: Int?) {
        player?.setPropertyValue("sid", id?.toString() ?: "no")
        refreshSubtitleTracks()
    }

    fun addSubtitle(source: AnimeSubtitleSource) {
        player?.addSubtitle(source.url, source.label, source.language, select = true)
        refreshSubtitleTracks()
    }

    fun setVolume(volume: Int) {
        player?.setVolume(volume)
    }

    fun setSpeed(speed: Double) {
        player?.setSpeed(speed)
    }

    fun setMute(muted: Boolean) {
        isMuted = muted
        player?.setPropertyValue("mute", if (muted) "yes" else "no")
    }

    /** Shifts the subtitles earlier (negative) or later (positive), in seconds. */
    fun shiftSubtitles(seconds: Double) {
        subtitleDelaySeconds = seconds
        player?.setPropertyValue("sub-delay", seconds.toString())
    }

    fun setContentScale(scale: String) {
        player?.setPropertyValue("keepaspect", if (scale == "fill") "no" else "yes")
        player?.setPropertyValue("panscan", if (scale == "crop") "1" else "0")
    }

    private fun refreshSubtitleTracks() {
        val text = runCatching { player?.getStringProperty("track-list") }.getOrNull() ?: return
        subtitleTracks = runCatching {
            (Json.parseToJsonElement(text) as? JsonArray ?: JsonArray(emptyList())).mapNotNull { item ->
                val track = item as? JsonObject ?: return@mapNotNull null
                if (track.string("type") != "sub") return@mapNotNull null
                val id = track.string("id")?.toIntOrNull() ?: return@mapNotNull null
                val language = track.string("lang")?.takeIf(String::isNotBlank)
                val title = track.string("title")?.takeIf(String::isNotBlank)
                val external = track.string("external") == "true"
                val label = title ?: language?.let(::subtitleLanguageName)
                    ?: if (external) "External subtitle" else "Subtitle $id"
                PlayerSubtitleTrack(id, label, language, external, track.string("selected") == "true")
            }
        }.getOrDefault(emptyList())
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private fun subtitleLanguageName(code: String): String = when (code.lowercase()) {
        "en" -> "English"
        "ja" -> "Japanese"
        "es" -> "Spanish"
        "fr" -> "French"
        "de" -> "German"
        "it" -> "Italian"
        "pt" -> "Portuguese"
        "ru" -> "Russian"
        "zh" -> "Chinese"
        else -> code.uppercase()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker?.let { runCatching { it.join(3_000L) } }
        release()
    }

    private fun release() {
        surface?.let { runCatching { it.close() } }
        surface = null
        player?.let { runCatching { it.shutdown() } }
        player = null
    }

    private fun playbackOptions(headers: Map<String, String>): List<Pair<String, String>> {
        val options = defaultPlayerOptions()
            .filterNot { it.first == "hwdec" }
            .toMutableList()
        // The software renderer needs decoded frames in system memory.
        options += "hwdec" to "no"
        val fields = headers.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .map { (name, value) -> "$name: ${value.replace(',', ' ').replace('\n', ' ')}" }
        if (fields.isNotEmpty()) options += "http-header-fields" to fields.joinToString(",")
        return options
    }
}

