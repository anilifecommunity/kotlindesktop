package me.enivzoy.anilife.app.data

import ai.tegmentum.wasmtime4j.WasmRuntime
import ai.tegmentum.wasmtime4j.component.Component
import ai.tegmentum.wasmtime4j.component.ComponentEngine
import ai.tegmentum.wasmtime4j.component.ComponentHostFunction
import ai.tegmentum.wasmtime4j.component.ComponentInstance
import ai.tegmentum.wasmtime4j.component.ComponentLinker
import ai.tegmentum.wasmtime4j.component.ComponentVal
import ai.tegmentum.wasmtime4j.factory.WasmRuntimeFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

internal data class WasmAnimeTitle(
    val anilistId: Long,
    val malId: Long?,
    val titles: List<String>,
)

/** Runs the bundled AniLife WASI Preview 2 provider components through their shared WIT contract. */
internal class WasmAnimeProviders {
    private val runtime: WasmRuntime by lazy { WasmRuntimeFactory.create() }
    private val engine: ComponentEngine by lazy { runtime.createComponentEngine() }
    private val compiled: List<LoadedProvider> by lazy { loadProviders() }

    fun sources(title: WasmAnimeTitle, episode: Double, dub: Boolean): List<AnimeVideoSource> =
        compiled.flatMap { provider -> runCatching { provider.sources(title, episode, dub) }.getOrDefault(emptyList()) }

    private fun loadProviders(): List<LoadedProvider> = PROVIDER_FILES.mapNotNull { file ->
        runCatching {
            val bytes = javaClass.getResourceAsStream("/providers/$file")?.use { it.readBytes() }
                ?: error("Bundled provider $file is missing.")
            val component = engine.compileComponent(bytes, file)
            val info = invokeInfo(component)
            LoadedProvider(component, info.first, info.second)
        }.getOrNull()
    }

    private fun invokeInfo(component: Component): Pair<String, Set<String>> {
        val linker = linker(emptySet(), ConcurrentHashMap())
        val store = runtime.createStore(engine.engine, FUEL_LIMIT, MEMORY_LIMIT_BYTES, CALL_TIMEOUT_SECONDS)
        return try {
            val instance = linker.instantiate(store, component)
            try {
                val info = instance.invoke("info") as? Map<*, *> ?: error("Provider info was not a record.")
                val name = info["name"] as? String ?: info["id"] as? String ?: "Video provider"
                val hosts = (info["hosts"] as? List<*>)?.mapNotNull { it as? String }?.toSet().orEmpty()
                name to hosts
            } finally {
                instance.close()
            }
        } finally {
            store.close()
            linker.close()
        }
    }

    private inner class LoadedProvider(
        private val component: Component,
        private val name: String,
        private val hosts: Set<String>,
    ) {
        private val cache = ConcurrentHashMap<String, CacheValue>()

        fun sources(title: WasmAnimeTitle, episode: Double, dub: Boolean): List<AnimeVideoSource> {
            val linker = linker(hosts, cache)
            val store = runtime.createStore(engine.engine, FUEL_LIMIT, MEMORY_LIMIT_BYTES, CALL_TIMEOUT_SECONDS)
            try {
                val instance = linker.instantiate(store, component)
                try {
                    val malId: Optional<Long> = title.malId
                        ?.coerceIn(0, UINT32_MAX)
                        ?.let { Optional.of<Long>(it) }
                        ?: Optional.empty<Long>()
                    val query = mapOf<String, Any>(
                        "anilist-id" to title.anilistId.coerceIn(0, UINT32_MAX),
                        "mal-id" to malId,
                        "titles" to title.titles.distinct(),
                        "episode" to episode,
                        "dub" to dub,
                    )
                    val result = instance.invoke("servers", query) as? Map<*, *> ?: return emptyList()
                    if (result["isOk"] != true) return emptyList()
                    val servers = result["ok"] as? List<*> ?: return emptyList()
                    return servers.flatMap { value -> parseServer(value, name, dub) }
                } finally {
                    instance.close()
                }
            } finally {
                store.close()
                linker.close()
            }
        }
    }

    private data class CacheValue(val value: String, val expiresAt: Long)

