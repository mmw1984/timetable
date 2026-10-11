package com.timetable.wear.tiles

import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
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
import com.timetable.wear.complications.parseSubject
import com.timetable.wear.data.local.UiPreferences
import com.timetable.wear.data.model.ScheduleItem
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
 * Full-schedule tile (Figma page 21): a grid of uniform deep-purple
 * stadium pills, subject over period range (e.g. CHIN over 1-2).
 *
 * Figma covers 4 groups (2x2); larger days pack kit-style: 5 -> 2+3,
 * 6 -> 2+2+2, 7 -> 2+2+3, 8 -> 2+2+2+2, 9+ -> three-slot rows.
 * Three-slot pills show the subject only (no room for the range).
 * Pills are uniform per the kit (no current highlight; "now" lives on
 * the countdown tile).
 *
 * The Glance widget ([com.timetable.wear.widgets.ScheduleWidget]) serves
 * watch-face widget slots (small TILE_COMPAT containers); this service
 * serves the tiles carousel (full canvas).
 */
@AndroidEntryPoint
class FullScheduleTileService : TileService() {

    @Inject lateinit var engine: TimetableEngine
    @Inject lateinit var prefs: UiPreferences
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val merge = prefs.mergeConsecutive.first()
            val snapshot = engine.getTileSnapshot() ?: engine.getTodaySnapshot()
            val layout = when {
                snapshot == null -> loadingLayout(requestParams)
                !snapshot.isNextDay &&
                    (snapshot.timetableType == TimetableType.NONE || snapshot.dayCycle == null) ->
                    emptyLayout(requestParams)
                else -> scheduleGrid(snapshot, requestParams, merge)
            }
            buildTile(layout, freshnessMillis = 300_000L)
        }

    private fun scheduleGrid(
        snapshot: TodaySnapshot,
        requestParams: TileRequest,
        merge: Boolean
    ): LayoutElement = materialScope(this, requestParams.deviceConfiguration) {
        val groups = tileGroups(snapshot.scheduleItems, merge)
        if (groups.isEmpty()) {
            return@materialScope centeredText("今日沒有課程")
        }
        // No titleSlot: Figma grid only (countdown tile owns "now").
        // Max 8 groups: pairs with a three-slot last row for odd counts
        // (4 -> 2+2 per Figma, 5 -> 2+3, 7 -> 2+2+3, 8 -> 2+2+2+2).
        // Fixed kit row heights (expand rows turn pills into circles);
        // twin spacers center the grid (vertical expand-sharing works).
        val split = tileRowSplit(groups.size)
        // Fixed kit row heights (expand rows turn pills into circles);
        // twin spacers center the grid (vertical expand-sharing works).
        // Three-wide rows are shorter (their 45dp pills suit ~40dp).
        // Smaller type as rows grow (blog Ski-style tiers).
        val subjectSp = when {
            split.size <= 2 -> 16f
            split.size == 3 -> 15f
            else -> 13f
        }
        val rangeSp = when {
            split.size <= 2 -> 12f
            split.size == 3 -> 11f
            else -> 10f
        }
        primaryLayout(
            mainSlot = {
                LayoutElementBuilders.Column.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .apply {
                        addContent(verticalSpring())
                        var index = 0
                        split.forEachIndexed { rowIndex, slots ->
                            if (rowIndex > 0) addContent(spacerDp(1, TILE_GRID_GAP_DP))
                            val row = groups.subList(index, minOf(index + slots, groups.size))
                            index += slots
                            addContent(gridRow(row, subjectSp, rangeSp, split.size))
                        }
                        addContent(verticalSpring())
                    }
                    .build()
            }
        )
    }

    /**
     * One grid row: fixed kit widths fill the row exactly (Row
     * expand-sharing is unreliable), heights expand so rows share the
     * mainSlot evenly. Two-wide slots show subject over range;
     * three-wide slots show the subject only.
     */
    private fun MaterialScope.gridRow(
        row: List<ScheduleItem>,
        subjectSp: Float,
        rangeSp: Float,
        rowCount: Int
    ): LayoutElement {
        val slotWidthDp = if (row.size >= 3) SCHEDULE_SLOT_3_DP else SCHEDULE_SLOT_2_DP
        val rowHDp = if (row.size >= 3) {
            ROW_H_3_WIDE_DP
        } else {
            when (rowCount) {
                1 -> 72f
                2 -> 56f
                3 -> 40f
                else -> 29f
            }
        }
        val builder = LayoutElementBuilders.Row.Builder()
            .setWidth(expand())
            .setHeight(dp(rowHDp))
        row.forEachIndexed { index, item ->
            if (index > 0) builder.addContent(spacerDp(TILE_GRID_GAP_DP, 1))
            val subject = parseSubject(item.subject).take(TILE_GRID_SUBJECT_CHARS)
            if (row.size >= 3) {
                builder.addContent(
                    gridPill(
                        line1 = subject.ifBlank { gridOrderLabel(item) },
                        line2 = gridOrderLabel(item).takeIf { subject.isNotBlank() }.orEmpty(),
                        subjectSp = 12f,
                        rangeSp = 9f,
                        widthDp = slotWidthDp
                    )
                )
            } else {
                builder.addContent(
                    gridPill(
                        line1 = subject.ifBlank { gridOrderLabel(item) },
                        line2 = gridOrderLabel(item).takeIf { subject.isNotBlank() }.orEmpty(),
                        subjectSp = subjectSp,
                        rangeSp = rangeSp,
                        widthDp = slotWidthDp
                    )
                )
            }
        }
        return builder.build()
    }

    /** Uniform kit pill: subject over range, fixed Figma colors. */
    private fun MaterialScope.gridPill(
        line1: String,
        line2: String,
        subjectSp: Float,
        rangeSp: Float,
        widthDp: Float
    ): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(widthDp))
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(colorOf(SCHEDULE_PILL_BG))
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
                            .setStart(dp(4f))
                            .setEnd(dp(4f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(expand())
                    .setHeight(DimensionBuilders.wrap())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .apply {
                        addContent(
                            pillLine(line1, subjectSp, LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        )
                        if (line2.isNotBlank()) {
                            addContent(
                                pillLine(line2, rangeSp, LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                            )
                        }
                    }
                    .build()
            )
            .build()

    /** Expanding spacer that centers fixed-height rows vertically. */
    private fun verticalSpring(): LayoutElement =
        LayoutElementBuilders.Spacer.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .build()

    private fun pillLine(text: String, sizeSp: Float, weight: Int): LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(sizeSp))
                    .setWeight(weight)
                    .setColor(colorOf(SCHEDULE_PILL_TEXT))
                    .build()
            )
            .setMaxLines(1)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
            .build()

    private fun MaterialScope.centeredText(text: String): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                m3Text(text.layoutString, typography = Typography.TITLE_MEDIUM)
            )
            .build()

    private fun loadingLayout(requestParams: TileRequest): LayoutElement =
        materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                mainSlot = { centeredText("載入中…") }
            )
        }

    private fun emptyLayout(requestParams: TileRequest): LayoutElement =
        materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                mainSlot = { centeredText("今日沒有課程") }
            )
        }

    override fun onTileResourcesRequest(
        requestParams: androidx.wear.tiles.RequestBuilders.ResourcesRequest
    ): ListenableFuture<androidx.wear.protolayout.ResourceBuilders.Resources> =
        com.google.common.util.concurrent.Futures.immediateFuture(
            resources(requestParams.version)
        )
}

private fun colorOf(argb: Long): ColorBuilders.ColorProp =
    ColorBuilders.ColorProp.Builder().setArgb(argb.toInt()).build()

/** Kit schedule pills (Figma page 21): uniform deep purple + light text. */
private const val SCHEDULE_PILL_BG: Long = 0xFF4D3D76L
private const val SCHEDULE_PILL_TEXT: Long = 0xFFF6EDFFL

/** Fixed slot widths: 2x70 + 4 gap fills the safe row (round screens clip 74). */
private const val SCHEDULE_SLOT_2_DP = 70f
private const val SCHEDULE_SLOT_3_DP = 45f

/** Three-wide rows stay short so their 45dp pills keep stadium shape. */
private const val ROW_H_3_WIDE_DP = 40f
