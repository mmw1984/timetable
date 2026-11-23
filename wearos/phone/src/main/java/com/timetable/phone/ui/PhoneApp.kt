package com.timetable.phone.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Phone bottom bar ↔ tablet/foldable navigation rail, handled by
 * NavigationSuiteScaffold from the window size class.
 */
@Composable
fun PhoneApp() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val items = listOf(
        Triple("今日", Icons.Default.Home, 0),
        Triple("本週", Icons.Default.DateRange, 1),
        Triple("設定", Icons.Default.Settings, 2)
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            items.forEach { (label, icon, index) ->
                item(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label) }
                )
            }
        }
    ) {
        when (selectedTab) {
            0 -> TodayScreen(modifier = Modifier.fillMaxSize())
            1 -> WeekScreen(modifier = Modifier.fillMaxSize())
            else -> SettingsScreen(modifier = Modifier.fillMaxSize())
        }
    }
}
