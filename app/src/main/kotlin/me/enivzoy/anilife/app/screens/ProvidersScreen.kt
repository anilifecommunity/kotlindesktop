package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.enivzoy.anilife.app.data.ImportedWasmProvider
import me.enivzoy.anilife.app.data.ProviderCatalog
import me.enivzoy.anilife.app.data.ProviderInfo
import me.enivzoy.anilife.app.ui.AniLifeColors
import java.io.File
import javax.swing.JFileChooser

/**
 * The video providers the app plays through.
 *
 * Each provider is a WebAssembly module; the bundled ones cover the usual sites, and more can be
 * dropped in from disk.
 */
@Composable
fun ProvidersScreen(
    catalog: ProviderCatalog,
    modifier: Modifier = Modifier,
) {
    var bundled by remember { mutableStateOf<List<ProviderInfo>>(emptyList()) }
    var imported by remember { mutableStateOf<List<ImportedWasmProvider>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                bundled = catalog.bundled()
                imported = catalog.imported()
            } catch (failure: Exception) {
                error = failure.message ?: "Couldn't read the providers."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Extension,
                contentDescription = null,
                tint = AniLifeColors.Foreground,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(13.dp))
            Column {
                Text("Providers", color = AniLifeColors.Foreground, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
                Text(
                    "The video providers AniLife plays through.",
                    color = AniLifeColors.MutedForeground,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AniLifeColors.Foreground, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Loading providers…", color = AniLifeColors.MutedForeground, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        error?.let { message ->
            Text(message, color = AniLifeColors.Destructive, fontSize = 13.sp, lineHeight = 18.sp)
        }

        if (!loading) {
            ProviderGroup(
                title = "Bundled",
                note = "These ship with AniLife and are always available.",
                rows = bundled.map { info -> ProviderRowData(info.name, info.source, null) },
            )

            ProviderGroup(
                title = "Added",
                note = "Provider modules dropped in from disk.",
                rows = imported.map { module ->
                    ProviderRowData(
                        name = module.name,
                        note = "Imported · ${module.sizeBytes / 1024} KB",
                        onRemove = {
                            scope.launch {
                                runCatching { catalog.remove(module) }
                                    .onFailure { error = it.message ?: "Couldn't remove the provider." }
                                refresh()
                            }
                        },
                    )
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val chooser = JFileChooser()
                        chooser.dialogTitle = "Choose a provider module"
                        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
                        chooser.isAcceptAllFileFilterUsed = false
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            scope.launch {
                                runCatching { catalog.import(chooser.selectedFile.toPath()) }
                                    .onFailure { error = it.message ?: "Couldn't import the provider." }
                                refresh()
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AniLifeColors.Primary,
                        contentColor = AniLifeColors.PrimaryForeground,
                    ),
                ) {
                    Text("Add provider (.wasm)", fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = { refresh() }) {
                    Text("Refresh", fontSize = 14.sp, lineHeight = 18.sp)
                }
            }

            Text(
                "Providers are sandboxed WebAssembly modules. They run with no access to your files " +
                    "beyond the video they fetch.",
                color = AniLifeColors.MutedForeground,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun ProviderGroup(
    title: String,
    note: String,
    rows: List<ProviderRowData>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AniLifeColors.Foreground, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(note, color = AniLifeColors.MutedForeground, fontSize = 12.sp, lineHeight = 17.sp)
        if (rows.isEmpty()) {
            Text("None yet.", color = AniLifeColors.MutedForeground, fontSize = 13.sp, lineHeight = 18.sp)
        }
        rows.forEach { row ->
            ProviderRow(name = row.name, note = row.note, onRemove = row.onRemove)
        }
    }
}

private data class ProviderRowData(
    val name: String,
    val note: String,
    val onRemove: (() -> Unit)?,
)

@Composable
private fun ProviderRow(name: String, note: String, onRemove: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AniLifeColors.Card)
            .border(1.dp, AniLifeColors.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                color = AniLifeColors.Foreground,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                note,
                color = AniLifeColors.MutedForeground,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove $name",
                    tint = AniLifeColors.MutedForeground,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
