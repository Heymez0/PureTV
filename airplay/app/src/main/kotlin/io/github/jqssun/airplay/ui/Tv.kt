package io.github.jqssun.airplay.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.jqssun.airplay.ui.theme.LocalPureTv

fun Context.isTvDevice(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType ==
        Configuration.UI_MODE_TYPE_TELEVISION

@Composable
fun isTv(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.isTvDevice() }
}

fun Modifier.dpadFocus(shape: Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }
        .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
}

/** The launcher's panel: a deep fill and a hairline rim that is brighter at the top (SettingsSurface.panel). */
fun Modifier.tvPanel(shape: Shape = RoundedCornerShape(18.dp)): Modifier = composed {
    val s = LocalPureTv.current
    val rim = if (s.dark) Brush.verticalGradient(listOf(Color(0x30FFFFFF), s.rim)) else SolidColor(s.rim)
    background(s.panel, shape).border(1.dp, rim, shape)
}

/** The launcher's backdrop: a deep surface lit by a warm light from the top right and a cool one from the bottom left. */
fun Modifier.tvBackdrop(): Modifier = composed {
    val dark = LocalPureTv.current.dark
    drawBehind {
        drawRect(if (dark) Color(0xFF0B0B0E) else Color(0xFFF2F0EC))
        val reach = maxOf(size.width, size.height)
        drawRect(Brush.radialGradient(listOf(if (dark) Color(0x66584426) else Color(0xB3F0DFC0), Color.Transparent),
            center = Offset(size.width * 0.86f, size.height * 0.04f), radius = reach * 0.75f))
        drawRect(Brush.radialGradient(listOf(if (dark) Color(0x6612283C) else Color(0x99D6E2EA), Color.Transparent),
            center = Offset(size.width * 0.08f, size.height * 0.96f), radius = reach * 0.7f))
    }
}

/** A focusable settings row, rounded like the launcher's; reports focus for [tvRowColors]. */
fun Modifier.tvRow(onFocus: (Boolean) -> Unit): Modifier = composed {
    clip(MaterialTheme.shapes.small).onFocusChanged { onFocus(it.isFocused) }
}

/** Settings rows focus like the launcher's: a filled row with inverted text, not a ring. */
@Composable
fun tvRowColors(focused: Boolean): ListItemColors {
    val s = LocalPureTv.current
    return if (focused) ListItemDefaults.colors(containerColor = s.focusFill, headlineColor = s.focusInk,
        supportingColor = s.focusDim, leadingIconColor = s.focusInk, trailingIconColor = s.focusInk)
    else ListItemDefaults.colors(containerColor = Color.Transparent)
}

/** The launcher's switch: gold when on, grey when off, light thumb. */
@Composable
fun tvSwitchColors(): SwitchColors {
    val s = LocalPureTv.current
    val thumb = Color(0xFFF5F5F7)
    return SwitchDefaults.colors(
        checkedThumbColor = thumb, checkedTrackColor = s.on, checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = thumb, uncheckedTrackColor = s.off, uncheckedBorderColor = Color.Transparent)
}

fun Modifier.dpadAdjust(onLeft: () -> Unit, onRight: () -> Unit): Modifier = composed {
    val focus = LocalFocusManager.current
    onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
            Key.DirectionLeft -> { onLeft(); true }
            Key.DirectionRight -> { onRight(); true }
            // slider
            Key.DirectionUp -> { focus.moveFocus(FocusDirection.Up); true }
            Key.DirectionDown -> { focus.moveFocus(FocusDirection.Down); true }
            else -> false
        }
    }
}
