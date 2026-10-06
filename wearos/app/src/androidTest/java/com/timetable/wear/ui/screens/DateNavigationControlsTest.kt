package com.timetable.wear.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.timetable.wear.engine.HomeScheduleState
import com.timetable.wear.ui.theme.TimetableTheme
import org.junit.Rule
import org.junit.Test

class DateNavigationControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun datesAtTheLowerBoundaryDisablePreviousNavigation() {
        composeRule.setContent {
            TimetableTheme {
                DateNavigationControls(
                    state = HomeScheduleState(
                        selectedDateDisplay = "9月3日 星期四",
                        canNavigatePrevious = false,
                        canNavigateNext = true,
                        isLoading = false
                    ),
                    onPrevious = {},
                    onNext = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("上一個上課日").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("下一個上課日").assertIsEnabled()
    }
}
