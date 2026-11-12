package me.enivzoy.anilife.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeCssTest {
    @Test
    fun defaultStylesheetDefinesEveryToken() {
        val parsed = ThemeCss.parse(ThemeCss.defaultCss())

        assertTrue(parsed.issues.isEmpty())
        assertEquals(ThemeCss.tokens.size, parsed.colors.size)
        assertTrue(parsed.colors.keys.containsAll(ThemeCss.tokens.map { it.name }))
    }

    @Test
    fun parsesHexColorsInCssByteOrder() {
        val parsed = ThemeCss.parse(
            """
            :root {
              --six: #87D66A;
              --eight: #11223380;
              --short: #abc;
              --shade: #abcd;
            }
            """.trimIndent(),
        )

        assertTrue(parsed.issues.isEmpty())
        assertEquals(0xFF87D66AL, parsed.colors["six"])
        assertEquals(0x80112233L, parsed.colors["eight"])
        assertEquals(0xFFAABBCCL, parsed.colors["short"])
        assertEquals(0xDDAABBCCL, parsed.colors["shade"])
    }

    @Test
    fun parsesRgbAndRgbaColors() {
        val parsed = ThemeCss.parse(
            """
            --solid: rgb(255, 128, 0);
            --fraction: rgba(10, 20, 30, 0.5);
            --byte-alpha: rgba(10, 20, 30, 128);
            --percent: rgb(100% 0% 0%);
            """.trimIndent(),
        )

        assertTrue(parsed.issues.isEmpty())
        assertEquals(0xFFFF8000L, parsed.colors["solid"])
        assertEquals(0x7F0A141EL, parsed.colors["fraction"])
        assertEquals(0x800A141EL, parsed.colors["byte-alpha"])
        assertEquals(0xFFFF0000L, parsed.colors["percent"])
    }

    @Test
    fun reportsInvalidValuesWithLineNumbers() {
        val parsed = ThemeCss.parse(
            """
            :root {
              --good: #000000;
              --missing:
              --broken: not-a-color;
            }
            """.trimIndent(),
        )

        assertEquals(1, parsed.colors.size)
        assertEquals(listOf(3, 4), parsed.issues.map { it.line })
        assertTrue(parsed.issues.all { !it.message.isBlank() })
    }

    @Test
    fun ignoresCommentsAndStrayBraces() {
        val parsed = ThemeCss.parse(
            """
            /* --commented: #FFFFFF; */
            :root {
              --visible: #010203;
            }
            """.trimIndent(),
        )

        assertTrue(parsed.issues.isEmpty())
        assertEquals(mapOf("visible" to 0xFF010203L), parsed.colors)
    }

    @Test
    fun formatsColorsBackToCss() {
        assertEquals("#87D66A", ThemeCss.toHex(0xFF87D66AL))
        assertEquals("#11223380", ThemeCss.toHex(0x80112233L))
        assertNull(ThemeCss.colorOf("hsl(120, 50%, 50%)"))
        assertEquals(0xFFAABBCCL, ThemeCss.colorOf("#ABC"))
    }

    @Test
    fun theSettingsWindowSharesTheAppPalette() {
        assertTrue(ThemeCss.tokens.none { it.name.startsWith("settings-") })
    }

    @Test
    fun aPickedColorReplacesTheTokenItBelongsTo() {
        val css = """
            :root {
              --primary: #87D66A;
              --background: #0A0F0A;
            }
        """.trimIndent()

        val updated = setToken(css, "primary", "#FF0000")

        assertEquals("#FF0000", ThemeCss.parse(updated).colors["primary"]?.let { ThemeCss.toHex(it) })
        assertEquals(0xFF0A0F0AL, ThemeCss.parse(updated).colors["background"])
    }

    @Test
    fun aPickedColorIsAddedWhenTheTokenIsMissing() {
        val css = """
            :root {
              --primary: #87D66A;
            }
        """.trimIndent()

        val updated = setToken(css, "background", "#101010")

        assertEquals(0xFF101010L, ThemeCss.parse(updated).colors["background"])
        assertEquals(0xFF87D66AL, ThemeCss.parse(updated).colors["primary"])
    }
}
