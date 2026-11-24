package me.enivzoy.anilife.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

private const val MODEL = "realesr-animevideov3"
private const val MODEL_SCALE = 4
private const val LINUX_ARCHIVE = "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-ncnn-vulkan-20220424-ubuntu.zip"
private const val MAC_ARCHIVE = "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-ncnn-vulkan-20220424-macos.zip"

class AnimeUpscaler {
    suspend fun upscale(episode: LocalEpisode, onProgress: (Float) -> Unit): Path =
        withContext(Dispatchers.IO) {
            val input = episode.path
            require(Files.isRegularFile(input)) { "Episode file not found: ${input}" }
            val tools = prepareTools(onProgress)
            val fps = detectFps(tools.ffprobe, input)
            val report = { fraction: Float -> onProgress((0.05f + 0.95f * fraction).coerceIn(0f, 1f)) }
            val temporary = Files.createTempDirectory("anilife-upscale-")
            try {
                val frames = Files.createDirectory(temporary.resolve("frames"))
                val upscaled = Files.createDirectory(temporary.resolve("upscaled"))
                report(0.05f)
                run(
                    listOf(
                        tools.ffmpeg, "-y", "-i", input.toString(),
                        "-qscale:v", "1", "-qmin", "1", "-qmax", "1", "-vsync", "0",
                        frames.resolve("frame%08d.png").toString(),
                    ),
                )
                checkActive()
                report(0.15f)
                val total = countFrames(frames)
                require(total > 0) { "ffmpeg didn't extract any frames." }
                run(
                    listOf(
                        tools.upscaler, "-i", frames.toString(), "-o", upscaled.toString(),
                        "-n", MODEL, "-s", MODEL_SCALE.toString(), "-f", "png",
                    ),
                ) {
                    val current = countFrames(upscaled)
                    report(0.15f + 0.75f * (current.toFloat() / total).coerceIn(0f, 1f))
                    delay(300)
                }
                checkActive()
                val written = countFrames(upscaled)
                require(written == total) { "Upscaled $written of $total frames." }
                report(0.9f)
                val output = outputPath(input)
                run(
                    listOf(
                        tools.ffmpeg, "-y", "-framerate", fps,
                        "-i", upscaled.resolve("frame%08d.png").toString(),
                        "-i", input.toString(), "-map", "0:v:0", "-map", "1:a?",
                        "-c:v", "libx264", "-crf", "18", "-pix_fmt", "yuv420p",
                        "-c:a", "copy", output.toString(),
                    ),
                )
                checkActive()
                report(1f)
                output
            } finally {
                deleteRecursively(temporary)
            }
        }

    private suspend fun prepareTools(onProgress: (Float) -> Unit): Tools {
        val ffmpeg = findTool("ffmpeg")
            ?: error("ffmpeg wasn't found. Install ffmpeg or add it to PATH.")
        val ffprobe = findTool("ffprobe")
            ?: error("ffprobe wasn't found. Install ffmpeg or add it to PATH.")
        val upscaler = findUpscaler(onProgress)
        return Tools(ffmpeg, ffprobe, upscaler)
    }

