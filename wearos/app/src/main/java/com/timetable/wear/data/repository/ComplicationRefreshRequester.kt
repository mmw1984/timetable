package com.timetable.wear.data.repository

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.timetable.wear.complications.AllInOneDataSource
import com.timetable.wear.complications.DynamicClassDataSource
import com.timetable.wear.widgets.CountdownWidget
import com.timetable.wear.widgets.ScheduleWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface ComplicationRefreshRequester {
    fun requestAll()
}

class WearComplicationRefreshRequester(
    private val context: Context
) : ComplicationRefreshRequester {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun requestAll() {
        val services = listOf(
            DynamicClassDataSource::class.java,
            AllInOneDataSource::class.java
        )
        services.forEach { service ->
            runCatching {
                ComplicationDataSourceUpdateRequester.create(
                    context,
                    ComponentName(context, service)
                ).requestUpdateAll()
            }
        }
        // Also refresh widgets when timetable data changes
        scope.launch {
            runCatching { CountdownWidget().triggerUpdateAll(context) }
            runCatching { ScheduleWidget().triggerUpdateAll(context) }
        }
    }
}
