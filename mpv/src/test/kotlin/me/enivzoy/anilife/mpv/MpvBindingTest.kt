package me.enivzoy.anilife.mpv

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

 




class MpvBindingTest {

    @Test
    fun initialisesAndShutsDown() {
        val library = Mpv.defaultLibraryPath()
        if (library == null || !File(library).exists()) {
            println("libmpv not found; skipping binding smoke test")
            return
        }

        val events = CopyOnWriteArrayList<Event>()
        val mpv = Mpv(libraryPath = library, initialOptions = emptyList())
        mpv.startEvents { events.add(it) }
        mpv.setLogLevel("warn")
        mpv.loadfile(File("missing-intro-test-file.mp4").absolutePath)

        val deadline = System.currentTimeMillis() + 5_000
        while (events.none { it is Event.EndFile } && System.currentTimeMillis() < deadline) Thread.sleep(20)
        mpv.shutdown()

        val end = events.firstOrNull { it is Event.EndFile } as? Event.EndFile
        assertNotNull(end, "expected an end-file event: $events")
        assertTrue(end.reason == EndReason.ERROR || end.reason == EndReason.REDIRECT, "unexpected end reason: $events")
        assertTrue(events.any { it is Event.Log }, "expected mpv log events to be decoded: $events")
        println("libmpv binding initialised and shut down; events seen: $events")
    }
}

