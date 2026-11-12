package me.enivzoy.anilife.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import me.enivzoy.anilife.app.ui.AniLifeTheme
import me.enivzoy.anilife.app.data.AppSettingsStore
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.awt.Color
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.geom.AffineTransform

fun main() {
    application {
    val settingsStore = remember { AppSettingsStore() }
    val appSettings by settingsStore.settings.collectAsState()
     
     
    val state = rememberWindowState(
        size = initialWindowSize(),
        position = WindowPosition(Alignment.Center),
    )
    val previousPlacement = remember { androidx.compose.runtime.mutableStateOf(state.placement) }
    val windowedBounds = remember { androidx.compose.runtime.mutableStateOf<Rectangle?>(null) }
    val windowedStyle = remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    val windowedScale = remember { androidx.compose.runtime.mutableStateOf<AffineTransform?>(null) }
    val icon = remember { loadWindowIcon() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "AniLife",
        state = state,
        icon = icon,
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(800, 480)
            window.background = Color(10, 15, 10)
            applyDarkTitleBar(window)
        }
        AniLifeTheme(appSettings) {
            AniLifeApp(settingsStore = settingsStore, onPlayerFullscreen = { fullscreen ->
                    val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")
                    if (isWindows) {
                        if (fullscreen && windowedStyle.value == null) {
                            if (state.placement != WindowPlacement.Fullscreen) previousPlacement.value = state.placement
                            windowedBounds.value = Rectangle(window.bounds)
                            windowedScale.value = window.graphicsConfiguration.defaultTransform
                            val hwnd = Native.getWindowPointer(window)
                            val style = WindowsWindowApi.INSTANCE.GetWindowLongPtrW(hwnd, GWL_STYLE)
                            check(style != 0L) { "Couldn't read the window style for fullscreen mode." }
                            windowedStyle.value = style
                            val borderlessStyle = (style and WS_OVERLAPPEDWINDOW.inv()) or WS_POPUP
                            check(WindowsWindowApi.INSTANCE.SetWindowLongPtrW(hwnd, GWL_STYLE, borderlessStyle) != 0L) {
                                "Couldn't remove the window frame for fullscreen mode."
                            }
                            val bounds = window.graphicsConfiguration.bounds
                            val scale = window.graphicsConfiguration.defaultTransform
                            check(
                                WindowsWindowApi.INSTANCE.SetWindowPos(
                                    hwnd,
                                    null,
                                    (bounds.x * scale.scaleX).toInt(),
                                    (bounds.y * scale.scaleY).toInt(),
                                    (bounds.width * scale.scaleX).toInt(),
                                    (bounds.height * scale.scaleY).toInt(),
                                    SWP_FRAMECHANGED or SWP_NOOWNERZORDER or SWP_NOZORDER,
                                ) != 0,
                            ) { "Couldn't resize the window to the display bounds." }
                        } else if (!fullscreen && windowedStyle.value != null) {
                            val hwnd = Native.getWindowPointer(window)
                            val style = windowedStyle.value!!
                            check(WindowsWindowApi.INSTANCE.SetWindowLongPtrW(hwnd, GWL_STYLE, style) != 0L) {
                                "Couldn't restore the window frame."
                            }
                            val bounds = requireNotNull(windowedBounds.value)
                            val scale = requireNotNull(windowedScale.value)
                            check(
                                WindowsWindowApi.INSTANCE.SetWindowPos(
                                    hwnd,
                                    null,
                                    (bounds.x * scale.scaleX).toInt(),
                                    (bounds.y * scale.scaleY).toInt(),
                                    (bounds.width * scale.scaleX).toInt(),
                                    (bounds.height * scale.scaleY).toInt(),
                                    SWP_FRAMECHANGED or SWP_NOOWNERZORDER or SWP_NOZORDER,
                                ) != 0,
                            ) { "Couldn't restore the window size." }
                            windowedBounds.value = null
                            windowedStyle.value = null
                            windowedScale.value = null
                            state.placement = previousPlacement.value
                        }
                    } else if (fullscreen) {
                        if (state.placement != WindowPlacement.Fullscreen) previousPlacement.value = state.placement
                        state.placement = WindowPlacement.Fullscreen
                    } else if (state.placement == WindowPlacement.Fullscreen) {
                        state.placement = previousPlacement.value.takeUnless { it == WindowPlacement.Fullscreen }
                            ?: WindowPlacement.Floating
                    }
            })
        }
    }
    }
}

 
private fun initialWindowSize(): DpSize {
    val density = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice
        .defaultConfiguration
        .defaultTransform
        .scaleX
        .takeIf { it > 0.0 } ?: 1.0
    val screen = Toolkit.getDefaultToolkit().screenSize
    val width = (screen.width / density - 48).coerceIn(800.0, 1280.0)
    val height = (screen.height / density - 96).coerceIn(480.0, 760.0)
    return DpSize(width.dp, height.dp)
}

 
private fun loadWindowIcon(): BitmapPainter? {
    val bytes = Thread.currentThread().contextClassLoader
        ?.getResourceAsStream("anilife-icon.png")
        ?.use { it.readBytes() }
        ?: return null
    val bitmap = runCatching {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull() ?: return null
    return BitmapPainter(bitmap)
}

 
private fun applyDarkTitleBar(window: java.awt.Window) {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("windows")) return
    runCatching {
        val dwm = Native.load("dwmapi", DwmApi::class.java)
        val hwnd = Native.getWindowPointer(window)
        val enabled = Memory(4).apply { setInt(0, 1) }
         
        if (dwm.DwmSetWindowAttribute(hwnd, 20, enabled, 4) != 0) {
            dwm.DwmSetWindowAttribute(hwnd, 19, enabled, 4)
        }
    }
}

interface DwmApi : Library {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int
}

private interface WindowsWindowApi : Library {
    fun GetWindowLongPtrW(hwnd: Pointer, index: Int): Long
    fun SetWindowLongPtrW(hwnd: Pointer, index: Int, value: Long): Long
    fun SetWindowPos(hwnd: Pointer, insertAfter: Pointer?, x: Int, y: Int, width: Int, height: Int, flags: Int): Int

    companion object {
        val INSTANCE: WindowsWindowApi = Native.load("user32", WindowsWindowApi::class.java)
    }
}

private const val GWL_STYLE = -16
private const val WS_POPUP = 0x80000000L
private const val WS_OVERLAPPEDWINDOW = 0x00CF0000L
private const val SWP_NOOWNERZORDER = 0x0200
private const val SWP_FRAMECHANGED = 0x0020
private const val SWP_NOZORDER = 0x0004

