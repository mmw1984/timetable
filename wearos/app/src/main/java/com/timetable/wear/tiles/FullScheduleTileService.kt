package com.timetable.wear.tiles

import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.timetable.wear.data.model.TimetableType
import com.timetable.wear.engine.TimetableEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

@AndroidEntryPoint
class FullScheduleTileService : TileService() {

    @Inject lateinit var engine: TimetableEngine
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val snapshot = engine.getTileSnapshot() ?: engine.getTodaySnapshot()
            val layout = if (snapshot == null) {
                loadingLayout(requestParams)
            } else {
                fullLayout(snapshot, requestParams)
            }
            buildTile(layout, freshnessMillis = 300_000L)
        }

    override fun onTileResourcesRequest(
        requestParams: androidx.wear.tiles.RequestBuilders.ResourcesRequest
    ): ListenableFuture<androidx.wear.protolayout.ResourceBuilders.Resources> =
        com.google.common.util.concurrent.Futures.immediateFuture(
            resources(requestParams.version)
        )

    private fun loadingLayout(requestParams: TileRequest): LayoutElement =
        materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                mainSlot = {
                    text("載入中…".layoutString, typography = Typography.BODY_MEDIUM)
                },
                bottomSlot = {
                    textEdgeButton(
                        onClick = openAppClickable(),
                        labelContent = { text("開啟應用".layoutString) }
                    )
                }
            )
        }

    private fun fullLayout(snapshot: com.timetable.wear.engine.TodaySnapshot, requestParams: TileRequest): LayoutElement =
        materialScope(this, requestParams.deviceConfiguration) {
            val isNextDay = snapshot.isNextDay
            if (!isNextDay && (snapshot.timetableType == TimetableType.NONE || snapshot.dayCycle == null)) {
                return@materialScope primaryLayout(
                    mainSlot = {
                        Column.Builder()
                            .setWidth(expand())
                            .setHeight(expand())
                            .addContent(text("非上課日".layoutString, typography = Typography.TITLE_MEDIUM))
                            .addContent(text("今日沒有課程".layoutString, typography = Typography.BODY_SMALL))
                            .build()
                    },
                    bottomSlot = {
                        textEdgeButton(
                            onClick = openAppClickable(),
                            labelContent = { text("開啟應用".layoutString) }
                        )
                    }
                )
            }
            val typeShort = when (snapshot.timetableType) {
                TimetableType.NORMAL -> "正常"
                TimetableType.SPECIAL_A -> "特A"
                TimetableType.SPECIAL_B -> "特B"
                TimetableType.SPECIAL_C -> "特C"
                TimetableType.SPECIAL_D -> "特D"
                TimetableType.SPECIAL_E -> "特E"
                else -> ""
            }
            val dayLabel = snapshot.dayCycle?.let { "Day$it" } ?: ""
            val headerTitle = if (isNextDay) {
                // Include the date so "下次上課" is unambiguous on weekends/holidays.
                val datePart = snapshot.dateDisplay.ifBlank { dayLabel }
                "下次上課·$datePart·$dayLabel·$typeShort".trim('·')
            } else {
                "完整課表·$dayLabel·$typeShort".trim('·')
            }

            // Next-day preview is in the future: never highlight a row as "current".
            val currentKey = if (isNextDay) {
                null
            } else {
                snapshot.scheduleItems.firstOrNull {
                    it.start == snapshot.currentPeriod.start && it.end == snapshot.currentPeriod.end && it.start.isNotBlank()
                }?.stableKey
            }

            primaryLayout(
                titleSlot = {
                    text(headerTitle.layoutString, typography = Typography.TITLE_SMALL)
                },
                mainSlot = {
                    periodCardGroups(snapshot.scheduleItems, currentKey)
                }
            )
        }
}
