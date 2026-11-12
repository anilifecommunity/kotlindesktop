package me.enivzoy.anilife.app.data

import me.enivzoy.anilife.core.model.AnimeCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MediaProviderServer(
    val id: String,
    val name: String,
    val address: String,
    val username: String,
)

data class MediaProviderItem(
    val id: String,
    val name: String,
    val type: String,
    val overview: String?,
    val imageUrl: String?,
    val seriesName: String?,
    val episodeNumber: Int?,
)

data class MediaProviderPlayback(val url: String, val headers: Map<String, String>)

class MediaProviderStore(private val file: File = providerFile()) {
    fun read(): List<MediaProviderServer> {
        if (!file.isFile) return emptyList()
        val properties = Properties()
        file.inputStream().use(properties::load)
        val count = properties.getProperty("count")?.toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { index ->
            val prefix = "server.$index."
            val id = properties.getProperty(prefix + "id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val name = properties.getProperty(prefix + "name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val address = properties.getProperty(prefix + "address")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val username = properties.getProperty(prefix + "username").orEmpty()
            MediaProviderServer(id, name, address, username)
        }
    }

    fun write(servers: List<MediaProviderServer>) {
        file.parentFile?.let { Files.createDirectories(it.toPath()) }
        val properties = Properties().apply {
            setProperty("count", servers.size.toString())
            servers.forEachIndexed { index, server ->
                val prefix = "server.$index."
                setProperty(prefix + "id", server.id)
                setProperty(prefix + "name", server.name)
                setProperty(prefix + "address", server.address)
                setProperty(prefix + "username", server.username)
            }
        }
        val temporary = File(file.parentFile, "${file.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { properties.store(it, null) }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }

    companion object {
        private fun providerFile(): File {
            val base = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let(::File)
                ?: File(System.getProperty("user.home"), ".local/share")
            return File(base, "AniLife/media-providers.properties")
        }
    }
}

class MediaProviderManager(
    private val store: MediaProviderStore = MediaProviderStore(),
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) {
    private val mutableServers = MutableStateFlow(store.read())
    val servers = mutableServers.asStateFlow()
    private val sessions = ConcurrentHashMap<String, MediaProviderSession>()

    suspend fun addAndConnect(name: String, address: String, username: String, password: String): MediaProviderServer {
        require(name.isNotBlank()) { "Enter a name for this media server." }
        require(username.isNotBlank()) { "Enter the server username." }
        val normalizedAddress = normalizeMediaServerAddress(address)
        val candidate = MediaProviderServer(UUID.randomUUID().toString(), name.trim(), normalizedAddress, username.trim())
        val session = MediaProviderClient(candidate, http).authenticate(password)
        val updated = mutableServers.value + candidate
        store.write(updated)
        mutableServers.value = updated
        sessions[candidate.id] = session
        return candidate
    }

    suspend fun connect(serverId: String, password: String): MediaProviderSession {
        val server = mutableServers.value.firstOrNull { it.id == serverId }
            ?: error("That media server is no longer configured.")
        sessions.remove(serverId)
        val session = MediaProviderClient(server, http).authenticate(password)
        sessions[server.id] = session
        return session
    }

    fun remove(serverId: String) {
        val updated = mutableServers.value.filterNot { it.id == serverId }
        store.write(updated)
        mutableServers.value = updated
        sessions.remove(serverId)
    }

    fun disconnect(serverId: String) {
        sessions.remove(serverId)
    }

    suspend fun libraries(serverId: String): List<MediaProviderItem> =
        client(serverId).libraries()

    suspend fun browse(serverId: String, parentId: String): List<MediaProviderItem> =
        client(serverId).browse(parentId)

    fun playback(serverId: String, itemId: String): MediaProviderPlayback =
        client(serverId).playback(itemId)

    suspend fun download(serverId: String, item: MediaProviderItem, downloadRoot: Path): LocalEpisode =
        client(serverId).download(item, downloadRoot)

    private fun client(serverId: String): MediaProviderClient {
        val server = mutableServers.value.firstOrNull { it.id == serverId }
            ?: error("That media server is no longer configured.")
        val session = sessions[serverId] ?: error("Connect to this media server first.")
        return MediaProviderClient(server, http, session)
    }
}

class MediaProviderSession internal constructor(
    internal val userId: String,
    internal val accessToken: String,
)

internal class MediaProviderClient(
    private val server: MediaProviderServer,
    private val http: HttpClient,
    private val session: MediaProviderSession? = null,
) {
    suspend fun authenticate(password: String): MediaProviderSession = withContext(Dispatchers.IO) {
        require(password.isNotEmpty()) { "Enter the server password." }
        val body = buildJsonObject {
            put("Username", server.username)
            put("Pw", password)
        }.toString()
        val response = send("/Users/AuthenticateByName", body, null)
        val token = response.string("AccessToken") ?: error("The media server response did not contain an access token.")
        val resolvedUserId = (response["User"] as? JsonObject)?.string("Id")
            ?: error("The media server response did not contain a user ID.")
        MediaProviderSession(resolvedUserId, token)
    }

    suspend fun libraries(): List<MediaProviderItem> = withContext(Dispatchers.IO) {
        val active = requireSession()
        val response = send("/Users/${encode(active.userId)}/Views", null, active)
        response.items().mapNotNull(::parseItem)
    }

    suspend fun browse(parentId: String): List<MediaProviderItem> = withContext(Dispatchers.IO) {
        val active = requireSession()
        val query = listOf(
            "ParentId" to parentId,
            "Fields" to "Overview,ImageTags,IndexNumber,ParentIndexNumber",
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
            "Limit" to "200",
            "StartIndex" to "0",
        ).joinToString("&") { "${encode(it.first)}=${encode(it.second)}" }
        val response = send("/Users/${encode(active.userId)}/Items?$query", null, active)
        response.items().mapNotNull(::parseItem)
    }

    fun playback(itemId: String): MediaProviderPlayback {
        val active = requireSession()
        return MediaProviderPlayback(
            url = "${server.address}/Videos/${encode(itemId)}/stream?Static=true",
            headers = mapOf("X-Emby-Token" to active.accessToken),
        )
    }

    suspend fun download(item: MediaProviderItem, downloadRoot: Path): LocalEpisode = withContext(Dispatchers.IO) {
        val active = requireSession()
        require(item.type.lowercase() in playableTypes) { "Only video items can be downloaded." }
        val uri = URI("${server.address}/Items/${encode(item.id)}/Download")
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(60))
            .header("X-Emby-Authorization", authorizationHeader(active))
            .header("X-Emby-Token", active.accessToken)
            .GET()
            .build()
        val directory = downloadRoot
            .resolve(safeFileName(item.seriesName ?: item.name))
        Files.createDirectories(directory)
        val suggestedName = item.name.takeIf(String::isNotBlank) ?: "Video"
        val temporary = directory.resolve("${UUID.randomUUID()}.part")
        var destination: Path? = null
        try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofFile(temporary))
            require(response.statusCode() in 200..299) { "The media server returned HTTP ${response.statusCode()} while downloading." }
            require(Files.size(temporary) > 0L) { "The media server returned an empty video file." }
            val contentType = response.headers().firstValue("Content-Type").orElse(null)
                ?.substringBefore(';')
                ?.lowercase()
                .orEmpty()
            require(contentType.startsWith("video/") || contentType in setOf("application/octet-stream", "application/x-matroska")) {
                "The media server response wasn't a video file."
            }
            val disposition = response.headers().firstValue("Content-Disposition").orElse(null).orEmpty()
            val serverFileName = disposition.substringAfter("filename=", "").trim().trim('"')
            val serverExtension = serverFileName.substringAfterLast('.', "")
                .takeIf { it.matches(Regex("[A-Za-z0-9]{2,5}")) }
                ?.lowercase()
            val itemExtension = item.name.substringAfterLast('.', "").lowercase()
                .takeIf { it in videoExtensions }
            val extension = serverExtension?.takeIf { it in videoExtensions } ?: itemExtension ?: "mkv"
            val baseName = if (itemExtension != null && suggestedName.substringAfterLast('.', "").equals(itemExtension, true)) {
                suggestedName.substringBeforeLast('.')
            } else {
                suggestedName
            }
            val output = directory.resolve("${safeFileName(baseName)}.$extension").normalize()
            require(output.startsWith(directory.normalize())) { "The server returned an invalid file name." }
            destination = output
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING)
            }
            val title = item.seriesName?.takeIf(String::isNotBlank) ?: item.name
            val stableId = (UUID.nameUUIDFromBytes(title.lowercase().toByteArray()).mostSignificantBits and Long.MAX_VALUE).coerceAtLeast(1L)
            val episode = item.episodeNumber?.toDouble() ?: 1.0
            val localEpisode = LocalEpisode(
                anime = AnimeCard(id = stableId, title = title),
                episode = episode,
                audio = "sub",
                path = output,
                bytes = Files.size(output),
            )
            LocalDownloadsStore().addAll(listOf(localEpisode))
            localEpisode
        } catch (error: Throwable) {
            Files.deleteIfExists(temporary)
            destination?.let(Files::deleteIfExists)
            throw error
        }
    }

    private suspend fun send(path: String, body: String?, active: MediaProviderSession?): JsonObject = withContext(Dispatchers.IO) {
        val requestBuilder = HttpRequest.newBuilder(URI(server.address + path))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("X-Emby-Authorization", authorizationHeader(active))
        if (body == null) requestBuilder.GET() else {
            requestBuilder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
        }
        val response = http.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            when (response.statusCode()) {
                401, 403 -> "Media server authentication failed or access was denied."
                in 300..399 -> "The media server redirected the request. Check the configured server address."
                else -> "The media server returned HTTP ${response.statusCode()}."
            }
        }
        Json.parseToJsonElement(response.body()).jsonObject
    }

    private fun authorizationHeader(active: MediaProviderSession?): String = buildString {
        append("MediaBrowser Client=\"AniLife\", Device=\"Desktop\", DeviceId=\"")
        append(DEVICE_ID)
        append("\", Version=\"1.0\"")
        active?.let {
            append(", Token=\"")
            append(it.accessToken.replace("\"", ""))
            append('"')
        }
    }

    private fun requireSession(): MediaProviderSession =
        session ?: error("Connect to this media server first.")

    private fun parseItem(value: JsonObject): MediaProviderItem? {
        val id = value.string("Id") ?: return null
        val name = value.string("Name")?.takeIf(String::isNotBlank) ?: return null
        val type = value.string("Type") ?: "Folder"
        val imageTag = (value["ImageTags"] as? JsonObject)?.string("Primary")
        val imageUrl = imageTag?.let {
            "${server.address}/Items/${encode(id)}/Images/Primary?maxWidth=480&tag=${encode(it)}&api_key=${encode(requireSession().accessToken)}"
        }
        return MediaProviderItem(
            id = id,
            name = name,
            type = type,
            overview = value.string("Overview"),
            imageUrl = imageUrl,
            seriesName = value.string("SeriesName"),
            episodeNumber = value.int("IndexNumber"),
        )
    }

    private fun JsonObject.items() = (this["Items"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList()))
        .mapNotNull { runCatching { it.jsonObject }.getOrNull() }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[<>:\"/\\\\|?*\\p{Cntrl}]"), "_")
        .trim()
        .trimEnd('.', ' ')
        .take(100)
        .ifBlank { "Video" }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private fun JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toIntOrNull()

    companion object {
        private val playableTypes = setOf("movie", "episode", "video")
        private val videoExtensions = setOf("mkv", "mp4", "m4v", "webm", "avi", "mov", "ts", "m2ts")
        private val DEVICE_ID = UUID.nameUUIDFromBytes("AniLife-Desktop".toByteArray()).toString()
    }
}

fun normalizeMediaServerAddress(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    val uri = runCatching { URI(trimmed) }.getOrNull()
        ?: throw IllegalArgumentException("Enter a valid media server URL.")
    require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) {
        "Media server URLs must use HTTP or HTTPS."
    }
    require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "Enter a server address with a host and no credentials, query, or fragment."
    }
    return uri.toASCIIString()
}

