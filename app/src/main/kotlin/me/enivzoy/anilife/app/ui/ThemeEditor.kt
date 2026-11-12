package me.enivzoy.anilife.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Plain-text theme editor. The palette is stored as CSS-like custom properties and applied live while
 * typing, so every surface in the app can be recolored without touching code.
 */
@Composable
fun ThemeEditor(
    css: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(css.ifBlank { ThemeCss.defaultCss() }) }
    var dirty by remember { mutableStateOf(false) }
    val parsed = remember(draft) { ThemeCss.parse(draft) }

    LaunchedEffect(draft) {
        if (!dirty) return@LaunchedEffect
        delay(350)
        onChange(draft)
        dirty = false
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Edit the palette as CSS custom properties. Changes apply as you type and are saved automatically.",
            color = AniLifeColors.MutedForeground,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

        ThemePreview(parsed)

        Box(
            Modifier.fillMaxWidth()
                .height(330.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AniLifeColors.Background)
                .border(1.dp, AniLifeColors.Border, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    dirty = true
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = AniLifeColors.Foreground,
                ),
                cursorBrush = SolidColor(AniLifeColors.Primary),
                visualTransformation = CssHighlight,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = when {
                    parsed.issues.isNotEmpty() ->
                        "${parsed.issues.size} problem${if (parsed.issues.size == 1) "" else "s"} in the stylesheet"
                    css.isBlank() -> "Using the default palette · ${parsed.colors.size} tokens"
                    else -> "${parsed.colors.size} of ${ThemeCss.tokens.size} tokens defined"
                },
                color = if (parsed.issues.isEmpty()) AniLifeColors.MutedForeground else AniLifeColors.Destructive,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    val text = draft
                    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
                },
            ) { Text("Copy") }
            OutlinedButton(
                onClick = {
                    draft = ThemeCss.defaultCss()
                    dirty = true
                },
            ) { Text("Reset") }
        }

        if (parsed.issues.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                parsed.issues.take(6).forEach { issue ->
                    Text(
                        "Line ${issue.line}: ${issue.message}",
                        color = AniLifeColors.Destructive,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Text("Tokens", color = AniLifeColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        TokenGrid(parsed.colors) { token ->
            val current = draft.trimEnd()
            val line = "  --${token.name}: ${token.default};"
            draft = if (current.endsWith("}")) {
                current.dropLast(1).trimEnd() + "\n$line\n}"
            } else {
                "$current\n$line"
            }
            dirty = true
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenGrid(
    overrides: Map<String, Long>,
    onInsert: (ThemeCss.Token) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ThemeCss.tokens.forEach { token ->
            val argb = overrides[token.name] ?: ThemeCss.colorOf(token.default) ?: 0xFF000000
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(AniLifeColors.Muted)
                    .clickable { onInsert(token) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(ThemeCss.color(argb)))
                Text("--${token.name}", color = AniLifeColors.Foreground, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ThemePreview(parsed: ThemeCss.Parsed) {
    fun token(name: String, fallback: Long = 0xFF000000) =
        ThemeCss.color(parsed.colors[name] ?: fallback)

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(token("background", 0xFF0A0F0A))
            .border(1.dp, AniLifeColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(token("card", 0xFF111A11))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Preview", color = token("card-foreground", 0xFFF8F8F8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Muted text", color = token("muted-foreground", 0xFFA1A1A1), fontSize = 11.sp)
        }
        Box(
            Modifier.clip(RoundedCornerShape(9.dp))
                .background(token("primary", 0xFF87D66A))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text("Play", color = token("primary-foreground", 0xFF0A0F0A), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(2.dp))
    }
}

private object CssHighlight : VisualTransformation {
    private val names = Regex("(--[a-zA-Z0-9-]+)")
    private val values = Regex("(#[0-9a-fA-F]{3,8}|rgba?\\([^)]*\\))")

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = buildAnnotatedString {
            append(text.text)
            names.findAll(text.text).forEach { match ->
                addStyle(SpanStyle(color = AniLifeColors.Primary), match.range.first, match.range.last + 1)
            }
            values.findAll(text.text).forEach { match ->
                addStyle(SpanStyle(color = AniLifeColors.Score), match.range.first, match.range.last + 1)
            }
        }
        return TransformedText(builder, OffsetMapping.Identity)
    }
}
