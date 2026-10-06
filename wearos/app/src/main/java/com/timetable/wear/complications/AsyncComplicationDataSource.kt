package com.timetable.wear.complications

import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

abstract class AsyncComplicationDataSource : ComplicationDataSourceService() {
    protected val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        requestScope.cancel()
        super.onDestroy()
    }
}
