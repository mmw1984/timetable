package com.timetable.wear.tiles

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
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
import com.timetable.wear.complications.formatCountdown
import com.timetable.wear.complications.isActiveTimedPeriod
import com.timetable.wear.complications.parseSubject
import com.timetable.wear.complications.remainingSecondsFor
import com.timetable.wear.complications.secondsUntil
import com.timetable.wear.data.model.PeriodInfo
import com.timetable.wear.data.model.TimetableType
import com.timetable.wear.engine.TimetableEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

@AndroidEntryPoint
class CountdownTileService : TileService() {

    @Inject lateinit var engine: TimetableEngine
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val snapshot = engine.getTodaySnapshot()
            if (snapshot == null) {
                return@future buildTile(loadingLayout(requestParams), freshnessMillis = 60_000L)
            }
            // Build per-minute timeline entries so 15:12 → 15:11 → ... updates without re-request
            val layouts = mutableListOf<LayoutElement>()
            val baseSnapshot = snapshot
            // Determine target for countdown: current if timed else next
            val isTimedBase = isActiveTimedPeriod(baseSnapshot.currentPeriod) && baseSnapshot.currentPeriod.end.isNotBlank()
            val baseRemaining = if (isTimedBase) {
                remainingSecondsFor(baseSnapshot.currentPeriod.end).coerceAtLeast(0)
            } else {
                baseSnapshot.nextPeriod?.let { secondsUntil(it.start).coerceAtLeast(0) } ?: 0
            }
            val count = (baseRemaining / 60).coerceIn(1, 30) + 1
            repeat(count.coerceAtMost(30)) { offset ->
                val remaining = (baseRemaining - offset * 60).coerceAtLeast(0)
                val layout = countdownLayoutForRemaining(baseSnapshot, requestParams, remaining)
                layouts.add(layout)
            }
            if (layouts.size == 1) {
                buildTile(layouts.first(), freshnessMillis = 60_000L)
            } else {
                buildTimelineTile(layouts, freshnessMillis = 60_000L)
            }
        }

    private fun countdownLayoutForRemaining(
        snapshot: com.timetable.wear.engine.TodaySnapshot,
        requestParams: TileRequest,
        remainingOverride: Int
    ): LayoutElement = materialScope(this, requestParams.deviceConfiguration) {
        val isNextDay = snapshot.currentPeriod.name == "下次上課"
        if (!isNextDay && (snapshot.timetableType == TimetableType.NONE || snapshot.dayCycle == null)) {
            return@materialScope primaryLayout(
                mainSlot = {
                    Column.Builder()
                        .addContent(text("目前沒有課堂".layoutString, typography = Typography.TITLE_MEDIUM))
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
        if (isNextDay) {
            return@materialScope primaryLayout(
                titleSlot = { text("下次上課 · Day ${snapshot.dayCycle}".layoutString, typography = Typography.TITLE_SMALL) },
                mainSlot = {
                    Column.Builder()
                        .addContent(text(snapshot.currentPeriod.subject.layoutString, typography = Typography.BODY_SMALL))
                        .addContent(text("點擊查看課表".layoutString, typography = Typography.BODY_SMALL))
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

        val current = snapshot.currentPeriod
        val isTimed = isActiveTimedPeriod(current) && current.end.isNotBlank()
        val periodLabel: String
        val countdownText: String
        if (isTimed) {
            countdownText = formatCountdown(remainingOverride)
            periodLabel = when (current.type) {
                PeriodInfo.PeriodType.PERIOD -> "${current.name} · ${parseSubject(current.subject)}"
                PeriodInfo.PeriodType.BREAK_TIME, PeriodInfo.PeriodType.ASSEMBLY -> current.name
                else -> current.name.ifBlank { "空堂時間" }
            }
        } else {
            // FREE → show countdown to next
            val next = snapshot.nextPeriod
            countdownText = if (next == null) "--:--" else formatCountdown(remainingOverride)
            periodLabel = next?.let {
                when (it.type) {
                    PeriodInfo.PeriodType.PERIOD -> "下一堂 ${parseSubject(it.subject)} ${it.start}"
                    else -> "下一堂 ${it.name} ${it.start}"
                }
            } ?: "已放學"
        }

        val nextLabel = if (isTimed) {
            snapshot.nextPeriod?.let { next ->
                when (next.type) {
                    PeriodInfo.PeriodType.PERIOD -> "下一堂 ${parseSubject(next.subject)} ${next.start}"
                    else -> "下一堂 ${next.name} ${next.start}"
                }
            } ?: "已放學"
        } else {
            "" // already showing next as main when FREE, hide duplicate
        }

        primaryLayout(
            titleSlot = { text("Day ${snapshot.dayCycle}".layoutString, typography = Typography.TITLE_SMALL) },
            mainSlot = {
                // No rings per user request: big MM:SS text only
                Column.Builder()
                    .addContent(text(countdownText.layoutString, typography = Typography.DISPLAY_MEDIUM))
                    .addContent(text(periodLabel.layoutString, typography = Typography.TITLE_SMALL))
                    .apply { if (nextLabel.isNotBlank()) addContent(text(nextLabel.layoutString, typography = Typography.BODY_SMALL)) }
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

}
