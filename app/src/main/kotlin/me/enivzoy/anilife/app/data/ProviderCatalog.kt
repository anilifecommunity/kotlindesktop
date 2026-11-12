package me.enivzoy.anilife.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * The video providers the app ships with, plus any the viewer drops in.
 *
 * A provider is a WebAssembly module that answers for one streaming site. The bundled ones live in
 * the app's resources; extra ones can be imported from disk and are read from the provider
 * directory next to the rest of AniLife's data.
 */
class ProviderCatalog(
    private val files: WasmProviderFileStore = WasmProviderFileStore(),
) {
    /** The bundled providers, by the name their module reports. */
    suspend fun bundled(): List<ProviderInfo> = withContext(Dispatchers.IO) {
        WasmAnimeProviders().bundledProviderNames().map { name ->
            ProviderInfo(name = name, source = "Bundled", sizeBytes = 0)
        }
    }

    /** The providers the viewer added from disk. */
    suspend fun imported(): List<ImportedWasmProvider> = files.list()

    /** Where extra provider modules are kept. */
    fun directory(): Path = files.directory()

    suspend fun import(source: Path): ImportedWasmProvider = files.import(source)

    suspend fun remove(module: ImportedWasmProvider) = files.remove(module)

    /** Every provider module the app will load, bundled first. */
    suspend fun all(): List<ProviderInfo> = bundled() + imported().map {
        ProviderInfo(
            name = it.name.removeSuffix(".wasm"),
            source = "Imported",
            sizeBytes = it.sizeBytes,
        )
    }
}

/** One provider, as the providers page lists it. */
data class ProviderInfo(
    val name: String,
    val source: String,
    val sizeBytes: Long = 0,
)
