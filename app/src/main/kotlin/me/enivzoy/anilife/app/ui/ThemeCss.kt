package me.enivzoy.anilife.app.ui

import androidx.compose.ui.graphics.Color

/**
 * CSS-like theme tokens. Values are stored as raw CSS in [me.enivzoy.anilife.app.data.AppSettings.themeCss]
 * and parsed on every change, so the palette can be edited as plain text.
 *
 * Supported syntax:
 *   --background: #0A0F0A;
 *   --scrim: #000000CC;            (RRGGBBAA, CSS order)
 *   --border: rgb(255, 255, 255);  or rgba(255, 255, 255, 0.1)
 */
object ThemeCss {

    data class Token(val name: String, val label: String, val default: String)

    data class Issue(val line: Int, val message: String)

    data class Parsed(val colors: Map<String, Long>, val issues: List<Issue>) {
        val valid: Boolean get() = issues.isEmpty()
    }

    val tokens: List<Token> = listOf(
        Token("background", "App background", "#0A0F0A"),
        Token("foreground", "App text", "#F8F8F8"),
        Token("card", "Cards and panels", "#111A11"),
        Token("card-foreground", "Text on cards", "#F8F8F8"),
        Token("card-hover", "Card hover", "#1A261A"),
        Token("muted", "Muted surfaces", "#1A261A"),
        Token("muted-foreground", "Muted text", "#A1A1A1"),
        Token("border", "Borders", "#FFFFFF1A"),
        Token("input", "Input fields", "#FFFFFF26"),
        Token("primary", "Primary / buttons", "#87D66A"),
        Token("primary-foreground", "Text on primary", "#0A0F0A"),
        Token("secondary", "Secondary", "#1A261A"),
        Token("secondary-foreground", "Text on secondary", "#F8F8F8"),
        Token("accent", "Accent", "#1A261A"),
        Token("accent-foreground", "Text on accent", "#F8F8F8"),
        Token("destructive", "Errors", "#F87171"),
        Token("sidebar", "Sidebar background", "#080F08"),
        Token("sidebar-accent", "Sidebar selection", "#1A261A"),
        Token("score", "Score badges", "#87D66A"),
        Token("success", "Success", "#57F287"),
        Token("warning", "Warning", "#FBBF24"),
        Token("scrim", "Modal scrim", "#000000CC"),
        Token("chip", "Chips", "#0A0F0AE6"),
        Token("chip-overlay", "Chips over artwork", "#262626CC"),
        Token("overlay", "Detail overlay", "#171717CC"),
        Token("toast", "Toasts", "#171717D9"),
        Token("toast-danger", "Error toasts", "#B91C1CE6"),
        Token("shadow", "Soft shadow", "#0000004D"),
        Token("checkbox", "Checkboxes", "#87D66A"),
        Token("checkbox-tick", "Checkbox tick", "#0A0F0A"),
        Token("download-progress", "Download progress", "#87D66A"),
        Token("seekbar-top", "Seek bar top", "#333333"),
        Token("seekbar-bottom", "Seek bar bottom", "#111111"),
        Token("download-surface", "Downloads surface", "#1E2024"),
        Token("download-surface-raised", "Downloads raised", "#25272C"),
        Token("download-outline", "Downloads outline", "#3A3D43"),
        Token("download-text", "Downloads text", "#E8E9EC"),
        Token("download-muted", "Downloads muted text", "#A2A5AC"),
        Token("download-accent", "Downloads accent", "#FF8FC9"),
        Token("download-accent-foreground", "Downloads accent text", "#291522"),
        Token("settings-bg", "Settings background", "#000000"),
        Token("settings-panel", "Settings panel", "#090909"),
        Token("settings-border", "Settings panel border", "#1A1A1A"),
        Token("settings-divider", "Settings divider", "#212222"),
        Token("settings-selected", "Settings selection", "#222222"),
        Token("settings-selected-foreground", "Settings selection text", "#FAFAFA"),
        Token("settings-control", "Settings controls", "#0B0B0B"),
        Token("settings-control-border", "Settings control border", "#2A2A2A"),
        Token("settings-control-active", "Active control", "#E5E5E5"),
        Token("settings-control-active-foreground", "Active control text", "#1B1B1B"),
        Token("settings-switch-track", "Switch track when off", "#272727"),
        Token("settings-switch-knob", "Switch knob when off", "#E5E5E5"),
    )

