package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.mpv.Mpv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.ZipInputStream

/** Downloads libmpv on first launch when it isn't already available. */
object MpvBootstrap {
    private const val RELEASE_API = "https://api.github.com/repos/mpv-player/mpv/releases/latest"
    private const val USER_AGENT = "AniLife/1.0"

    /** Returns the path to a usable libmpv, downloading one if necessary. */
    suspend fun ensure(onStatus: (String) -> Unit = {}): String? {
        Mpv.defaultLibraryPath()?.let { return it }
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
            onStatus("libmpv not found")
            return null
        }
        val targetDir = File(System.getProperty("user.home"), ".anilife").apply { mkdirs() }
        val target = File(targetDir, "libmpv-2.dll")
        if (target.exists() && target.length() > 0) {
            System.setProperty("AniLife.mpv.library", target.absolutePath)
            onStatus("mpv ready")
            return target.absolutePath
        }
        onStatus("mpv not found; downloading…")
        return try {
            withContext(Dispatchers.IO) {
                val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
                val releaseJson = client.send(
                    HttpRequest.newBuilder(URI.create(RELEASE_API))
                        .header("User-Agent", USER_AGENT).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                ).body()
                val assetUrl = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]*libmpv-[^\"]*x86_64-w64-mingw32-lgpl\\.zip)\"")
                    .find(releaseJson)?.groupValues?.get(1)
                    ?: return@withContext null
                onStatus("fetching libmpv…")
                val zipBytes = client.send(
                    HttpRequest.newBuilder(URI.create(assetUrl)).header("User-Agent", USER_AGENT).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray(),
                ).body()
                onStatus("extracting…")
                ZipInputStream(zipBytes.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith("libmpv-2.dll")) {
                            target.outputStream().use { out -> zis.copyTo(out) }
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
                if (target.exists() && target.length() > 0) {
                    System.setProperty("AniLife.mpv.library", target.absolutePath)
                    onStatus("mpv ready")
                    target.absolutePath
                } else null
            }
        } catch (e: Throwable) {
            onStatus("mpv download failed: ${e.message}")
            null
        }
    }
}
