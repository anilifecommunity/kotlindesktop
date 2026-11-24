package me.enivzoy.anilife.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

internal const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AniLifeSetup/1.0"
internal const val ADOPTIUM_API =
    "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse"
internal const val MPV_RELEASES = "https://api.github.com/repos/shinchiro/mpv-winbuild-cmake/releases/latest"
internal const val FFMPEG_RELEASES = "https://api.github.com/repos/BtbN/FFmpeg-Builds/releases/latest"
internal const val UPSCALER_ARCHIVE =
    "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-ncnn-vulkan-20220424-windows.zip"
internal const val UPSCALER_SHA256 = "abc02804e17982a3be33675e4d471e91ea374e65b70167abc09e31acb412802d"
private const val UPSCALER_VERSION = "v0.2.5.0"

enum class Dependency { JRE, MPV, FFMPEG, UPSCALER }

enum class DependencyStatus { Pending, Downloading, Verifying, Extracting, Installed, Failed }

data class DependencyState(
    val dependency: Dependency,
    val status: DependencyStatus = DependencyStatus.Pending,
    val progress: Float = 0f,
    val bytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    val error: String? = null,
)

internal data class DependencySource(
    val url: String,
    val sha256: String,
    val version: String,
)

fun dependencyDirectory(): File {
    val base = System.getenv("APPDATA")?.takeIf(String::isNotBlank)?.let(::File)
        ?: File(System.getProperty("user.home"), ".anilife")
    return File(base, "bin")
}

internal fun releaseAssetPattern(dependency: Dependency): Regex = when (dependency) {
    Dependency.MPV -> Regex("^mpv-dev-x86_64-\\d{8}-git-[0-9a-f]+\\.7z$")
    Dependency.FFMPEG -> Regex("^ffmpeg-master-latest-win64-gpl\\.zip$")
    else -> error("$dependency has no release asset pattern.")
}

internal fun upscalerSource(): DependencySource =
    DependencySource(UPSCALER_ARCHIVE, UPSCALER_SHA256, UPSCALER_VERSION)

