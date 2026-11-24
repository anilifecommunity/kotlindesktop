package me.enivzoy.anilife.app.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DependencyManagerTest {
    @Test
    fun usesWindowsUserAgent() {
        assertTrue(USER_AGENT.contains("Windows NT"))
        assertTrue(USER_AGENT.startsWith("Mozilla/"))
    }

    @Test
    fun usesOfficialDependencyEndpoints() {
        assertEquals(
            "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse",
            ADOPTIUM_API,
        )
        assertEquals("https://api.github.com/repos/shinchiro/mpv-winbuild-cmake/releases/latest", MPV_RELEASES)
        assertEquals("https://api.github.com/repos/BtbN/FFmpeg-Builds/releases/latest", FFMPEG_RELEASES)
    }

    @Test
    fun fetchTextFollowsRedirects() {
        val body = """{"tag_name":"latest","assets":[]}"""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        server.createContext("/start") { exchange ->
            exchange.responseHeaders.set("Location", "http://127.0.0.1:$port/target")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/target") { exchange ->
            respond(exchange, 200, body)
        }
        server.start()
        try {
            assertEquals(body, fetchText("http://127.0.0.1:$port/start"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchTextRejectsNonSuccessStatus() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        server.createContext("/missing") { exchange ->
            respond(exchange, 404, "not found")
        }
        server.start()
        try {
            val error = assertFailsWith<IllegalStateException> { fetchText("http://127.0.0.1:$port/missing") }
            assertTrue(error.message.orEmpty().contains("404"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun parsesJreSourceFromAdoptiumResponse() {
        val body = """
            [{
              "version": {"semver": "25.0.5+100000002.0.LTS"},
              "binary": {"package": {
                "link": "https://example.com/jre.zip",
                "checksum": "abc123"
              }}
            }]
        """.trimIndent()

        val source = parseJreSource(body)

        assertEquals("https://example.com/jre.zip", source.url)
        assertEquals("abc123", source.sha256)
        assertEquals("25.0.5+100000002.0.LTS", source.version)
    }

    @Test
    fun parsesMpvSevenZipReleaseAsset() {
        val body = """
            {
              "tag_name": "20261111",
              "assets": [
                {"name": "mpv-i686-20261111-git-abcdef0.7z",
                 "browser_download_url": "https://example.com/mpv-i686.7z",
                 "digest": "sha256:aaa111"},
                {"name": "mpv-dev-x86_64-20261111-git-abcdef0.7z",
                 "browser_download_url": "https://example.com/mpv-dev-x86_64.7z",
                 "digest": "sha256:def456"}
              ]
            }
        """.trimIndent()

        val source = parseReleaseSource(body, releaseAssetPattern(Dependency.MPV))

        assertEquals("https://example.com/mpv-dev-x86_64.7z", source.url)
        assertEquals("def456", source.sha256)
        assertEquals("20261111", source.version)
    }

    @Test
    fun parsesFfmpegReleaseAsset() {
        val body = """
            {
              "tag_name": "latest",
              "assets": [
                {"name": "ffmpeg-master-latest-win64-gpl-shared.zip",
                 "browser_download_url": "https://example.com/shared.zip",
                 "digest": "sha256:aaa111"},
                {"name": "ffmpeg-master-latest-win64-gpl.zip",
                 "browser_download_url": "https://example.com/ffmpeg.zip",
                 "digest": "sha256:def456"}
              ]
            }
        """.trimIndent()

        val source = parseReleaseSource(body, releaseAssetPattern(Dependency.FFMPEG))

        assertEquals("https://example.com/ffmpeg.zip", source.url)
        assertEquals("def456", source.sha256)
        assertEquals("latest", source.version)
    }

    @Test
    fun recognizesInstalledUpscalerScaleModels() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val directory = Files.createTempDirectory("anilife-dependencies-")
        try {
            val files = listOf(
                "jre/bin/java.exe",
                "mpv/libmpv-2.dll",
                "ffmpeg/ffmpeg.exe",
                "ffmpeg/ffprobe.exe",
                "upscaler/realesrgan-ncnn-vulkan.exe",
                "upscaler/models/realesr-animevideov3-x4.param",
                "upscaler/models/realesr-animevideov3-x4.bin",
            )
            files.forEach { relative ->
                val file = directory.resolve(relative)
                Files.createDirectories(file.parent)
                Files.writeString(file, "installed")
            }
            Dependency.entries.forEach { dependency ->
                Files.writeString(directory.resolve("${dependency.name.lowercase()}.installed"), "version|sha256")
            }
            val manager = DependencyManager(CoroutineScope(Dispatchers.Unconfined), directory.toFile())

            assertTrue(manager.isSetupComplete())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun marksAlreadyInstalledDependenciesReady() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val directory = Files.createTempDirectory("anilife-dependencies-")
        try {
            val files = listOf(
                "jre/bin/java.exe",
                "mpv/libmpv-2.dll",
                "ffmpeg/ffmpeg.exe",
                "ffmpeg/ffprobe.exe",
                "upscaler/realesrgan-ncnn-vulkan.exe",
                "upscaler/models/realesr-animevideov3-x4.param",
                "upscaler/models/realesr-animevideov3-x4.bin",
            )
            files.forEach { relative ->
                val file = directory.resolve(relative)
                Files.createDirectories(file.parent)
                Files.writeString(file, "installed")
            }
            Dependency.entries.forEach { dependency ->
                Files.writeString(directory.resolve("${dependency.name.lowercase()}.installed"), "version|sha256")
            }
            val manager = DependencyManager(CoroutineScope(Dispatchers.Unconfined), directory.toFile())

            val result = runBlocking { manager.downloadAndInstall { _, _ -> } }

            assertTrue(result.isSuccess)
            Dependency.entries.forEach { dependency ->
                val state = requireNotNull(manager.states.value[dependency])
                assertEquals(DependencyStatus.Installed, state.status)
                assertEquals(1f, state.progress)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun usesOfficialUpscalerRelease() {
        val source = upscalerSource()

        assertEquals(
            "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-ncnn-vulkan-20220424-windows.zip",
            source.url,
        )
        assertEquals(UPSCALER_SHA256, source.sha256)
        assertEquals("v0.2.5.0", source.version)
    }
}

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
