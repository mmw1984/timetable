package com.timetable.wear.complications

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.timetable.shared.data.repository.ScheduleUpdateListener
import com.timetable.wear.widgets.CountdownWidget
import com.timetable.wear.widgets.ScheduleWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pokes every ambient surface after timetable data changes: the two
 * complication providers and the Glance tiles widgets.
 */
class WearScheduleUpdateListener(
    private val context: Context
) : ScheduleUpdateListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onScheduleUpdated() {
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
