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
    val colorScheme = dynamicColorScheme(LocalContext.current) ?: ColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}
