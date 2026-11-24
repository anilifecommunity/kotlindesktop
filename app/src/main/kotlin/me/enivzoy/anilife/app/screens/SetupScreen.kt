package me.enivzoy.anilife.app.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.enivzoy.anilife.app.data.Dependency
import me.enivzoy.anilife.app.data.DependencyManager
import me.enivzoy.anilife.app.data.DependencyState
import me.enivzoy.anilife.app.data.DependencyStatus
import me.enivzoy.anilife.app.ui.AniLifeColors

internal fun formatSetupBytes(value: Long): String {
    if (value <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var amount = value.toDouble()
    var unit = 0
    while (amount >= 1024.0 && unit < units.lastIndex) {
        amount /= 1024.0
        unit++
    }
    val digits = if (unit == 0 || amount >= 100.0) 0 else 1
    return "%.${digits}f %s".format(amount, units[unit])
}

@Composable
fun SetupScreen(manager: DependencyManager, onContinue: () -> Unit) {
    val states by manager.states.collectAsState()
    val dependencies = Dependency.entries.map { requireNotNull(states[it]) }
    val complete = dependencies.all { it.status == DependencyStatus.Installed }

    LaunchedEffect(manager) { manager.start() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AniLifeColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "AniLife setup",
                    color = AniLifeColors.Foreground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Installing the runtime and media tools AniLife needs.",
                    color = AniLifeColors.MutedForeground,
                    fontSize = 14.sp,
                )
            }

            dependencies.forEach { state ->
                DependencyCard(
                    state = state,
                    onRetry = { manager.retry(state.dependency) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = manager::retryAll,
                    enabled = !complete,
                    colors = ButtonDefaults.textButtonColors(contentColor = AniLifeColors.MutedForeground),
                ) {
                    Text("Retry all")
                }
                Button(
                    onClick = onContinue,
                    enabled = complete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AniLifeColors.Primary,
                        contentColor = AniLifeColors.PrimaryForeground,
                        disabledContainerColor = AniLifeColors.Secondary,
                        disabledContentColor = AniLifeColors.MutedForeground,
                    ),
                ) {
                    Text("Continue", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DependencyCard(state: DependencyState, onRetry: () -> Unit) {
    val label = when (state.dependency) {
        Dependency.JRE -> "Java runtime"
        Dependency.MPV -> "mpv player"
        Dependency.FFMPEG -> "ffmpeg tools"
        Dependency.UPSCALER -> "Upscaler models"
    }
    val detail = when (state.status) {
        DependencyStatus.Pending -> "Waiting"
        DependencyStatus.Downloading -> listOfNotNull(
            state.totalBytes.takeIf { it > 0L }?.let { "${formatSetupBytes(state.bytes)} / ${formatSetupBytes(it)}" },
            state.speedBps.takeIf { it > 0L }?.let { "${formatSetupBytes(it)}/s" },
        ).joinToString(" · ").ifBlank { "Downloading…" }
        DependencyStatus.Verifying -> "Verifying checksum"
        DependencyStatus.Extracting -> "Installing"
        DependencyStatus.Installed -> "Ready"
        DependencyStatus.Failed -> state.error ?: "Setup failed"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AniLifeColors.Card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state.status) {
                DependencyStatus.Installed -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = AniLifeColors.Success,
                    modifier = Modifier.size(20.dp),
                )
                DependencyStatus.Downloading, DependencyStatus.Verifying, DependencyStatus.Extracting ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AniLifeColors.Primary,
                        strokeWidth = 2.dp,
                    )
                else -> Box(Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = AniLifeColors.Foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = AniLifeColors.MutedForeground, fontSize = 12.sp)
            }
            if (state.status == DependencyStatus.Failed) {
                TextButton(onClick = onRetry) { Text("Retry", color = AniLifeColors.Primary) }
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (state.status == DependencyStatus.Failed) AniLifeColors.Destructive else AniLifeColors.Primary,
            trackColor = AniLifeColors.Secondary,
        )
    }
}
