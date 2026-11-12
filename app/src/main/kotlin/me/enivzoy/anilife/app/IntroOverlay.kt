package me.enivzoy.anilife.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.sun.jna.Native
import me.enivzoy.anilife.mpv.EndReason
import me.enivzoy.anilife.mpv.Event
import me.enivzoy.anilife.mpv.Mpv
import me.enivzoy.anilife.mpv.defaultPlayerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.EventQueue
import java.io.File
import javax.swing.JPanel

@Composable
fun IntroOverlay(homeReady: Boolean, onFinished: () -> Unit) {
    val canvas = remember { Canvas().apply { background = java.awt.Color.BLACK; isFocusable = false } }
    val currentHomeReady by rememberUpdatedState(homeReady)
    var playerVisible by remember { mutableStateOf(true) }
    var blackCover by remember { mutableStateOf(true) }
    val coverAlpha by animateFloatAsState(
        targetValue = if (blackCover) 1f else 0f,
        animationSpec = tween(500),
        label = "intro-black-fade",
    )

    LaunchedEffect(canvas) {
        var player: Mpv? = null
        try {
            val introFile = withContext(Dispatchers.IO) {
                val destination = File.createTempFile("AniLife-intro-", ".mp4")
                destination.deleteOnExit()
                val input = checkNotNull(Mpv::class.java.getResourceAsStream("/anilife-intro.mp4")) {
                    "The intro video resource is missing."
                }
                input.use { stream -> destination.outputStream().use(stream::copyTo) }
                destination
            }
            while (!canvas.isShowing || canvas.width <= 0 || canvas.height <= 0) delay(16)
            delay(100)
            val windowId = Native.getComponentID(canvas)
            val library = Mpv.defaultLibraryPath() ?: error("libmpv wasn't found.")
            val options = defaultPlayerOptions()
                .filterNot { it.first in setOf("vo", "wid", "idle", "keep-open", "volume", "keepaspect", "panscan") } +
                listOf(
                    "vo" to "gpu-next",
                    "wid" to windowId.toString(),
                    "idle" to "no",
                    "keep-open" to "no",
                    "volume" to "100",
                    "keepaspect" to "yes",
                    "panscan" to "0",
                )
            val activePlayer = withContext(Dispatchers.IO) { Mpv(library, options) }
            player = activePlayer
            activePlayer.setLogLevel("warn")
            val ended = CompletableDeferred<Boolean>()
            activePlayer.startEvents { event ->
                when (event) {
                    Event.FileLoaded -> EventQueue.invokeLater { blackCover = false }
                    is Event.EndFile -> ended.complete(event.reason == EndReason.EOF)
                    is Event.Log -> if (event.level == "fatal") ended.complete(false)
                    else -> Unit
                }
            }
            withContext(Dispatchers.IO) { activePlayer.loadfile(introFile.absolutePath) }
            withTimeoutOrNull(INTRO_TIMEOUT_MILLIS) { ended.await() }
            withTimeoutOrNull(HOME_READY_TIMEOUT_MILLIS) {
                snapshotFlow { currentHomeReady }.first { it }
            }
            blackCover = true
            delay(550)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            System.err.println("AniLife intro playback failed: ${error.message}")
            error.printStackTrace()
            blackCover = true
            delay(550)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { player?.shutdown() }
        }
        playerVisible = false
        delay(16)
        blackCover = false
        delay(550)
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (playerVisible) {
            SwingPanel(
                factory = {
                    JPanel(BorderLayout()).apply {
                        background = java.awt.Color.BLACK
                        add(canvas, BorderLayout.CENTER)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = {},
            )
        }
        if (coverAlpha > 0f) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = coverAlpha }
                    .background(Color.Black),
            )
        }
    }
}

private const val INTRO_TIMEOUT_MILLIS = 20_000L
private const val HOME_READY_TIMEOUT_MILLIS = 15_000L

