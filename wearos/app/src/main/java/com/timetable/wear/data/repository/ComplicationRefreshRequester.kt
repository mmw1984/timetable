package com.timetable.wear.data.repository

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.tiles.TileService
import com.timetable.wear.complications.AllInOneDataSource
import com.timetable.wear.complications.DynamicClassDataSource
import com.timetable.wear.tiles.CountdownTileService
import com.timetable.wear.tiles.FullScheduleTileService

interface ComplicationRefreshRequester {
    fun requestAll()
}

class WearComplicationRefreshRequester(
    private val context: Context
) : ComplicationRefreshRequester {
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
        // Also refresh tiles when timetable data changes
        listOf(
            CountdownTileService::class.java,
            FullScheduleTileService::class.java
        ).forEach { tile ->
            runCatching { TileService.getUpdater(context).requestUpdate(tile) }
        }
    }
}
