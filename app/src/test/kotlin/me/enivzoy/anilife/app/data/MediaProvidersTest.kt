package me.enivzoy.anilife.app.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MediaProvidersTest {
    @Test
    fun serverConfigurationPersistsWithoutCredentials() {
        val directory = Files.createTempDirectory("AniLife-providers")
        try {
            val file = directory.resolve("providers.properties").toFile()
            val store = MediaProviderStore(file)
            val server = MediaProviderServer(
                id = "server-id",
                name = "Home media",
                address = "https://media.example.test",
                username = "viewer",
            )

            store.write(listOf(server))

            assertEquals(listOf(server), MediaProviderStore(file).read())
            val saved = file.readText()
            assertFalse(saved.contains("password", ignoreCase = true))
            assertFalse(saved.contains("accessToken", ignoreCase = true))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun serverAddressValidationRejectsEmbeddedCredentialsAndQuery() {
        assertEquals(
            "https://media.example.test/jellyfin",
            normalizeMediaServerAddress("  https://media.example.test/jellyfin/  "),
        )
        assertFalse(runCatching { normalizeMediaServerAddress("https://user:pass@media.example.test") }.isSuccess)
        assertFalse(runCatching { normalizeMediaServerAddress("https://media.example.test?token=secret") }.isSuccess)
    }

    @Test
    fun authenticatesBrowsesAndKeepsAccessTokenOutOfPlaybackUrlAndDisk() = runBlocking {
        val directory = Files.createTempDirectory("AniLife-provider-api")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = when (exchange.requestURI.path) {
                "/Users/AuthenticateByName" -> """{"AccessToken":"private-token","User":{"Id":"user-1"}}"""
                "/Users/user-1/Views" -> """{"Items":[{"Id":"library-1","Name":"Library","Type":"CollectionFolder"}]}"""
                "/Users/user-1/Items" -> """{"Items":[{"Id":"episode-1","Name":"Episode 1","Type":"Episode","IndexNumber":1}]}"""
                else -> """{"error":"not found"}"""
            }
            val status = if (exchange.requestURI.path in setOf(
                    "/Users/AuthenticateByName",
                    "/Users/user-1/Views",
                    "/Users/user-1/Items",
                )
            ) 200 else 404
            val responseBytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        }
        server.start()
        try {
            val storeFile = directory.resolve("providers.properties").toFile()
            val manager = MediaProviderManager(
                store = MediaProviderStore(storeFile),
                http = HttpClient.newHttpClient(),
            )
            val provider = manager.addAndConnect(
                name = "Test media",
                address = "http://127.0.0.1:${server.address.port}",
                username = "viewer",
                password = "not-persisted",
            )

            assertEquals("Library", manager.libraries(provider.id).single().name)
            val episode = manager.browse(provider.id, "library-1").single()
            assertEquals("Episode 1", episode.name)
            val playback = manager.playback(provider.id, episode.id)
            assertTrue(playback.url.contains("/Videos/episode-1/stream"))
            assertFalse(playback.url.contains("private-token"))
            assertEquals("private-token", playback.headers["X-Emby-Token"])
            assertFalse(storeFile.readText().contains("private-token"))
            assertFalse(storeFile.readText().contains("not-persisted"))
            manager.disconnect(provider.id)
            assertFailsWith<IllegalStateException> { manager.libraries(provider.id) }
        } finally {
            server.stop(0)
            directory.toFile().deleteRecursively()
        }
        Unit
    }
}