    private suspend fun findUpscaler(onProgress: (Float) -> Unit): String {
        val configured = System.getProperty("AniLife.upscaler.dir")?.takeIf(String::isNotBlank)?.let { Path.of(it) }
        val directory = configured ?: dependencyDirectory().resolve("upscaler").toPath()
        Files.createDirectories(directory)
        val executable = if (isWindows) "$UPSCALER.exe" else UPSCALER
        val installed = directory.resolve(executable)

        if (Files.isRegularFile(installed)) return installed.toString()
        javaClass.getResourceAsStream("/upscaler/$executable")?.use { source ->
            Files.copy(source, installed, StandardCopyOption.REPLACE_EXISTING)
            installed.toFile().setExecutable(true, false)
        }
        if (Files.isRegularFile(installed)) {
            Files.createDirectories(directory.resolve("models"))
            listOf("$MODEL-x$MODEL_SCALE.param", "$MODEL-x$MODEL_SCALE.bin").forEach { name ->
                javaClass.getResourceAsStream("/upscaler/models/$name")?.use { source ->
                    Files.copy(source, directory.resolve("models").resolve(name), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            return installed.toString()
        }
        findTool(UPSCALER)?.let { return it }

        val archiveUrl = when {
            isWindows -> UPSCALER_ARCHIVE
            isMac -> MAC_ARCHIVE
            else -> LINUX_ARCHIVE
        }
        val archive = Files.createTempFile("anilife-upscaler-", ".zip")
        try {
            download(archiveUrl, archive) { onProgress(it * 0.05f) }
            unzip(archive, directory)
        } finally {
            Files.deleteIfExists(archive)
        }
        require(Files.isRegularFile(installed)) { "The upscaler download didn't contain $executable." }
        installed.toFile().setExecutable(true, false)
        require(Files.isRegularFile(directory.resolve("models").resolve("$MODEL-x$MODEL_SCALE.param"))) {
            "The upscaler download didn't contain the $MODEL-x$MODEL_SCALE model."
        }
        return installed.toString()
    }

    private suspend fun download(url: String, target: Path, onProgress: (Float) -> Unit) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/octet-stream")
        try {
            connection.inputStream.use { input ->
                Files.newOutputStream(target).use { output ->
                    val total = connection.contentLength.toLong()
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        checkActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun unzip(archive: Path, root: Path) {
        val destination = root.toAbsolutePath().normalize()
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                val target = destination.resolve(entry.name).normalize()
                if (!target.startsWith(destination)) continue
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
                    target.toFile().setExecutable(true, false)
                }
                zip.closeEntry()
            }
        }
    }

    private suspend fun detectFps(ffprobe: String, input: Path): String {
        val output = capture(
            listOf(
                ffprobe, "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=r_frame_rate,avg_frame_rate",
                "-of", "default=nw=1:nk=1", input.toString(),
            ),
        )
        val rate = output.lineSequence().mapNotNull(::parseRate).firstOrNull() ?: 24.0
        return rate.toString()
    }

    private suspend fun run(command: List<String>, tick: (suspend () -> Unit)? = null) {
        checkActive()
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        try {
            while (process.isAlive) {
                checkActive()
                tick?.invoke()
                delay(300)
            }
            val exit = process.waitFor()
            checkActive()
            require(exit == 0) { "${command.firstOrNull()} exited with code $exit." }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private suspend fun capture(command: List<String>): String {
        checkActive()
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        return try {
            val output = process.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            val exit = process.waitFor()
            checkActive()
            require(exit == 0) { "${command.firstOrNull()} exited with code $exit." }
            output
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private suspend fun checkActive() {
        if (!coroutineContext.isActive) throw CancellationException("upscale cancelled")
    }

    private fun findTool(name: String): String? {
        val executable = if (isWindows) "$name.exe" else name
        val directories = listOfNotNull(
            System.getProperty("AniLife.ffmpeg.dir"),
        ) + System.getenv("PATH")?.split(File.pathSeparator).orEmpty() +
            listOf("/usr/bin", "/usr/local/bin", "C:\\ffmpeg\\bin", "C:\\tools\\ffmpeg\\bin")
        return directories.map { File(it, executable) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }

    private fun countFrames(directory: Path): Long =
        Files.list(directory).use { entries ->
            entries.filter { it.fileName.toString().endsWith(".png", ignoreCase = true) }.count()
        }

    private fun outputPath(input: Path): Path {
        val name = input.fileName.toString()
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ".mp4"
        val parent = input.parent ?: Path.of(".")
        var candidate = parent.resolve("${base}_4k$extension")
        var index = 2
        while (Files.exists(candidate)) {
            candidate = parent.resolve("${base}_4k_$index$extension")
            index++
        }
        return candidate
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { entries ->
            entries.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private data class Tools(val ffmpeg: String, val ffprobe: String, val upscaler: String)

    private companion object {
        const val UPSCALER = "realesrgan-ncnn-vulkan"
        val isWindows: Boolean get() = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val isMac: Boolean get() = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

        fun parseRate(value: String): Double? {
            val parts = value.trim().split('/')
            val numerator = parts.firstOrNull()?.toDoubleOrNull() ?: return null
            val denominator = parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0
            return (numerator / denominator).takeIf { it.isFinite() && it > 0 }
        }
    }
}