    private fun linker(hosts: Set<String>, cache: ConcurrentHashMap<String, CacheValue>): ComponentLinker<Any> {
        val linker: ComponentLinker<Any> = runtime.createComponentLinker<Any>(engine.engine)
        linker.enableWasiPreview2()
        linker.defineFunction(WIT_HOST + "#http", ComponentHostFunction.create { args ->
            val request = args[0].asRecord()
            val response = providerRequest(request, hosts)
            listOf(response.fold(
                onSuccess = { ComponentVal.ok(responseValue(it)) },
                onFailure = { ComponentVal.err(ComponentVal.string(it.message ?: "HTTP request failed")) },
            ))
        })
        linker.defineFunction(WIT_HOST + "#browser-fetch", ComponentHostFunction.create { args ->
            val origin = args[0].asString()
            listOf(providerRequest(args[1].asRecord(), hosts, origin).fold(
                onSuccess = { ComponentVal.ok(responseValue(it)) },
                onFailure = { ComponentVal.err(ComponentVal.string("HTTP fallback for browser fetch failed: ${it.message}")) },
            ))
        })
        linker.defineFunction(WIT_HOST + "#log", ComponentHostFunction.create { args ->
            System.err.println("[provider] ${args.firstOrNull()?.asString().orEmpty()}")
            emptyList()
        })
        linker.defineFunction(WIT_HOST + "#cache-get", ComponentHostFunction.create { args ->
            val cached = cache[args[0].asString()]?.takeIf { it.expiresAt > System.currentTimeMillis() }
            listOf(if (cached == null) ComponentVal.none() else ComponentVal.some(ComponentVal.string(cached.value)))
        })
        linker.defineFunction(WIT_HOST + "#cache-set", ComponentHostFunction.create { args ->
            val ttl = args[2].asU32().coerceAtMost(MAX_CACHE_TTL_SECONDS)
            val key = args[0].asString()
            val value = args[1].asString()
            require(key.length <= MAX_CACHE_KEY_CHARS && value.length <= MAX_CACHE_VALUE_CHARS) { "Provider cache entry exceeds its size limit." }
            if (cache.containsKey(key) || cache.size < MAX_CACHE_ENTRIES) {
                cache[key] = CacheValue(value, System.currentTimeMillis() + ttl * 1000L)
            }
            emptyList()
        })
        return linker
    }

