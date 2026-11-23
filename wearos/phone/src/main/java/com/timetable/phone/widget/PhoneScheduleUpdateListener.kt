package com.timetable.phone.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.timetable.shared.data.repository.ScheduleUpdateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Refreshes the home-screen widget after timetable data changes (cache restore
 * or remote refresh).
 */
class PhoneScheduleUpdateListener(
    private val context: Context
) : ScheduleUpdateListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onScheduleUpdated() {
        scope.launch {
            runCatching { NextClassWidget().updateAll(context) }
        }
    }
}
