package com.timetable.wear.tiles

import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text as m3Text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.timetable.wear.complications.formatCountdownMinutes
import com.timetable.wear.complications.isActiveTimedPeriod
import com.timetable.wear.complications.mergedPeriodBlock
import com.timetable.wear.complications.parseSubject
import com.timetable.wear.complications.remainingSecondsFor
import com.timetable.wear.complications.secondsUntil
import com.timetable.wear.data.local.UiPreferences
import com.timetable.wear.data.model.PeriodInfo
import com.timetable.wear.data.model.ScheduleItemType
import com.timetable.wear.data.model.TimetableType
import com.timetable.wear.engine.TimetableEngine
import com.timetable.wear.engine.TodaySnapshot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

/**
 * Countdown tile (Figma page 20, 一開三): full-canvas ProtoLayout with a
 * tall light stadium (countdown + subject) beside two stacked pills
 * (day + next). The system draws the manifest icon on top.
 *
 * The Glance widget ([com.timetable.wear.widgets.CountdownWidget]) serves
 * watch-face widget slots (small TILE_COMPAT containers); this service
 * serves the tiles carousel (full canvas).
 */
@AndroidEntryPoint
class CountdownTileService : TileService() {

    @Inject lateinit var engine: TimetableEngine
    @Inject lateinit var prefs: UiPreferences
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val merge = prefs.mergeConsecutive.first()
            val snapshot = engine.getTileSnapshot() ?: engine.getTodaySnapshot()
            if (snapshot == null) {
                return@future buildTile(loadingLayout(requestParams), freshnessMillis = 60_000L)
            }
            if (snapshot.isNextDay) {
                return@future buildTile(
                    countdownRow(snapshot, requestParams, merge, nextDay = true),
                    freshnessMillis = 300_000L
                )
            }
            if (snapshot.timetableType == TimetableType.NONE || snapshot.dayCycle == null) {
                return@future buildTile(
                    countdownRow(snapshot, requestParams, merge, empty = true),
                    freshnessMillis = 300_000L
                )
            }
            buildTile(
                countdownRow(snapshot, requestParams, merge),
                freshnessMillis = 60_000L
            )
        }

    private fun countdownRow(
        snapshot: TodaySnapshot,
        requestParams: TileRequest,
        merge: Boolean,
        nextDay: Boolean = false,
        empty: Boolean = false
    ): LayoutElement = materialScope(this, requestParams.deviceConfiguration) {
        val current = snapshot.currentPeriod
        val isTimed = !nextDay && !empty &&
            isActiveTimedPeriod(current) && current.end.isNotBlank()
        val block = if (isTimed) mergedPeriodBlock(snapshot.scheduleItems, current, merge) else null

        // Left box content (kit: big countdown, short class lines on top).
        val countdownText: String
        val className: String
        val subjectLine: String
        if (nextDay) {
            countdownText = "--:--"
            className = ""
            subjectLine = shortMonthDay(snapshot.dateDisplay).ifBlank { snapshot.dateDisplay }
        } else if (empty) {
            countdownText = "--:--"
            className = ""
            subjectLine = "今日沒有課程"
        } else if (isTimed) {
            val end = if (current.type == PeriodInfo.PeriodType.PERIOD) {
                block?.end ?: current.end
            } else {
                current.end
            }
            countdownText = formatCountdownMinutes(remainingSecondsFor(end).coerceAtLeast(0))
            if (current.type == PeriodInfo.PeriodType.PERIOD) {
                className = block?.displayName ?: current.name
                subjectLine = parseSubject(current.subject)
            } else {
                className = ""
                subjectLine = current.name.ifBlank { "空堂時間" }
            }
        } else {
            val next = snapshot.nextPeriod
            countdownText = if (next == null) {
                "--:--"
            } else {
                formatCountdownMinutes(secondsUntil(next.start).coerceAtLeast(0))
            }
            // "下一堂" is for classes only: breaks/lunch/assembly show
            // their own name with no prefix.
            className = if (next?.type == PeriodInfo.PeriodType.PERIOD) "下一堂" else ""
            subjectLine = next?.let {
                when (it.type) {
                    PeriodInfo.PeriodType.PERIOD -> parseSubject(it.subject)
                    else -> it.name.ifBlank { "休息" }
                }
            } ?: "已放學"
        }

        // Right pills (kit: dark day pill over deep-purple next pill).
        val dayLabel = snapshot.dayCycle?.let { "Day $it" }
            ?: snapshot.dateDisplay.ifBlank { "今日" }
        val nextLabel = when {
            nextDay || empty -> "已放學"
            block != null -> {
                val after = snapshot.scheduleItems
                    .filter { it.start.isNotBlank() && it.start > block.end }
                    .minByOrNull { it.start }
                when {
                    after == null -> "已放學"
                    after.type == ScheduleItemType.PERIOD ->
                        parseSubject(after.subject).take(4).trim().ifBlank { "已放學" }
                    else -> after.displayName.ifBlank { "已放學" }
                }
            }
            else -> snapshot.nextPeriod?.let { next ->
                when (next.type) {
                    PeriodInfo.PeriodType.PERIOD ->
                        parseSubject(next.subject).take(4).trim().ifBlank { "已放學" }
                    else -> next.name.ifBlank { "已放學" }
                }
            } ?: "已放學"
        }

        // No titleSlot/bottomSlot/icon: Figma row only. Narrower-than-kit
        // 70dp columns with a 4dp gap: the 74dp kit width clips against the
        // round-screen curve (seen on watch7small). Every box is tappable.
        primaryLayout(
            mainSlot = {
                LayoutElementBuilders.Row.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .addContent(
                        LayoutElementBuilders.Box.Builder()
                            .setWidth(dp(70f))
                            .setHeight(expand())
                            .addContent(
                                // Kit left box is always the light variant.
                                countdownBigBox(className, subjectLine, countdownText)
                            )
                            .build()
                    )
                    .addContent(spacerDp(TILE_COUNTDOWN_BOX_GAP_DP, 1))
                    .addContent(
                        LayoutElementBuilders.Column.Builder()
                            .setWidth(dp(70f))
                            .setHeight(expand())
                            .addContent(
                                tilePill(
                                    dayLabel,
                                    colorScheme.surfaceContainer.prop,
                                    colorScheme.onSurface.prop
                                )
                            )
                            .addContent(spacerDp(1, TILE_COUNTDOWN_BOX_GAP_DP))
                            .addContent(
                                tilePill(
                                    nextLabel,
                                    colorScheme.tertiaryContainer.prop,
                                    colorScheme.onTertiaryContainer.prop
                                )
                            )
                            .build()
                    )
                    .build()
            }
        )
    }

    /**
     * Single-line stadium pill that fills its parent (day / next slots).
     * Explicit oversized radius: CircleShape-style clips render as ellipses
     * on non-square boxes, a huge rounded-rect radius is a true stadium.
     * Theme colors (M3 Expressive dynamic, same source as the app).
     */
    private fun MaterialScope.tilePill(
        label: String,
        bg: ColorBuilders.ColorProp,
        fg: ColorBuilders.ColorProp
    ): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(bg)
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(200f))
                                    .build()
                            )
                            .build()
                    )
                    .setClickable(openAppClickable())
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(6f))
                            .setEnd(dp(6f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(15f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                            .setColor(fg)
                            .build()
                    )
                    .setMaxLines(1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                    .build()
            )
            .build()

    private fun loadingLayout(requestParams: TileRequest): LayoutElement =
        materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                mainSlot = {
                    m3Text("載入中…".layoutString, typography = Typography.BODY_MEDIUM)
                }
            )
        }

    override fun onTileResourcesRequest(
        requestParams: androidx.wear.tiles.RequestBuilders.ResourcesRequest
    ): ListenableFuture<androidx.wear.protolayout.ResourceBuilders.Resources> =
        com.google.common.util.concurrent.Futures.immediateFuture(
            resources(requestParams.version)
        )
}