    fun defaultCss(): String = buildString {
        appendLine(":root {")
        tokens.forEach { appendLine("  --${it.name}: ${it.default};") }
        appendLine("}")
    }

    fun parse(css: String): Parsed {
        val colors = LinkedHashMap<String, Long>()
        val issues = mutableListOf<Issue>()
        val cleaned = stripComments(css)
        cleaned.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || !line.contains(':')) return@forEachIndexed
            if (line.endsWith("{") || line == "}") return@forEachIndexed
            val colon = line.indexOf(':')
            val name = line.substring(0, colon).trim().removePrefix("--").trim().lowercase()
            val value = line.substring(colon + 1).trim().removeSuffix(";").trim()
            if (name.isEmpty()) return@forEachIndexed
            if (value.isEmpty()) {
                issues += Issue(index + 1, "Missing value for --$name")
                return@forEachIndexed
            }
            val color = parseColor(value)
            if (color == null) {
                issues += Issue(index + 1, "Invalid color \"$value\"")
                return@forEachIndexed
            }
            colors[name] = color
        }
        return Parsed(colors, issues)
    }

    /** Parses a single CSS color, exposed so the editor can preview defaults the user has not overridden. */
    fun colorOf(value: String): Long? = parseColor(value)

    fun toHex(argb: Long): String {
        val alpha = (argb ushr 24) and 0xFF
        val rgb = argb and 0xFFFFFF
        return if (alpha == 0xFFL) "#%06X".format(rgb) else "#%06X%02X".format(rgb, alpha)
    }

    fun color(argb: Long): Color = Color(argb)

    private fun stripComments(css: String): String =
        Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(css) { match ->
            "\n".repeat(match.value.count { it == '\n' })
        }

    private fun parseColor(value: String): Long? {
        val raw = value.trim().lowercase()
        if (raw == "transparent") return 0x00000000L
        if (raw.startsWith("#")) return parseHex(raw.substring(1))
        if (raw.startsWith("rgb")) return parseRgb(raw)
        return null
    }

    private fun parseHex(raw: String): Long? {
        if (raw.any { it !in "0123456789abcdef" }) return null
        fun part(text: String) = text.toLong(16)
        return when (raw.length) {
            3 -> {
                val r = part("${raw[0]}${raw[0]}")
                val g = part("${raw[1]}${raw[1]}")
                val b = part("${raw[2]}${raw[2]}")
                0xFF000000L or (r shl 16) or (g shl 8) or b
            }
            4 -> {
                val r = part("${raw[0]}${raw[0]}")
                val g = part("${raw[1]}${raw[1]}")
                val b = part("${raw[2]}${raw[2]}")
                val a = part("${raw[3]}${raw[3]}")
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            6 -> 0xFF000000L or part(raw)
            8 -> {
                val rgb = part(raw.substring(0, 6))
                val a = part(raw.substring(6, 8))
                (a shl 24) or rgb
            }
            else -> null
        }
    }

    private fun parseRgb(raw: String): Long? {
        val open = raw.indexOf('(')
        val close = raw.lastIndexOf(')')
        if (open == -1 || close <= open) return null
        val body = raw.substring(open + 1, close)
        val parts = body.replace('/', ' ').split(',', ' ').map(String::trim).filter(String::isNotEmpty)
        if (parts.size !in 3..4) return null
        fun channel(text: String): Int? {
            val numeric = if (text.endsWith("%")) {
                text.dropLast(1).toFloatOrNull()?.times(255f / 100f)
            } else {
                text.toFloatOrNull()
            }
            return numeric?.coerceIn(0f, 255f)?.toInt()
        }
        val r = channel(parts[0]) ?: return null
        val g = channel(parts[1]) ?: return null
        val b = channel(parts[2]) ?: return null
        val a = when {
            parts.size == 3 -> 255
            parts[3].endsWith("%") -> parts[3].dropLast(1).toFloatOrNull()?.let { (it * (255f / 100f)).toInt() }
            else -> parts[3].toFloatOrNull()?.let { if (it <= 1f) (it * 255f).toInt() else it.toInt() }
        } ?: return null
        return ((a.coerceIn(0, 255).toLong()) shl 24) or
            (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }
}
