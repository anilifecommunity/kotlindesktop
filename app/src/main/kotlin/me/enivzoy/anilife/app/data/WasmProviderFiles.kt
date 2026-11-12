package me.enivzoy.anilife.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class ImportedWasmProvider(
    val name: String,
    val path: Path,
    val sizeBytes: Long,
    val sha256: String,
)

class WasmProviderFileStore(private val directory: Path = providerDirectory()) {
    /** Where imported provider modules are kept. */
    fun directory(): Path = directory

    suspend fun list(): List<ImportedWasmProvider> = withContext(Dispatchers.IO) {
        if (!Files.isDirectory(directory)) return@withContext emptyList()
        Files.list(directory).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".wasm", true) }
                .map { path ->
                    val normalized = path.toAbsolutePath().normalize()
                    ImportedWasmProvider(
                        name = normalized.fileName.toString(),
                        path = normalized,
                        sizeBytes = Files.size(normalized),
                        sha256 = sha256(normalized),
                    )
                }
                .sorted(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .toList()
        }
    }

    suspend fun import(source: Path): ImportedWasmProvider = withContext(Dispatchers.IO) {
        val normalizedSource = source.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalizedSource)) { "Select an existing .wasm file." }
        require(normalizedSource.fileName.toString().endsWith(".wasm", true)) { "Only .wasm files can be imported." }
        val size = Files.size(normalizedSource)
        require(size in WASM_HEADER_SIZE..MAX_MODULE_BYTES) {
            "The WebAssembly file must be between 8 bytes and 64 MiB."
        }
        validateHeader(normalizedSource)
        Files.createDirectories(directory)
        val digest = sha256(normalizedSource)
        val existing = list().firstOrNull { it.sha256 == digest }
        if (existing != null) return@withContext existing
        val baseName = safeFileName(normalizedSource.fileName.toString().removeSuffix(".wasm").removeSuffix(".WASM"))
        var index = 1
        var destination: Path
        do {
            val suffix = if (index == 1) "" else " ($index)"
            destination = directory.resolve("$baseName$suffix.wasm")
            index++
        } while (Files.exists(destination))
        val temporary = directory.resolve(".${UUID.randomUUID()}.tmp")
        try {
            Files.copy(normalizedSource, temporary)
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, destination)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        ImportedWasmProvider(destination.fileName.toString(), destination, size, digest)
    }

    suspend fun remove(module: ImportedWasmProvider) = withContext(Dispatchers.IO) {
        val target = module.path.toAbsolutePath().normalize()
        require(target.parent == directory.toAbsolutePath().normalize() && target.fileName.toString().endsWith(".wasm", true)) {
            "That file isn't an imported provider module."
        }
        Files.deleteIfExists(target)
    }

    private fun validateHeader(path: Path) {
        val header = ByteArray(WASM_HEADER_SIZE.toInt())
        Files.newInputStream(path).use { input ->
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                require(read > 0) { "The file is too short to be a WebAssembly module." }
                offset += read
            }
        }
        require(header.contentEquals(WASM_CORE_HEADER) || header.contentEquals(WASM_COMPONENT_HEADER)) {
            "The file doesn't have a supported WebAssembly module or component header."
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[<>:\"/\\\\|?*\\p{Cntrl}]"), "_")
        .trim()
        .trimEnd('.', ' ')
        .take(100)
        .ifBlank { "provider" }

    companion object {
        private const val WASM_HEADER_SIZE = 8L
        private const val MAX_MODULE_BYTES = 64L * 1024 * 1024
        private val WASM_CORE_HEADER = byteArrayOf(0, 0x61, 0x73, 0x6d, 1, 0, 0, 0)
        private val WASM_COMPONENT_HEADER = byteArrayOf(0, 0x61, 0x73, 0x6d, 0x0d, 0, 1, 0)

        private fun providerDirectory(): Path {
            val base = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let(::File)
                ?: File(System.getProperty("user.home"), ".local/share")
            return File(base, "AniLife/providers").toPath()
        }
    }
}

