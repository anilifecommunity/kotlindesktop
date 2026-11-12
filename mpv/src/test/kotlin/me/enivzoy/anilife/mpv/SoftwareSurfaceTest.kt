package me.enivzoy.anilife.mpv

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SoftwareSurfaceTest {

    @Test
    fun rendersVideoFramesThroughTheSoftwareSurface() {
        val library = Mpv.defaultLibraryPath()
        if (library == null || !File(library).exists()) {
            println("libmpv not found; skipping software surface test")
            return
        }
        val media = listOf(
            File("src/main/resources/anilife-intro.mp4"),
            File("../app/src/main/resources/anilife-intro.mp4"),
            File("D:/AniLife/decomp/kotlin-port/app/src/main/resources/anilife-intro.mp4"),
        ).firstOrNull(File::isFile)
        if (media == null) {
            println("intro clip not found; skipping software surface test")
            return
        }

        val options = defaultPlayerOptions()
            .filterNot { it.first == "hwdec" }
            .toMutableList()
        options += "hwdec" to "no"
        options += "ao" to "null"

        val mpv = Mpv(libraryPath = library, initialOptions = options)
        val surface = mpv.createSoftwareVideoSurface(320, 180)
        try {
            mpv.loadfile(media.absolutePath)
            val deadline = System.currentTimeMillis() + 20_000
            var litPixels = 0
            var bestLitPixels = 0
            var frames = 0
            while (System.currentTimeMillis() < deadline && bestLitPixels < 500) {
                if (surface.needsRender()) {
                    val bytes = surface.render()
                    if (bytes != null) {
                        frames++
                        var index = 0
                        litPixels = 0
                        while (index + 3 < bytes.size) {
                            val b = bytes[index].toInt() and 0xff
                            val g = bytes[index + 1].toInt() and 0xff
                            val r = bytes[index + 2].toInt() and 0xff
                            if (r + g + b > 24) litPixels++
                            index += 4
                        }
                        bestLitPixels = maxOf(bestLitPixels, litPixels)
                    }
                } else {
                    Thread.sleep(16)
                }
            }
            assertTrue(bestLitPixels > 500, "expected decoded video frames from $media (best=$bestLitPixels)")
            println("software renderer painted $frames frames; brightest had $bestLitPixels lit pixels")
        } finally {
            surface.close()
            mpv.shutdown()
        }
    }
}

