package com.timetable.wear.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.compose.remote.creation.compose.action.pendingIntentAction
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import com.timetable.wear.MainActivity

/**
 * Fallback palette (Figma pages 20/21, M3 Tiles Design Kit): used when the
 * renderer cannot resolve dynamic system colors (old watches).
 */
internal const val W_COLOR_WHITE: Long = 0xFFFFFFFFL
internal const val W_COLOR_LEFT_BG: Long = 0xFFE9DDFFL
internal const val W_COLOR_LEFT_TEXT: Long = 0xFF210F48L
internal const val W_COLOR_PILL_TOP_BG: Long = 0xFF332E3CL
internal const val W_COLOR_PILL_TOP_TEXT: Long = 0xFFF5EEFBL
internal const val W_COLOR_PILL_BOTTOM_BG: Long = 0xFF4D3D76L
internal const val W_COLOR_PILL_BOTTOM_TEXT: Long = 0xFFF6EDFFL
internal const val W_COLOR_ON_SURFACE: Long = 0xFFE6E0E9L

/**
 * M3 Expressive dynamic colors for Glance widgets. RemoteCompose cannot
 * reference theme colors directly in this library version, so the widget
 * resolves the Android system tokens on-device on every update (the 30s
 * ticker picks up watch-face theme changes) and falls back to the Figma
 * kit values where tokens are missing.
 */
data class WidgetPalette(
    val leftBg: Long,
    val leftText: Long,
    val pillTopBg: Long,
    val pillTopText: Long,
    val pillBottomBg: Long,
    val pillBottomText: Long,
    val onSurface: Long
)

/** Figma kit palette as a static fallback (Figma pages 20/21). */
fun staticPalette(): WidgetPalette = WidgetPalette(
    leftBg = W_COLOR_LEFT_BG,
    leftText = W_COLOR_LEFT_TEXT,
    pillTopBg = W_COLOR_PILL_TOP_BG,
    pillTopText = W_COLOR_PILL_TOP_TEXT,
    pillBottomBg = W_COLOR_PILL_BOTTOM_BG,
    pillBottomText = W_COLOR_PILL_BOTTOM_TEXT,
    onSurface = W_COLOR_ON_SURFACE
)

fun paletteFor(context: Context): WidgetPalette {
    // Same source as the app theme (TimetableTheme): the system dynamic
    // scheme when available, else the library default. Re-resolved on every
    // widget update, so the 30s ticker follows watch-face theme changes.
    val scheme = try {
        dynamicColorScheme(context)
    } catch (_: Exception) {
        null
    } ?: ColorScheme()
    fun argb(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
    return WidgetPalette(
        leftBg = argb(scheme.primaryContainer),
        leftText = argb(scheme.onPrimaryContainer),
        pillTopBg = argb(scheme.surfaceContainer),
        pillTopText = argb(scheme.onSurface),
        pillBottomBg = argb(scheme.primaryDim),
        pillBottomText = argb(scheme.onPrimary),
        onSurface = argb(scheme.onSurface)
    ).also {
        android.util.Log.d(
            "WidgetPalette",
            "leftBg=${it.leftBg.toString(16)} leftText=${it.leftText.toString(16)} " +
                "top=${it.pillTopBg.toString(16)}/${it.pillTopText.toString(16)} " +
                "bottom=${it.pillBottomBg.toString(16)}/${it.pillBottomText.toString(16)}"
        )
    }
}

/**
 * Height taken by the system title slot above widget content (the widget
 * label, e.g. "課堂倒數"): tile containers report the full 137x89dp but the
 * row must be shorter or the bottom pill is clipped (watch7small).
 */
internal const val TILE_TITLE_CHROME_DP = 28f

/** Tap anywhere opens the app. */
@RemoteComposable
@Composable
internal fun openAppAction(): Action = pendingIntentAction { context ->
    PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

@RemoteComposable
@Composable
fun WidgetCenterText(text: String, sizeSp: Int, colorArgb: Long) {
    RemoteText(
        text = text.rs,
        color = Color(colorArgb).rc,
        fontSize = sizeSp.rsp
    )
}

/** Start the 30s widget ticker ( foreground service keeps countdown live ). */
internal fun startWidgetTicker(context: Context) {
    WidgetTickService.start(context)
}
