package me.enivzoy.anilife.app.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import me.enivzoy.anilife.core.model.Update
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdaterLoggingTest {
    @Test
    fun installCommandUsesVerboseMsiLogAndQuietExe() {
        val root = Files.createTempDirectory("updater-command-test")
        try {
            val msi = root.resolve("folder with spaces").resolve("anilife.msi")
            val exe = root.resolve("folder with spaces").resolve("anilife.exe")
            val log = root.resolve("updater-msi.log")
            assertEquals(
                "msiexec.exe /i \"${msi}\" /qn /norestart /L*V \"${log}\"",
                installCommand(msi, log),
            )
            assertEquals("\"${exe}\" /quiet /norestart", installCommand(exe, log))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun downloadReportsMonotonicProgressAndThrowsTypedFailures() = runBlocking {
        val payload = ByteArray(256 * 1024) { (it % 251).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/installer.exe") { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.createContext("/missing.exe") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        val root = Files.createTempDirectory("updater-download-test")
        try {
            val updater = Updater(HttpClient.newHttpClient())
            val url = "http://127.0.0.1:${server.address.port}/installer.exe"
            val destination = root.resolve("anilife.exe")
            val progress = mutableListOf<Float>()
            assertTrue(
                updater.download(
                    Update("1.1.0", "", url, payload.size.toLong(), hash),
                    destination,
                ) { bytes, total -> progress += if (total != null && total > 0L) bytes * 100f / total else 0f },
            )
            assertTrue(progress.isNotEmpty())
            assertEquals(progress, progress.sorted())
            assertEquals(100f, progress.last())

            val checksum = assertFailsWith<ChecksumException> {
                updater.download(Update("1.1.0", "", url, payload.size.toLong(), "0000"), destination)
            }
            assertTrue(checksum.message.orEmpty().contains("0000"))
            assertTrue(checksum.message.orEmpty().contains(hash))

            assertFailsWith<SizeMismatchException> {
                updater.download(Update("1.1.0", "", url, payload.size + 5L, hash), destination)
            }
            val http = assertFailsWith<UpdateHttpException> {
                updater.download(
                    Update("1.1.0", "", "http://127.0.0.1:${server.address.port}/missing.exe", 0, ""),
                    destination,
                )
            }
            assertEquals(404, http.status)
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun manifestFetchReportsRateLimitAndUsesFallbackAfterRefusal() = runBlocking {
        val limited = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        limited.createContext("/latest") { exchange ->
            exchange.responseHeaders.set("X-RateLimit-Remaining", "0")
            exchange.responseHeaders.set("X-RateLimit-Reset", (Instant.now().epochSecond + 120).toString())
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        limited.start()
        val limitedUrl = "http://127.0.0.1:${limited.address.port}/latest"
        try {
            val rateLimited = Updater(HttpClient.newHttpClient(), limitedUrl, limitedUrl)
            val failure = assertFailsWith<UpdateException> { rateLimited.check("1.0.0") }
            assertTrue(failure.message.orEmpty().contains("rate limit", ignoreCase = true))
            assertTrue(failure.message.orEmpty().contains("Retry after"))
        } finally {
            limited.stop(0)
        }

        val deadPort = 1
        val fallback = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fallback.createContext("/latest") { exchange ->
            val body = """{"version":"1.0.2","url":"http://127.0.0.1:${fallback.address.port}/app.exe","sizeBytes":1}"""
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        fallback.start()
        try {
            val updater = Updater(
                HttpClient.newHttpClient(),
                "http://127.0.0.1:$deadPort/latest",
                "http://127.0.0.1:${fallback.address.port}/latest",
            )
            assertEquals("1.0.2", updater.check("1.0.0")?.version)
        } finally {
            fallback.stop(0)
        }
    }

    @Test
    fun previousInstallMarkerIsReadAndRemovedFromTempHome() {
        val originalHome = System.getProperty("user.home")
        val root = Files.createTempDirectory("updater-marker-test")
        try {
            System.setProperty("user.home", root.toString())
            val logs = Files.createDirectories(
                root.resolve("AppData").resolve("Roaming").resolve("AniLife").resolve("logs"),
            )
            val marker = logs.resolve("updater-result.txt")
            Files.writeString(marker, "3010")
            Updater.reportPreviousInstall()
            assertFalse(Files.exists(marker))
            Files.writeString(marker, "invalid")
            Updater.reportPreviousInstall()
            assertFalse(Files.exists(marker))
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home")
            } else {
                System.setProperty("user.home", originalHome)
            }
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun redactionHidesHomePrefixAndUrlQueries() {
        val home = System.getProperty("user.home")
        val path = Path.of(home, "AppData", "logs").toString()
        assertEquals(Path.of("~", "AppData", "logs").toString(), redactForLog(path))
        assertEquals("https://github.com/x/y", redactForLog("https://github.com/x/y?token=secret"))
    }

    @Test
    fun updateStateBusyOnlyWhileDownloadingOrInstalling() {
        assertTrue(UpdateState.Downloading(0.42f).busy)
        assertTrue(UpdateState.Installing.busy)
        assertFalse(UpdateState.Idle.busy)
        assertFalse(UpdateState.Failed("failed").busy)
    }
}
