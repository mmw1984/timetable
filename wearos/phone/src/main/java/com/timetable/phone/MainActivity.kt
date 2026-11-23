package com.timetable.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.timetable.phone.ui.PhoneApp
import com.timetable.phone.ui.theme.TimetablePhoneTheme
import com.timetable.phone.widget.NextClassWidget
import com.timetable.shared.engine.TimetableEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var engine: TimetableEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimetablePhoneTheme {
                PhoneApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Restart the 1s ticker when coming back to the foreground, and give the
        // home-screen widget a fresh "尚餘 N 分鐘" while we are at it.
        lifecycleScope.launch {
            engine.start()
            runCatching { NextClassWidget().updateAll(applicationContext) }
        }
    }

    override fun onPause() {
        // Stop the 1s ticker while backgrounded: it would otherwise keep the
        // CPU awake. The widget does not use the ticker.
        engine.stop()
        super.onPause()
    }
}
