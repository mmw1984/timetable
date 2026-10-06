package com.timetable.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme

/**
 * Shared dense text styles for the small Wear screen.
 *
 * Every screen previously built these via `typography.xxx.copy(...)` inline at
 * each [androidx.wear.compose.material3.Text] call site, allocating new
 * [TextStyle] objects on every recomposition. These helpers build each style
 * once per composition instead.
 */
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val CenteredLineHeight =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

@Composable
fun denseLabelSmall(): TextStyle {
    val base = MaterialTheme.typography.labelSmall
    return remember(base) {
        base.copy(platformStyle = NoFontPadding, lineHeightStyle = CenteredLineHeight)
    }
}

@Composable
fun denseLabelSmall11(): TextStyle {
    val base = MaterialTheme.typography.labelSmall
    return remember(base) {
        base.copy(
            fontSize = 11.sp,
            platformStyle = NoFontPadding,
            lineHeightStyle = CenteredLineHeight
        )
    }
}

@Composable
fun denseBodySmall(): TextStyle {
    val base = MaterialTheme.typography.bodySmall
    return remember(base) {
        base.copy(platformStyle = NoFontPadding, lineHeightStyle = CenteredLineHeight)
    }
}

@Composable
fun denseTitleSmall(): TextStyle {
    val base = MaterialTheme.typography.titleSmall
    return remember(base) {
        base.copy(platformStyle = NoFontPadding, lineHeightStyle = CenteredLineHeight)
    }
}

@Composable
fun denseTitleLarge(): TextStyle {
    val base = MaterialTheme.typography.titleLarge
    return remember(base) {
        base.copy(platformStyle = NoFontPadding, lineHeightStyle = CenteredLineHeight)
    }
}
