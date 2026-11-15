package io.github.jqssun.airplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Static colors and shapes match the launcher's settings (SettingsSurface), without blur or animated backgrounds.
private val TvDark = darkColorScheme(
    primary = Color(0xFFF5F5F7), onPrimary = Color(0xFF1C1C1E),
    primaryContainer = Color(0xFF303033), onPrimaryContainer = Color(0xFFF5F5F7),
    secondary = Color(0xFFA9A9AE), onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF303034), onSecondaryContainer = Color(0xFFF5F5F7),
    tertiary = Color(0xFFD2AB55),
    background = Color(0xFF151517), onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF141417), onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF242426), onSurfaceVariant = Color(0xFFA9A9AE),
    surfaceContainer = Color(0xFF1C1C1F), surfaceContainerLow = Color(0xFF18181B),
    surfaceContainerHigh = Color(0xFF232326), surfaceContainerHighest = Color(0xFF303034),
    outline = Color(0xFF5B5B60), outlineVariant = Color(0xFF3B3B3E)
)

private val TvLight = lightColorScheme(
    primary = Color(0xFF28282D), onPrimary = Color(0xFFF5F5F7),
    primaryContainer = Color(0xFFD6D3CE), onPrimaryContainer = Color(0xFF1D1D1F),
    secondary = Color(0xFF58585F), onSecondary = Color(0xFFF5F5F7),
    secondaryContainer = Color(0xFFD6D3CE), onSecondaryContainer = Color(0xFF1D1D1F),
    tertiary = Color(0xFF8A6A1E),
    background = Color(0xFFF3F3F5), onBackground = Color(0xFF1D1D1F),
    surface = Color(0xFFF5F3EF), onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFE4E1DC), onSurfaceVariant = Color(0xFF58585F),
    surfaceContainer = Color(0xFFEDEAE6), surfaceContainerLow = Color(0xFFF1EFEB),
    surfaceContainerHigh = Color(0xFFE9E6E1), surfaceContainerHighest = Color(0xFFDDDAD4),
    outline = Color(0xFFAAA7A0), outlineVariant = Color(0xFFD3CFC8)
)

/** Launcher surfaces the Material scheme has no slot for: lit panel, hairline rim, filled focus and the gold "on". */
@Immutable
data class PureTvStyle(
    val dark: Boolean,
    val panel: Color, val rim: Color, val selected: Color,
    val focusFill: Color, val focusInk: Color, val focusDim: Color,
    val on: Color, val off: Color
)

private val DarkStyle = PureTvStyle(true,
    panel = Color(0xD9161618), rim = Color(0x14FFFFFF), selected = Color(0x1AFFFFFF),
    focusFill = Color(0xFFF5F5F7), focusInk = Color(0xFF1D1D1F), focusDim = Color(0xFF505056),
    on = Color(0xFFD2AB55), off = Color(0xFF48484D))

private val LightStyle = PureTvStyle(false,
    panel = Color(0x9FFFFFFF), rim = Color(0x26000000), selected = Color(0x20000000),
    focusFill = Color(0xFF28282D), focusInk = Color(0xFFF5F5F7), focusDim = Color(0xFFD4D4DC),
    on = Color(0xFFD2AB55), off = Color(0xFFAAA7A0))

val LocalPureTv = staticCompositionLocalOf { DarkStyle }

@Composable
fun AirPlayTheme(top: Int, bottom: Int, dark: Boolean = true, content: @Composable () -> Unit) {
    // top/bottom: the launcher's former two-colour sky; the backdrop is now drawn by Modifier.tvBackdrop().
    MaterialTheme(colorScheme = if (dark) TvDark else TvLight, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp))) {
        CompositionLocalProvider(
            LocalPureTv provides if (dark) DarkStyle else LightStyle, content = content)
    }
}
