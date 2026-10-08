package com.timetable.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

@Composable
fun TimetableTheme(
    content: @Composable () -> Unit
) {
    // Material 3 Expressive: take colors from the system wallpaper theme when available,
    // falling back to the library default scheme otherwise.
    // Remembered so dynamicColorScheme isn't re-queried on every recomposition.
    val context = LocalContext.current
    val colorScheme = androidx.compose.runtime.remember(context) {
        dynamicColorScheme(context) ?: ColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
