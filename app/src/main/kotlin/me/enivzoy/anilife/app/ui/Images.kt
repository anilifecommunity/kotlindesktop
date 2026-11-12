package me.enivzoy.anilife.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

 



object RemoteImages {
    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    private val cacheDir: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
        File(base, "AniLife/image-cache").also { it.mkdirs() }
    }

    private val lock = Any()
    private val memory = object : LinkedHashMap<String, ImageBitmap>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean = size > 140
    }

    fun cached(url: String): ImageBitmap? = synchronized(lock) { memory[url] }

    suspend fun load(url: String): ImageBitmap? {
        cached(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            cached(url)?.let { return@withContext it }
            val bytes = readDisk(url) ?: download(url)?.also { writeDisk(url, it) }
            if (bytes == null || bytes.isEmpty()) return@withContext null
            val bitmap = runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
                ?: return@withContext null
            synchronized(lock) { memory[url] = bitmap }
            bitmap
        }
    }

    private fun download(url: String): ByteArray? = runCatching {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "AniLife/1.0")
            .header("Accept", "image/*")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() in 200..299) response.body() else null
    }.getOrNull()

    private fun readDisk(url: String): ByteArray? = runCatching {
        val file = File(cacheDir, keyFor(url))
        if (file.isFile && file.length() > 0) file.readBytes() else null
    }.getOrNull()

    private fun writeDisk(url: String, bytes: ByteArray) {
        runCatching {
            val file = File(cacheDir, keyFor(url))
            val temporary = File(cacheDir, "${file.name}.tmp")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(file)) {
                file.writeBytes(bytes)
                temporary.delete()
            }
        }
    }

    private fun keyFor(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

private sealed interface RemoteImageState {
    data object Loading : RemoteImageState
    data object Unavailable : RemoteImageState
    data class Ready(val bitmap: ImageBitmap) : RemoteImageState
}

 
@Composable
private fun rememberRemoteImage(url: String?): RemoteImageState {
    if (url.isNullOrBlank()) return RemoteImageState.Unavailable
    var state by remember(url) {
        mutableStateOf(RemoteImages.cached(url)?.let(RemoteImageState::Ready) ?: RemoteImageState.Loading)
    }
    LaunchedEffect(url) {
        if (state == RemoteImageState.Loading) {
            state = RemoteImages.load(url)?.let(RemoteImageState::Ready) ?: RemoteImageState.Unavailable
        }
    }
    return state
}

 
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = AniLifeColors.CardHover,
    tint: Color? = null,
) {
    val state = rememberRemoteImage(url)
    Box(modifier.background(placeholderColor), contentAlignment = Alignment.Center) {
        when (state) {
            is RemoteImageState.Ready -> Image(
                bitmap = state.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
            RemoteImageState.Loading -> Shimmer(Modifier.fillMaxSize())
            RemoteImageState.Unavailable -> Text(
                text = "No Artwork",
                color = AniLifeColors.MutedForeground.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (tint != null) {
            Box(Modifier.fillMaxSize().background(tint))
        }
    }
}

