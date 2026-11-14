package me.enivzoy.anilife.mpv

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

const val MPV_ERROR_NONE: Int = 0
const val MPV_ERROR_OPTION_NOT_FOUND: Int = -5

const val MPV_EVENT_NONE: Int = 0
const val MPV_EVENT_SHUTDOWN: Int = 1
const val MPV_EVENT_LOG_MESSAGE: Int = 2
const val MPV_EVENT_START_FILE: Int = 6
const val MPV_EVENT_END_FILE: Int = 7
const val MPV_EVENT_FILE_LOADED: Int = 8
const val MPV_EVENT_VIDEO_RECONFIG: Int = 17
const val MPV_EVENT_AUDIO_RECONFIG: Int = 18
const val MPV_EVENT_SEEK: Int = 20
const val MPV_EVENT_PLAYBACK_RESTART: Int = 21
const val MPV_EVENT_PROPERTY_CHANGE: Int = 22

const val MPV_FORMAT_NONE: Int = 0
const val MPV_FORMAT_STRING: Int = 1
const val MPV_FORMAT_FLAG: Int = 3
const val MPV_FORMAT_INT64: Int = 4
const val MPV_FORMAT_DOUBLE: Int = 5

const val MPV_RENDER_PARAM_INVALID: Int = 0
const val MPV_RENDER_PARAM_API_TYPE: Int = 1
const val MPV_RENDER_PARAM_SW_SIZE: Int = 17
const val MPV_RENDER_PARAM_SW_FORMAT: Int = 18
const val MPV_RENDER_PARAM_SW_STRIDE: Int = 19
const val MPV_RENDER_PARAM_SW_POINTER: Int = 20

const val MPV_RENDER_UPDATE_FRAME: Long = 1L

class MpvError(val code: Int, override val message: String) : RuntimeException(message)

enum class Format { NONE, STRING, FLAG, INT64, DOUBLE }

enum class EndReason { EOF, STOP, QUIT, ERROR, REDIRECT, UNKNOWN }

sealed class PropertyValue {
    data class Flag(val value: Boolean) : PropertyValue()
    data class Int64(val value: Long) : PropertyValue()
    data class DoubleValue(val value: Double) : PropertyValue()
    data class Str(val value: String) : PropertyValue()
    object None : PropertyValue()
}

sealed class Event {
    object StartFile : Event()
    object FileLoaded : Event()
    data class EndFile(val reason: EndReason, val error: String?, val code: Int?) : Event()
    data class PropertyChange(val name: String, val value: PropertyValue) : Event()
    data class Log(val prefix: String, val level: String, val text: String) : Event()
    object Shutdown : Event()
}

interface MpvPlayer {
    fun loadfile(url: String, flags: String = "replace")

    fun play()

    fun pause()

    fun shutdown()
}

