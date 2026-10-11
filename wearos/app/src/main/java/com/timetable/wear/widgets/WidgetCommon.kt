package com.timetable.wear.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.remote.creation.compose.action.pendingIntentAction
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import com.timetable.wear.MainActivity

/** Kit palette (Figma pages 20/21, M3 Tiles Design Kit). */
internal const val W_COLOR_WHITE: Long = 0xFFFFFFFFL
internal const val W_COLOR_LEFT_BG: Long = 0xFFE9DDFFL
internal const val W_COLOR_LEFT_TEXT: Long = 0xFF210F48L
internal const val W_COLOR_PILL_TOP_BG: Long = 0xFF332E3CL
internal const val W_COLOR_PILL_TOP_TEXT: Long = 0xFFF5EEFBL
internal const val W_COLOR_PILL_BOTTOM_BG: Long = 0xFF4D3D76L
internal const val W_COLOR_PILL_BOTTOM_TEXT: Long = 0xFFF6EDFFL
internal const val W_COLOR_ON_SURFACE: Long = 0xFFE6E0E9L

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