    private fun providerRequest(
        request: Map<String, ComponentVal>,
        hosts: Set<String>,
        browserOrigin: String? = null,
    ): Result<ProviderHttpResponse> = runCatching {
        val method = request.getValue("method").asString().uppercase()
        var uri = URI(request.getValue("url").asString())
        val headers = request.getValue("headers").asList().mapNotNull { tuple ->
            tuple.asTuple().takeIf { it.size == 2 }?.let { it[0].asString() to it[1].asString() }
        }.toMutableList().apply {
            browserOrigin?.let { origin ->
                val originHost = URI(origin).host?.lowercase() ?: error("Browser origin has no host.")
                require(hosts.any { allowed -> originHost == allowed.lowercase() || originHost.endsWith(".${allowed.lowercase()}") }) {
                    "Provider is not allowed to use browser origin $originHost."
                }
                if (none { it.first.equals("referer", true) }) add("Referer" to origin.trimEnd('/') + "/")
            }
        }
        val body = request.getValue("body").asSome().orElse(null)?.asByteArray()
        for (hop in 0..MAX_REDIRECTS) {
            require(uri.scheme == "https" || uri.scheme == "http") { "Provider requests must use HTTP or HTTPS." }
            val host = uri.host?.lowercase() ?: error("The provider URL has no host.")
            require(hosts.any { allowed -> host == allowed.lowercase() || host.endsWith(".${allowed.lowercase()}") }) {
                "Provider is not allowed to access $host."
            }
            val builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("User-Agent", DEFAULT_USER_AGENT)
            headers.forEach { (key, value) ->
                if (!key.equals("host", true) && !key.equals("content-length", true) && !key.equals("user-agent", true)) builder.header(key, value)
            }
            val publisher = body?.let(HttpRequest.BodyPublishers::ofByteArray) ?: HttpRequest.BodyPublishers.noBody()
            val httpRequest = builder.method(method, publisher).build()
            val response = http.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() in 300..399) {
                response.body().close()
                val location = response.headers().firstValue("location").orElse(null)
                    ?: error("Provider redirect response omitted its location.")
                require(hop < MAX_REDIRECTS) { "Provider request exceeded the redirect limit." }
                uri = uri.resolve(location)
            } else {
                val responseBytes = response.body().use { input -> input.readNBytes(MAX_HTTP_BODY_BYTES + 1) }
                require(responseBytes.size <= MAX_HTTP_BODY_BYTES) { "Provider response exceeded the size limit." }
                val responseBody = responseBytes.let { bytes ->
                    if (response.headers().firstValue("content-encoding").orElse("").equals("gzip", true)) {
                        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                    } else bytes
                }
                require(responseBody.size <= MAX_HTTP_BODY_BYTES) { "Decompressed provider response exceeded the size limit." }
                return@runCatching ProviderHttpResponse(
                    response.statusCode(),
                    response.headers().map().flatMap { (key, values) -> values.map { key to it } },
                    responseBody,
                )
            }
        }
        error("Provider request exceeded the redirect limit.")
    }

    private fun responseValue(response: ProviderHttpResponse): ComponentVal = ComponentVal.record(
        mapOf(
            "status" to ComponentVal.u16(response.status),
            "headers" to ComponentVal.list(response.headers.map { (key, value) -> ComponentVal.tuple(ComponentVal.string(key), ComponentVal.string(value)) }),
            "body" to ComponentVal.listU8(response.body),
        ),
    )

    private data class ProviderHttpResponse(val status: Int, val headers: List<Pair<String, String>>, val body: ByteArray)

    private fun parseServer(value: Any?, providerName: String, requestedDub: Boolean): List<AnimeVideoSource> {
        val server = value as? Map<*, *> ?: return emptyList()
        val serverName = server["name"] as? String ?: providerName
        val dub = server["dub"] as? Boolean ?: requestedDub
        val headers = (server["headers"] as? List<*>)?.mapNotNull { pair ->
            val parts = pair as? List<*> ?: return@mapNotNull null
            val key = parts.getOrNull(0) as? String ?: return@mapNotNull null
            val header = parts.getOrNull(1) as? String ?: return@mapNotNull null
            key to header
        }?.toMap().orEmpty()
        val streams = server["streams"] as? List<*> ?: return emptyList()
        return streams.mapNotNull { item ->
            val stream = item as? Map<*, *> ?: return@mapNotNull null
            val url = stream["url"] as? String ?: return@mapNotNull null
            val format = stream["format"] as? String ?: inferFormat(url)
            AnimeVideoSource(
                provider = providerName,
                server = serverName,
                audio = if (dub) "Dub" else "Sub",
                format = format,
                quality = stream["quality"] as? String,
                url = url,
                headers = headers,
            )
        }
    }

    private fun inferFormat(url: String) = when {
        ".m3u8" in url.lowercase() -> "hls"
        ".mpd" in url.lowercase() -> "dash"
        else -> "mp4"
    }

    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build()

    private companion object {
        val PROVIDER_FILES = listOf("zoko.wasm", "megaplay.wasm", "animeonsen.wasm", "rift.wasm", "uniquestream.wasm", "anipm.wasm", "animeheaven.wasm", "animepahe.wasm", "anizone.wasm")
        const val WIT_HOST = "AniLife:provider/host@1.0.0"
        const val FUEL_LIMIT = 100_000_000L
        const val MEMORY_LIMIT_BYTES = 64L * 1024 * 1024
        const val CALL_TIMEOUT_SECONDS = 45L
        const val UINT32_MAX = 4_294_967_295L
        const val MAX_CACHE_TTL_SECONDS = 2_592_000L
        const val MAX_CACHE_ENTRIES = 4096
        const val MAX_CACHE_KEY_CHARS = 512
        const val MAX_CACHE_VALUE_CHARS = 262_144
        const val MAX_REDIRECTS = 6
        const val MAX_HTTP_BODY_BYTES = 16 * 1024 * 1024
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

