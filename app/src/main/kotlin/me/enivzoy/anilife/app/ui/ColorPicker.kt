package me.enivzoy.anilife.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import javax.swing.JColorChooser

/**
 * A colour chooser for the theme editor.
 *
 * The picker itself is Swing's, which every desktop JVM already has; the swatch and the button
 * around it are Compose, so it looks like the rest of the app.
 */
@Composable
fun ColorPickerButton(
    color: Color,
    label: String,
    onPicked: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, AniLifeColors.Border, RoundedCornerShape(9.dp))
            .clickable {
                val picked = JColorChooser.showDialog(
                    null,
                    "Pick $label",
                    color.toAwtColor(),
                    true,
                )
                if (picked != null) onPicked(Color(picked.red, picked.green, picked.blue, picked.alpha))
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, AniLifeColors.Border, CircleShape),
        )
        Text(label, color = AniLifeColors.Foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * The palette as a row of swatches, each showing the colour it currently resolves to. Tapping one
 * opens the picker for that token.
 */
@Composable
fun PaletteStrip(
    colors: Map<String, Long>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ThemeCss.tokens) { token ->
            val argb = colors[token.name] ?: ThemeCss.colorOf(token.default) ?: 0xFF000000L
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.width(72.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ThemeCss.color(argb))
                        .border(1.dp, AniLifeColors.Border, RoundedCornerShape(8.dp))
                        .clickable { onPick(token.name) },
                )
                Text(
                    token.name,
                    color = AniLifeColors.MutedForeground,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun Color.toAwtColor(): java.awt.Color =
    java.awt.Color((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), (alpha * 255).toInt())
