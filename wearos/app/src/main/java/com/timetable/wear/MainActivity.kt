package com.timetable.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.SwipeDismissableSceneStrategy
import com.timetable.wear.ui.screens.HomeScreen
import com.timetable.wear.ui.screens.SettingsScreen
import com.timetable.wear.ui.screens.UrlEditorScreen
import com.timetable.wear.ui.screens.WeekScreen
import com.timetable.wear.ui.theme.TimetableTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object UrlEditorRoute : NavKey

@Serializable
data object WeekRoute : NavKey

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimetableTheme {
                WearNavApp()
            }
        }
    }
}

@Composable
private fun WearNavApp() {
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(HomeRoute)
    val context = LocalContext.current
    val entryProviderFn: (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<HomeRoute> {
            HomeScreen(
                onOpenSettings = { backStack.add(SettingsRoute) },
                onOpenWeek = { backStack.add(WeekRoute) }
            )
        }
        entry<SettingsRoute> {
            SettingsScreen(onEditUrl = { backStack.add(UrlEditorRoute) })
        }
        entry<UrlEditorRoute> {
            UrlEditorScreen(onDismiss = { backStack.removeLastOrNull() })
        }
        entry<WeekRoute> {
            WeekScreen()
        }
    }
    AppScaffold {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                } else {
                    (context as? Activity)?.finish()
                }
            },
            sceneStrategies = listOf(SwipeDismissableSceneStrategy()),
            entryProvider = entryProviderFn
        )
    }
}
