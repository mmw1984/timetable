package com.timetable.wear.tiles

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
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
                        onClick = ModifiersBuilders.Clickable.Builder().setOnClick(ActionBuilders.launchAction(ComponentName("com.timetable.wear", "com.timetable.wear.MainActivity"))).build(),
                        labelContent = { text("開啟應用".layoutString) }
                    )
                }
            )
        }

    private fun fullLayout(snapshot: com.timetable.wear.engine.TodaySnapshot, requestParams: TileRequest): LayoutElement =
        materialScope(this, requestParams.deviceConfiguration) {
            val isNextDay = snapshot.currentPeriod.name == "下次上課"
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
                            onClick = ModifiersBuilders.Clickable.Builder().setOnClick(ActionBuilders.launchAction(ComponentName("com.timetable.wear", "com.timetable.wear.MainActivity"))).build(),
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
            val headerTitle = if (isNextDay) {
                "下次上課·Day${snapshot.dayCycle}·$typeShort"
            } else {
                "完整課表·Day${snapshot.dayCycle}·$typeShort"
            }

            val currentKey = snapshot.scheduleItems.firstOrNull {
                it.start == snapshot.currentPeriod.start && it.end == snapshot.currentPeriod.end && it.start.isNotBlank()
            }?.stableKey

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