class Mpv(
    libraryPath: String? = null,
    initialOptions: List<Pair<String, String>> = defaultPlayerOptions(),
) : MpvPlayer {

    private val library: NativeLibrary = NativeLibrary.getInstance(libraryPath ?: defaultLibraryPath())
    private val mpvCreate: Function = library.getFunction("mpv_create")
    private val mpvClientApiVersion: Function = library.getFunction("mpv_client_api_version")
    private val mpvErrorString: Function = library.getFunction("mpv_error_string")
    private val mpvFree: Function = library.getFunction("mpv_free")
    private val mpvInitialize: Function = library.getFunction("mpv_initialize")
    private val mpvTerminateDestroy: Function = library.getFunction("mpv_terminate_destroy")
    private val mpvSetOptionString: Function = library.getFunction("mpv_set_option_string")
    private val mpvSetPropertyString: Function = library.getFunction("mpv_set_property_string")
    private val mpvCommandString: Function = library.getFunction("mpv_command_string")
    private val mpvGetPropertyString: Function = library.getFunction("mpv_get_property_string")
    private val mpvGetProperty: Function = library.getFunction("mpv_get_property")
    private val mpvObserveProperty: Function = library.getFunction("mpv_observe_property")
    private val mpvRequestLogMessages: Function = library.getFunction("mpv_request_log_messages")
    private val mpvWaitEvent: Function = library.getFunction("mpv_wait_event")
    private val mpvWakeup: Function = library.getFunction("mpv_wakeup")
    private val mpvRenderContextCreate: Function = library.getFunction("mpv_render_context_create")
    private val mpvRenderContextFree: Function = library.getFunction("mpv_render_context_free")
    private val mpvRenderContextUpdate: Function = library.getFunction("mpv_render_context_update")
    private val mpvRenderContextRender: Function = library.getFunction("mpv_render_context_render")

    private val handle: Pointer = createHandle()

    private val stopRequested = AtomicBoolean(false)
    private val handlers = CopyOnWriteArrayList<(Event) -> Unit>()
    private val observed = ConcurrentHashMap<Long, String>()
    private val nextId = AtomicLong(1)
    private lateinit var eventThread: Thread

    init {
        val version: Long = callLong(mpvClientApiVersion, arrayOfNulls<Any?>(0))
        val major: Long = version shr 16
        val minor: Long = version and 0xffff
        if (major != 2L) {
            throw MpvError(0, "libmpv client API ${major}.${minor} is not supported (need 2.x)")
        }

        for ((name, value) in initialOptions) {
            val code: Int = callInt(mpvSetOptionString, arrayOf<Any?>(handle, cstr(name), cstr(value)))
            if (code == MPV_ERROR_OPTION_NOT_FOUND) continue
            check(code == MPV_ERROR_NONE) { "mpv option ${name}=${value} failed with code $code: ${errorString(code)}" }
        }

        val code: Int = callInt(mpvInitialize, arrayOf<Any?>(handle))
        check(code == MPV_ERROR_NONE) { "mpv_initialize failed with code $code: ${errorString(code)}" }

        eventThread = thread(name = "mpv-events") { eventLoop() }
    }

    override fun loadfile(url: String, flags: String) {
        val escapedUrl = url.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "")
        command(buildString {
            append("loadfile \"").append(escapedUrl).append('\"')
            if (flags.isNotBlank()) append(' ').append(flags)
        })
    }

    override fun play() {
        setProperty("pause", "no")
    }

    override fun pause() {
        setProperty("pause", "yes")
    }

    fun seek(seconds: Int) {
        command("seek $seconds relative")
    }

    fun seekTo(seconds: Double) {
        command("seek ${seconds.coerceAtLeast(0.0)} absolute+exact")
    }

    fun seekRelative(seconds: Double) {
        command("seek $seconds relative+exact")
    }

    fun setPaused(paused: Boolean) {
        setProperty("pause", if (paused) "yes" else "no")
    }

    fun setSpeed(speed: Double) {
        setProperty("speed", speed.coerceIn(0.25, 3.0).toString())
    }

    fun setPropertyValue(name: String, value: String) {
        setProperty(name, value)
    }

    fun getDoubleProperty(name: String): Double? {
        val data = Memory(8L)
        val code = callInt(mpvGetProperty, arrayOf<Any?>(handle, cstr(name), MPV_FORMAT_DOUBLE, data))
        return if (code == MPV_ERROR_NONE) data.getDouble(0) else null
    }

    fun getFlagProperty(name: String): Boolean? {
        val data = Memory(4L)
        val code = callInt(mpvGetProperty, arrayOf<Any?>(handle, cstr(name), MPV_FORMAT_FLAG, data))
        return if (code == MPV_ERROR_NONE) data.getInt(0) != 0 else null
    }

    fun getStringProperty(name: String): String? {
        val value = callPointer(mpvGetPropertyString, arrayOf<Any?>(handle, cstr(name))) ?: return null
        return try {
            value.getString(0, "UTF-8")
        } finally {
            callVoid(mpvFree, arrayOf<Any?>(value))
        }
    }

    fun observeProperty(name: String, format: Format) {
        val id = nextId.getAndIncrement()
        observed[id] = name
        val code = callInt(mpvObserveProperty, arrayOf<Any?>(handle, id, cstr(name), formatCode(format)))
        if (code != MPV_ERROR_NONE) {
            observed.remove(id)
            check(code == MPV_ERROR_NONE) { "mpv could not observe property $name: ${errorString(code)}" }
        }
    }

    fun observeDouble(name: String) {
        observeProperty(name, Format.DOUBLE)
    }

    fun observeFlag(name: String) {
        observeProperty(name, Format.FLAG)
    }

    fun stop() {
        command("stop")
    }

    fun cycleSubtitleTrack() {
        command("cycle sid")
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        setProperty("sub-visibility", if (enabled) "yes" else "no")
    }

    fun addSubtitle(url: String, title: String? = null, language: String? = null, select: Boolean = false) {
        val escapedUrl = url.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "")
        val flags = if (select) "select" else "auto"
        val escapedTitle = title.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "")
        val escapedLanguage = language.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "")
        command(buildString {
            append("sub-add \"").append(escapedUrl).append("\" ").append(flags)
            if (escapedTitle.isNotBlank() || escapedLanguage.isNotBlank()) append(" \"").append(escapedTitle).append('\"')
            if (escapedLanguage.isNotBlank()) append(" \"").append(escapedLanguage).append('\"')
        })
    }

    fun setVolume(volume: Int) {
        setProperty("volume", volume.coerceIn(0, 200).toString())
    }

    override fun shutdown() {
        if (!stopRequested.compareAndSet(false, true)) return
        callVoid(mpvWakeup, arrayOf<Any?>(handle))
        eventThread.join()
        callVoid(mpvTerminateDestroy, arrayOf<Any?>(handle))
    }

    fun startEvents(handler: (Event) -> Unit) {
        handlers.add(handler)
    }

    /**
     * A software-rendered mpv frame target. mpv paints BGRA frames into a native buffer;
     * [render] copies the newest frame into a reusable byte array that Compose can turn into
     * an `ImageBitmap`.
     *
     * Software rendering (rather than `wid`/OpenGL) keeps the video inside the Compose scene
     * graph, so the player chrome can be drawn on top of it instead of being covered by a
     * heavyweight native surface.
     */
    inner class SoftwareVideoSurface(
        val width: Int,
        val height: Int,
        private val format: String = "bgra",
    ) : AutoCloseable {
        private var closed = false
        private val stride = width * 4
        private val pixels = Memory(width.toLong() * height * 4L)
        private val frameBytes = ByteArray(width * height * 4)
        private val sizeMemory = Memory(8L).apply {
            setInt(0, width)
            setInt(4, height)
        }
        private val strideMemory = Memory(8L).apply { setLong(0, stride.toLong()) }
        private val formatMemory = Memory(format.length + 1L).apply { setString(0, format, "UTF-8") }
        private val apiTypeMemory = cstr("sw")
        private val renderParams: Memory
        private val context: Pointer

        init {
            renderParams = buildRenderParams()
            val out = PointerByReference()
            val code = callInt(mpvRenderContextCreate, arrayOf<Any?>(out, handle, renderParams))
            check(code == MPV_ERROR_NONE) {
                "mpv_render_context_create(sw) failed with code $code: ${errorString(code)}"
            }
            context = checkNotNull(out.value) { "mpv_render_context_create returned a null context" }
        }

        private fun buildRenderParams(): Memory {
            val entries = listOf(
                MPV_RENDER_PARAM_API_TYPE to apiTypeMemory,
                MPV_RENDER_PARAM_SW_SIZE to sizeMemory,
                MPV_RENDER_PARAM_SW_FORMAT to formatMemory,
                MPV_RENDER_PARAM_SW_STRIDE to strideMemory,
                MPV_RENDER_PARAM_SW_POINTER to pixels,
            )
            val memory = Memory((entries.size + 1) * 16L)
            entries.forEachIndexed { index, (type, pointer) ->
                val base = index * 16L
                memory.setInt(base, type)
                memory.setPointer(base + 8, pointer)
            }
            memory.setInt(entries.size * 16L, MPV_RENDER_PARAM_INVALID)
            return memory
        }

        /** True when mpv has a decoded frame waiting to be painted. */
        fun needsRender(): Boolean {
            if (closed) return false
            val flags = callLong(mpvRenderContextUpdate, arrayOf<Any?>(context))
            return flags and MPV_RENDER_UPDATE_FRAME != 0L
        }

        /**
         * Paints the current frame and copies it into [frameBytes]. The returned array is reused
         * by the next call, so callers must consume it before rendering again.
         */
        fun render(): ByteArray? {
            if (closed) return null
            val code = callInt(mpvRenderContextRender, arrayOf<Any?>(context, renderParams))
            if (code != MPV_ERROR_NONE) return null
            pixels.read(0, frameBytes, 0, frameBytes.size)
            return frameBytes
        }

        override fun close() {
            if (closed) return
            closed = true
            callVoid(mpvRenderContextFree, arrayOf<Any?>(context))
        }
    }

    fun createSoftwareVideoSurface(width: Int, height: Int, format: String = "bgra"): SoftwareVideoSurface =
        SoftwareVideoSurface(width.coerceAtLeast(16), height.coerceAtLeast(16), format)

    fun setLogLevel(level: String?) {
        val safe = level ?: "warn"
        val code: Int = callInt(mpvRequestLogMessages, arrayOf<Any?>(handle, cstr(safe)))
        check(code == MPV_ERROR_NONE) { "mpv_request_log_messages($safe) failed with code $code: ${errorString(code)}" }
    }

    private fun eventLoop() {
        while (!stopRequested.get()) {
            val ev: Pointer = callPointer(mpvWaitEvent, arrayOf<Any?>(handle, -1.0))
            val eventId: Int = ev.getInt(0)
            when (eventId) {
                MPV_EVENT_SHUTDOWN -> {
                    dispatch(Event.Shutdown)
                    break
                }
                MPV_EVENT_LOG_MESSAGE -> dispatch(toLog(ev))
                MPV_EVENT_START_FILE -> dispatch(Event.StartFile)
                MPV_EVENT_FILE_LOADED -> dispatch(Event.FileLoaded)
                MPV_EVENT_END_FILE -> dispatch(toEndFile(ev))
                MPV_EVENT_PROPERTY_CHANGE -> dispatch(toPropertyChange(ev))
                MPV_EVENT_NONE -> Unit  
            }
        }
    }

    private fun toLog(ev: Pointer): Event {
        val data = ev.getPointer(16) ?: return Event.Log("", "error", "mpv returned an empty log event.")
        val prefix: String = data.getPointer(0)?.getString(0, "UTF-8") ?: ""
        val level: String = data.getPointer(8)?.getString(0, "UTF-8") ?: ""
        val text: String = data.getPointer(16)?.getString(0, "UTF-8") ?: ""
        return Event.Log(prefix = prefix, level = level, text = text)
    }

    private fun toEndFile(ev: Pointer): Event {
        val data = ev.getPointer(16)
        val reason = when (data?.getInt(0)) {
            0 -> EndReason.EOF
            2 -> EndReason.STOP
            3 -> EndReason.QUIT
            4 -> EndReason.ERROR
            5 -> EndReason.REDIRECT
            else -> EndReason.UNKNOWN
        }
        val errorCode: Int = data?.getInt(4) ?: 0
        val error: String? = if (reason == EndReason.ERROR) errorString(errorCode) else null
        return Event.EndFile(reason = reason, error = error, code = if (reason == EndReason.ERROR) errorCode else null)
    }

    private fun toPropertyChange(ev: Pointer): Event {
        val replyId: Long = ev.getLong(8)
        val data: Pointer? = ev.getPointer(16)
        val name: String = data?.getPointer(0)?.getString(0, "UTF-8") ?: ""
        val format: Int = data?.getInt(8) ?: MPV_FORMAT_NONE
        val valuePtr: Pointer? = data?.getPointer(16)
        val value: PropertyValue = when (format) {
            MPV_FORMAT_FLAG -> PropertyValue.Flag((valuePtr?.getInt(0) ?: 0) != 0)
            MPV_FORMAT_INT64 -> PropertyValue.Int64(valuePtr?.getLong(0) ?: 0L)
            MPV_FORMAT_DOUBLE -> PropertyValue.DoubleValue(valuePtr?.getDouble(0) ?: 0.0)
            MPV_FORMAT_STRING -> PropertyValue.Str(valuePtr?.getPointer(0)?.getString(0, "UTF-8") ?: "")
            else -> PropertyValue.None
        }
        val propertyName: String = observed[replyId] ?: name
        return Event.PropertyChange(name = propertyName, value = value)
    }

    private fun dispatch(event: Event) {
        for (handler in handlers) handler(event)
    }

    private fun formatCode(format: Format): Int = when (format) {
        Format.NONE -> MPV_FORMAT_NONE
        Format.STRING -> MPV_FORMAT_STRING
        Format.FLAG -> MPV_FORMAT_FLAG
        Format.INT64 -> MPV_FORMAT_INT64
        Format.DOUBLE -> MPV_FORMAT_DOUBLE
    }

    private fun setOption(name: String, value: String) {
        val code: Int = callInt(mpvSetOptionString, arrayOf<Any?>(handle, cstr(name), cstr(value)))
        check(code == MPV_ERROR_NONE) { "mpv option ${name}=${value} failed with code $code: ${errorString(code)}" }
    }

    private fun setProperty(name: String, value: String) {
        val code = callInt(mpvSetPropertyString, arrayOf<Any?>(handle, cstr(name), cstr(value)))
        check(code == MPV_ERROR_NONE) { "mpv property ${name}=${value} failed with code $code: ${errorString(code)}" }
    }

    private fun command(value: String) {
        val code = callInt(mpvCommandString, arrayOf<Any?>(handle, cstr(value)))
        check(code == MPV_ERROR_NONE) { "mpv command failed: ${errorString(code)}" }
    }

    private fun errorString(code: Int): String {
        val ptr: Pointer = callPointer(mpvErrorString, arrayOf<Any?>(code))
        return ptr?.getString(0, "UTF-8") ?: "unknown error ($code)"
    }

    @Suppress("UNCHECKED_CAST")
    private fun callInt(fn: Function, args: Array<Any?>): Int = fn.invokeInt(args)

    @Suppress("UNCHECKED_CAST")
    private fun callLong(fn: Function, args: Array<Any?>): Long = fn.invokeLong(args)

    @Suppress("UNCHECKED_CAST")
    private fun callPointer(fn: Function, args: Array<Any?>): Pointer = fn.invokePointer(args)

    private fun callVoid(fn: Function, args: Array<Any?>) = fn.invokeVoid(args)

    private fun cstr(s: String): Pointer {
        val mem = Memory(s.length + 1L)
        mem.setString(0, s, "UTF-8")
        return mem
    }

    private fun createHandle(): Pointer {
        val handle: Pointer = callPointer(mpvCreate, arrayOfNulls<Any?>(0))
        check(true) { "mpv_create returned null" }
        return handle
    }

    companion object {
        fun defaultLibraryPath(): String? {
            System.getProperty("AniLife.mpv.library")?.let { return it }
            System.getenv("ANILIFE_MPV_LIBRARY")?.takeIf(String::isNotBlank)?.let { return it }
            val candidates = mutableListOf<String>()
            val codeSource = Mpv::class.java.protectionDomain?.codeSource
            val sourcePath = codeSource?.location?.toString()
            if (sourcePath != null) {
                candidates.add(sourcePath)
                candidates.add(File(sourcePath).parent)
            }
            candidates.add(System.getProperty("user.dir") ?: ".")
            candidates.add("D:/AniLife")
            candidates.add("D:/Anilili")
            candidates.add("D:/lilibackup/Anilili")
            candidates.add(System.getProperty("user.home") ?: ".")
            candidates.addAll(listOf("/usr/lib/x86_64-linux-gnu", "/usr/lib64", "/usr/lib", "/usr/local/lib"))
            for (dir in candidates) {
                for (name in listOf("libmpv-2.dll", "libmpv.so.2", "libmpv.dylib")) {
                    val file = File(dir, name)
                    if (file.exists()) return file.absolutePath
                }
            }
            return null
        }
    }
}

fun defaultPlayerOptions(): List<Pair<String, String>> = listOf(
    "vo" to "libmpv",
    "hwdec" to "auto-safe",
    "volume" to "100",
    "idle" to "yes",
    "keep-open" to "yes",
    "background-color" to "#000000",
    "osc" to "no",
    "osd-level" to "0",
    "input-default-bindings" to "no",
    "input-vo-keyboard" to "no",
    "input-cursor" to "no",
    "cursor-autohide" to "no",
    "audio-exclusive" to "no",
    "terminal" to "no",
    "ytdl" to "no",
    "user-agent" to "AniLife/1.0",
    "video-sync" to "audio",
    "hr-seek" to "yes",
    "cache" to "yes",
    "cache-secs" to "120",
    "demuxer-max-bytes" to "150MiB",
    "demuxer-max-back-bytes" to "50MiB",
    "network-timeout" to "30",
    "sub-ass-force-margins" to "yes",
    "sub-use-margins" to "yes",
    "sub-font-size" to "44",
    "sub-border-size" to "2.5",
)

