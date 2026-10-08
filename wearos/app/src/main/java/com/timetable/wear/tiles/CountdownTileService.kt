package com.timetable.wear.tiles

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
            // Use tile snapshot so weekends/holidays fall forward to the next school day,
            // matching FullScheduleTileService behaviour.
            val snapshot = engine.getTileSnapshot() ?: engine.getTodaySnapshot()
            if (snapshot == null) {
                return@future buildTile(loadingLayout(requestParams), freshnessMillis = 60_000L)
            }
            // Single fresh entry per request: the system re-requests on freshness expiry,
            // so the period label can never go stale across a period boundary (the old
            // 30-entry pre-built timeline kept showing the previous period with 00:00).
            // This also saves CPU/memory building 30 layouts on every tile request.
            if (snapshot.isNextDay) {
                return@future buildTile(
                    nextDayLayout(snapshot, requestParams),
                    freshnessMillis = 300_000L
                )
            }
            if (snapshot.timetableType == TimetableType.NONE || snapshot.dayCycle == null) {
                return@future buildTile(
                    emptyLayout(requestParams),
                    freshnessMillis = 300_000L
                )
            }
            val remaining = currentRemainingSeconds(snapshot)
            buildTile(
                countdownLayout(snapshot, requestParams, remaining),
                freshnessMillis = 60_000L
            )
        }

    private fun currentRemainingSeconds(snapshot: com.timetable.wear.engine.TodaySnapshot): Int {
        val current = snapshot.currentPeriod
        return if (isActiveTimedPeriod(current) && current.end.isNotBlank()) {
            remainingSecondsFor(current.end).coerceAtLeast(0)
        } else {
            snapshot.nextPeriod?.let { secondsUntil(it.start).coerceAtLeast(0) } ?: 0
        }
    }

    private fun nextDayLayout(
        snapshot: com.timetable.wear.engine.TodaySnapshot,
        requestParams: TileRequest
    ): LayoutElement = materialScope(this, requestParams.deviceConfiguration) {
        val dayLabel = snapshot.dayCycle?.let { "Day $it" } ?: snapshot.dateDisplay
        val title = if (snapshot.dateDisplay.isNotBlank()) {
            "下次上課 · ${snapshot.dateDisplay} · $dayLabel"
        } else {
            "下次上課 · $dayLabel"
        }
        primaryLayout(
            titleSlot = { text(title.layoutString, typography = Typography.TITLE_SMALL) },
            mainSlot = {
                Column.Builder()
                    .addContent(text(snapshot.currentPeriod.subject.layoutString, typography = Typography.BODY_SMALL))
                    .addContent(text("點擊查看課表".layoutString, typography = Typography.BODY_SMALL))
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

    private fun emptyLayout(requestParams: TileRequest): LayoutElement =
        materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                mainSlot = {
                    Column.Builder()
                        .addContent(text("目前沒有課堂".layoutString, typography = Typography.TITLE_MEDIUM))
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

    private fun countdownLayout(
        snapshot: com.timetable.wear.engine.TodaySnapshot,
        requestParams: TileRequest,
        remaining: Int
    ): LayoutElement = materialScope(this, requestParams.deviceConfiguration) {
        val current = snapshot.currentPeriod
        val isTimed = isActiveTimedPeriod(current) && current.end.isNotBlank()
        val periodLabel: String
        val countdownText: String
        if (isTimed) {
            countdownText = formatCountdown(remaining)
            periodLabel = when (current.type) {
                PeriodInfo.PeriodType.PERIOD -> "${current.name} · ${parseSubject(current.subject)}"
                PeriodInfo.PeriodType.BREAK_TIME, PeriodInfo.PeriodType.ASSEMBLY -> current.name
                else -> current.name.ifBlank { "空堂時間" }
            }
        } else {
            // FREE → show countdown to next
            val next = snapshot.nextPeriod
            countdownText = if (next == null) "--:--" else formatCountdown(remaining)
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

        val dayLabel = snapshot.dayCycle?.let { "Day $it" } ?: snapshot.dateDisplay.ifBlank { "今日課表" }
        primaryLayout(
            titleSlot = { text(dayLabel.layoutString, typography = Typography.TITLE_SMALL) },
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
                    onClick = openAppClickable(),
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
                        onClick = openAppClickable(),
                        labelContent = { text("開啟應用".layoutString) }
                    )
                }
            )
        }

}
