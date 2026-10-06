package com.timetable.wear.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline + Startup Profiles.
 *
 * Run on a rooted device or Wear OS emulator (AOSP image):
 *   ./gradlew :app:generateReleaseBaselineProfile
 *
 * The startup journey (marked `includeInStartupProfile`) covers cold start to
 * the fully-drawn home list (see `ReportDrawnWhen` in HomeScreen); the scroll
 * journey adds the schedule list to the runtime profile. Regenerate whenever
 * startup code paths change significantly.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = "com.timetable.wear",
        includeInStartupProfile = true
    ) {
        // Cold start to first frame.
        startActivityAndWait()
        // Wait until the bundled/cached timetable has rendered.
        device.wait(Until.hasObject(By.text("查看一週")), 10_000)
    }

    @Test
    fun scrollSchedule() = rule.collect(
        packageName = "com.timetable.wear",
        includeInStartupProfile = false
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("查看一週")), 10_000)
        // Exercise the schedule list so scrolling frames are pre-compiled.
        device.findObject(By.text("查看一週"))?.click()
        device.waitForIdle()
        pressBack()
        device.waitForIdle()
    }
}