internal fun fetchText(url: String): String {
    val connection = openConnection(url, api = true, followRedirects = true, connectMs = 30_000, readMs = 30_000)
    return try {
        requireSuccess(connection)
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

internal fun openConnection(
    url: String,
    api: Boolean,
    followRedirects: Boolean,
    connectMs: Int,
    readMs: Int,
): HttpURLConnection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
    instanceFollowRedirects = followRedirects
    connectTimeout = connectMs
    readTimeout = readMs
    setRequestProperty("User-Agent", USER_AGENT)
    setRequestProperty("Accept", if (api) "application/vnd.github+json" else "application/octet-stream")
    if (api) setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
}

internal fun requireSuccess(connection: HttpURLConnection) {
    val code = connection.responseCode
    if (code !in 200..299) {
        val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        error("Request failed ($code): ${body.take(240).ifBlank { connection.url.host }}")
    }
}

class DependencyManager(
    private val scope: CoroutineScope,
    private val localDir: File,
) {
    private val _states = MutableStateFlow(
        Dependency.entries.associateWith { DependencyState(it) },
    )
    val states: StateFlow<Map<Dependency, DependencyState>> = _states.asStateFlow()

    private var installJob: Job? = null
    private val jobs = mutableMapOf<Dependency, Job>()
    private var callback: (Dependency, Float) -> Unit = { _, _ -> }

    val installDir: Path = localDir.toPath()
    val jreDir: Path = installDir.resolve("jre")
    val mpvDir: Path = installDir.resolve("mpv")
    val ffmpegDir: Path = installDir.resolve("ffmpeg")
    val upscalerDir: Path = installDir.resolve("upscaler")

    fun isSetupComplete(): Boolean = !isWindows || Dependency.entries.all(::isInstalled)

    fun start() {
        if (isSetupComplete() || installJob?.isActive == true) return
        installJob = scope.launch {
            downloadAndInstall(callback).onFailure { error ->
                Dependency.entries.filter { currentState(it).status != DependencyStatus.Installed }
                    .forEach { fail(it, error) }
            }
        }
    }

    fun retryAll() {
        installJob?.cancel()
        installJob = null
        start()
    }

    fun retry(dependency: Dependency) {
        if (jobs[dependency]?.isActive == true) return
        jobs[dependency] = scope.launch {
            runCatching { install(dependency, callback) }
                .onSuccess { configurePaths() }
                .onFailure { error -> fail(dependency, error) }
        }
    }

    suspend fun downloadAndInstall(onProgress: (Dependency, Float) -> Unit): Result<Unit> {
        callback = onProgress
        if (!isWindows) {
            Dependency.entries.forEach { dependency ->
                state(dependency) { copy(status = DependencyStatus.Installed, progress = 1f) }
            }
            configurePaths()
            return Result.success(Unit)
        }
        return runCatching {
            Dependency.entries.forEach { dependency ->
                if (isInstalled(dependency)) {
                    state(dependency) { copy(status = DependencyStatus.Installed, progress = 1f, error = null) }
                } else {
                    install(dependency, onProgress)
                }
            }
            configurePaths()
        }
    }

    fun configurePaths() {
        System.setProperty("AniLife.ffmpeg.dir", ffmpegDir.toString())
        System.setProperty("AniLife.jre.home", jreDir.toString())
        System.setProperty("AniLife.upscaler.dir", upscalerDir.toString())
        findLibrary(mpvDir, "libmpv-2.dll")?.let { System.setProperty("AniLife.mpv.library", it.toString()) }
        if (isWindows && isInstalled(Dependency.JRE)) {
            installDir.resolve("use-jre").toFile().writeText("1")
        }
    }

    private fun isInstalled(dependency: Dependency): Boolean {
        val marker = installDir.resolve("${dependency.name.lowercase()}.installed")
        if (!Files.isRegularFile(marker) || marker.toFile().length() == 0L) return false
        val binaries = when (dependency) {
            Dependency.JRE -> listOf(jreDir.resolve("bin/java.exe"))
            Dependency.MPV -> listOf(mpvDir.resolve("libmpv-2.dll"))
            Dependency.FFMPEG -> listOf(ffmpegDir.resolve("ffmpeg.exe"), ffmpegDir.resolve("ffprobe.exe"))
            Dependency.UPSCALER -> listOf(
                upscalerDir.resolve("realesrgan-ncnn-vulkan.exe"),
                upscalerDir.resolve("models/realesr-animevideov3-x4.param"),
                upscalerDir.resolve("models/realesr-animevideov3-x4.bin"),
            )
        }
        return binaries.all(Files::isRegularFile)
    }

    private suspend fun install(dependency: Dependency, onProgress: (Dependency, Float) -> Unit) {
        state(dependency) {
            copy(status = DependencyStatus.Downloading, progress = 0f, error = null, bytes = 0L, totalBytes = 0L)
        }
        val source = resolveSource(dependency)
        val archive = Files.createTempFile("anilife-${dependency.name.lowercase()}-", archiveSuffix(source.url))
        try {
            download(source, archive, dependency, onProgress)
            state(dependency) { copy(status = DependencyStatus.Extracting, progress = 1f) }
            Files.createDirectories(installDir)
            val target = when (dependency) {
                Dependency.JRE -> jreDir
                Dependency.MPV -> mpvDir
                Dependency.FFMPEG -> ffmpegDir
                Dependency.UPSCALER -> upscalerDir
            }
            extract(archive, target, dependency)
            installDir.resolve("${dependency.name.lowercase()}.installed").toFile()
                .writeText("${source.version}|${source.sha256}")
            state(dependency) { copy(status = DependencyStatus.Installed, progress = 1f) }
        } catch (error: Throwable) {
            fail(dependency, error)
            throw error
        } finally {
            Files.deleteIfExists(archive)
        }
    }

    private fun resolveSource(dependency: Dependency): DependencySource = when (dependency) {
        Dependency.JRE -> parseJreSource(fetchText(ADOPTIUM_API))
        Dependency.MPV -> parseReleaseSource(fetchText(MPV_RELEASES), releaseAssetPattern(Dependency.MPV))
        Dependency.FFMPEG -> parseReleaseSource(fetchText(FFMPEG_RELEASES), releaseAssetPattern(Dependency.FFMPEG))
        Dependency.UPSCALER -> upscalerSource()
    }

    private suspend fun download(
        source: DependencySource,
        target: Path,
        dependency: Dependency,
        onProgress: (Dependency, Float) -> Unit,
    ) {
        val connection = openConnection(source.url, api = false, followRedirects = true, connectMs = 30_000, readMs = 60_000)
        val digest = MessageDigest.getInstance("SHA-256")
        val total = connection.contentLength.toLong().coerceAtLeast(0L)
        var copied = 0L
        var lastTime = System.nanoTime()
        var lastBytes = 0L
        try {
            requireSuccess(connection)
            DigestInputStream(BufferedInputStream(connection.inputStream), digest).use { input ->
                Files.newOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (!coroutineContext.isActive) error("Dependency download was cancelled.")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val now = System.nanoTime()
                        if (now - lastTime >= 200_000_000L || total > 0 && copied == total) {
                            val fraction = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else 0f
                            val speed = (copied - lastBytes) * 1_000_000_000L / (now - lastTime).coerceAtLeast(1L)
                            update(dependency) {
                                copy(
                                    status = DependencyStatus.Downloading,
                                    progress = fraction,
                                    bytes = copied,
                                    totalBytes = total,
                                    speedBps = speed,
                                )
                            }
                            onProgress(dependency, fraction)
                            lastTime = now
                            lastBytes = copied
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(source.sha256, ignoreCase = true)) {
            error("${dependency.name} checksum mismatch. Expected ${source.sha256.take(12)}…, got ${actual.take(12)}…")
        }
        state(dependency) {
            copy(status = DependencyStatus.Verifying, progress = 1f, bytes = copied, totalBytes = total)
        }
    }

    private fun extract(archive: Path, target: Path, dependency: Dependency) {
        when (dependency) {
            Dependency.JRE -> extractArchive(
                archive,
                target,
                name = { it.substringAfter('/', "") },
                keep = { it.isNotBlank() },
            )
            Dependency.MPV -> extractArchive(
                archive,
                target,
                name = { it.substringAfterLast('/') },
                keep = { it.equals("libmpv-2.dll", ignoreCase = true) },
            )
            Dependency.FFMPEG -> extractArchive(
                archive,
                target,
                name = { it.substringAfter("/bin/", "") },
                keep = {
                    it.isNotBlank() && !it.contains('/') &&
                        (it.endsWith(".exe", ignoreCase = true) || it.endsWith(".dll", ignoreCase = true))
                },
            )
            Dependency.UPSCALER -> extractArchive(archive, target, name = { it }, keep = { true })
        }
    }

    private fun archiveSuffix(url: String): String {
        val name = url.substringBefore('?').substringAfterLast('/')
        val extension = name.substringAfterLast('.', "").lowercase()
        return if (extension == "7z" || extension == "zip") ".$extension" else ".zip"
    }

    private fun findLibrary(root: Path, name: String): Path? = Files.walk(root).use { paths ->
        paths.filter { it.fileName.toString().equals(name, ignoreCase = true) }.findFirst().orElse(null)
    }

    private fun update(dependency: Dependency, change: DependencyState.() -> DependencyState) {
        _states.update { current -> current + (dependency to requireNotNull(current[dependency]).change()) }
    }

    private fun state(dependency: Dependency, change: DependencyState.() -> DependencyState) =
        update(dependency, change)

    private fun currentState(dependency: Dependency): DependencyState = requireNotNull(_states.value[dependency])

    private fun fail(dependency: Dependency, error: Throwable) {
        val message = error.message?.takeIf(String::isNotBlank) ?: "Setup failed."
        update(dependency) { copy(status = DependencyStatus.Failed, error = message) }
    }

    private val isWindows: Boolean get() = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
}

internal fun extractArchive(
    archive: Path,
    target: Path,
    name: (String) -> String,
    keep: (String) -> Boolean,
) {
    val root = target.toAbsolutePath().normalize()
    Files.createDirectories(root)
    if (archive.fileName.toString().endsWith(".7z", ignoreCase = true)) {
        extractSevenZip(archive, root, name, keep)
    } else {
        ZipInputStream(BufferedInputStream(Files.newInputStream(archive))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val destination = destination(root, entry.name, name, keep) ?: continue
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING)
                zip.closeEntry()
            }
        }
    }
}

private fun extractSevenZip(
    archive: Path,
    root: Path,
    name: (String) -> String,
    keep: (String) -> Boolean,
) {
    SevenZip.initSevenZipFromPlatformJAR()
    RandomAccessFile(archive.toFile(), "r").use { file ->
        SevenZip.openInArchive(null, RandomAccessFileInStream(file)).use { inArchive ->
            inArchive.simpleInterface.archiveItems
                .filter { !it.isFolder }
                .forEach { item -> extractSevenZipItem(item, root, name, keep) }
        }
    }
}

private fun extractSevenZipItem(
    item: ISimpleInArchiveItem,
    root: Path,
    name: (String) -> String,
    keep: (String) -> Boolean,
) {
    val destination = destination(root, item.path.orEmpty(), name, keep) ?: return
    Files.newOutputStream(destination).use { output ->
        val result = item.extractSlow { bytes ->
            output.write(bytes)
            bytes.size
        }
        check(result == ExtractOperationResult.OK) { "Failed to extract ${item.path}: $result" }
    }
}

private fun destination(
    root: Path,
    rawName: String,
    name: (String) -> String,
    keep: (String) -> Boolean,
): Path? {
    val normalized = rawName.replace('\\', '/').removePrefix("./")
    val relative = name(normalized).replace('\\', '/').removePrefix("./").trimStart('/')
    if (relative.isBlank() || !keep(relative)) return null
    val target = root.resolve(relative).normalize()
    if (target == root || !target.startsWith(root)) return null
    Files.createDirectories(target.parent)
    return target
}

internal fun parseJreSource(body: String): DependencySource {
    val releases = Json.parseToJsonElement(body) as? JsonArray ?: error("JRE release list is invalid.")
    val release = releases.firstOrNull() as? JsonObject ?: error("No JRE release was returned.")
    val binary = release["binary"] as? JsonObject ?: error("JRE release has no binary metadata.")
    val packageInfo = binary["package"] as? JsonObject ?: error("JRE release has no downloadable package.")
    val version = release["version"] as? JsonObject
    return DependencySource(
        url = packageInfo.text("link") ?: error("JRE download URL is missing."),
        sha256 = packageInfo.text("checksum") ?: error("JRE checksum is missing."),
        version = version?.text("semver") ?: version?.text("openjdk_version") ?: "25",
    )
}

internal fun parseReleaseSource(body: String, pattern: Regex): DependencySource {
    val release = Json.parseToJsonElement(body) as? JsonObject ?: error("Release metadata is invalid.")
    val assets = release["assets"] as? JsonArray ?: error("Release has no assets.")
    val asset = assets.mapNotNull { it as? JsonObject }
        .firstOrNull { pattern.matches(it.text("name").orEmpty()) }
        ?: error("No matching release asset was found.")
    return DependencySource(
        url = asset.text("browser_download_url") ?: error("Asset download URL is missing."),
        sha256 = asset.text("digest")?.removePrefix("sha256:") ?: error("Asset checksum is missing."),
        version = release.text("tag_name") ?: "latest",
    )
}

private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
