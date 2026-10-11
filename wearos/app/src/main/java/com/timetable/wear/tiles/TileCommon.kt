package com.timetable.wear.tiles

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders.ContainerDimension
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.ButtonDefaults.filledVariantButtonColors
import androidx.wear.protolayout.material3.CardDefaults.filledTonalCardColors
import androidx.wear.protolayout.material3.CardDefaults.filledVariantCardColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.text as m3Text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.TileBuilders.Tile
import com.timetable.wear.MainActivity
import com.timetable.wear.complications.parseSubject
import com.timetable.wear.data.model.ScheduleItem
import com.timetable.wear.data.model.ScheduleItemType

internal fun openAppClickable(): ModifiersBuilders.Clickable =
    ModifiersBuilders.Clickable.Builder()
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(MainActivity::class.java.packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build()
                )
                .build()
        )
        .build()

/** Kit "Style=Tonal/Small" stadium pill: one line, fully rounded. */
internal fun MaterialScope.periodPill(
    label: String,
    isCurrent: Boolean,
    width: ContainerDimension = expand(),
    heightDp: Float = TILE_GRID_PILL_HEIGHT_DP
): LayoutElementBuilders.LayoutElement {
    // textButton's fixed inner padding + LABEL_SMALL line height clip below
    // ~26dp pills: use a custom stadium pill with explicit 10sp text so
    // 4-row small screens stay fully legible (full-display priority).
    if (heightDp < TILE_SMALL_TEXT_PILL_THRESHOLD_DP) {
        return smallPillCard(label, isCurrent, width, heightDp)
    }
    return textButton(
        // 2-per-row grid: 3 short rows always fit vertically; textButton's
        // tight padding fits "1-2 MACO" where DataCard slots truncate it.
        onClick = openAppClickable(),
        width = width,
        height = dp(heightDp),
        shape = shapes.full,
        colors = if (isCurrent) filledVariantButtonColors() else filledTonalButtonColors(),
        labelContent = {
            m3Text(
                label.layoutString,
                typography = Typography.LABEL_SMALL
            )
        }
    )
}

/** Stadium pill with explicit small text for heights textButton cannot fit. */
internal fun MaterialScope.smallPillCard(
    label: String,
    isCurrent: Boolean,
    width: ContainerDimension,
    heightDp: Float
): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Box.Builder()
        .setWidth(width)
        .setHeight(dp(heightDp))
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(
                            if (isCurrent) {
                                colorScheme.primaryContainer.prop
                            } else {
                                colorScheme.surfaceContainer.prop
                            }
                        )
                        .setCorner(
                            ModifiersBuilders.Corner.Builder()
                                .setRadius(dp(heightDp / 2))
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
            LayoutElementBuilders.Text.Builder()
                .setText(label)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(10f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                        .setColor(
                            if (isCurrent) {
                                colorScheme.onPrimaryContainer.prop
                            } else {
                                colorScheme.onSurface.prop
                            }
                        )
                        .build()
                )
                .setMaxLines(1)
                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                .build()
        )
        .build()

/** Pills shorter than this use BODY_EXTRA_SMALL so glyphs fit (full-display priority). */
internal const val TILE_SMALL_TEXT_PILL_THRESHOLD_DP = 26f

/**
 * Slots narrower than this use the compact stadium even at full height:
 * a 48dp 3-slot pill cannot fit a 7-char "5-6 PHY" label in textButton's
 * inner padding, but the explicit-10sp stadium can (full-display priority).
 */
internal const val TILE_NARROW_SLOT_THRESHOLD_DP = 60f

/** Gap between the left big box and the right stacked boxes. */
internal const val TILE_COUNTDOWN_BOX_GAP_DP = 4

/**
 * Left big box of the 一開三 countdown tile (Figma page 20: a tall light
 * stadium): fills the row height, class lines pinned to the top, countdown
 * centered in the remaining space via twin expand() spacers. Fully-rounded
 * (stadium) corners via an oversized radius.
 */
/**
 * Left big box of the 一開三 countdown tile (Figma page 20: a tall light
 * stadium): fills the row height, class lines pinned to the top, countdown
 * centered in the remaining space via twin expand() spacers. Fully-rounded
 * (stadium) corners via an oversized radius.
 *
 * Fixed Figma colors (not theme roles): the kit box is always light
 * lavender with dark text, independent of the system dynamic theme.
 */
internal const val COUNTDOWN_BOX_BG: Long = 0xFFE9DDFFL
internal const val COUNTDOWN_BOX_TEXT: Long = 0xFF210F48L

internal fun MaterialScope.countdownBigBox(
    className: String,
    subjectLine: String,
    countdownText: String
): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(fixedColor(COUNTDOWN_BOX_BG))
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
                        .setTop(dp(8f))
                        .setBottom(dp(8f))
                        .build()
                )
                .build()
        )
        .addContent(
            Column.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .apply {
                    if (className.isNotBlank()) {
                        addContent(
                            fixedLine(
                                className,
                                sizeSp = 12f,
                                weight = LayoutElementBuilders.FONT_WEIGHT_MEDIUM
                            )
                        )
                    }
                    if (subjectLine.isNotBlank()) {
                        addContent(
                            fixedLine(
                                subjectLine,
                                sizeSp = 11f,
                                weight = LayoutElementBuilders.FONT_WEIGHT_MEDIUM
                            )
                        )
                    }
                    addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setWidth(expand())
                            .setHeight(expand())
                            .build()
                    )
                    addContent(
                        fixedLine(
                            countdownText,
                            sizeSp = if (countdownText.length > 5) 18f else 34f,
                            weight = LayoutElementBuilders.FONT_WEIGHT_BOLD
                        )
                    )
                    addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setWidth(expand())
                            .setHeight(expand())
                            .build()
                    )
                }
                .build()
        )
        .build()

/** Single centered line in a fixed Figma color (kit boxes ignore the theme). */
private fun fixedLine(
    text: String,
    sizeSp: Float,
    weight: Int,
    color: Long = COUNTDOWN_BOX_TEXT
): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Text.Builder()
        .setText(text)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(sizeSp))
                .setWeight(weight)
                .setColor(fixedColor(color))
                .build()
        )
        .setMaxLines(1)
        .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
        .build()

private fun fixedColor(argb: Long): ColorBuilders.ColorProp =
    ColorBuilders.ColorProp.Builder().setArgb(argb.toInt()).build()

/** Single-column compact row for 7+ groups, e.g. "1-2 MACO". */
internal fun MaterialScope.compactRowCard(
    label: String,
    isCurrent: Boolean
) = textButton(
    onClick = openAppClickable(),
    width = expand(),
    height = dp(TILE_COMPACT_ROW_HEIGHT_DP),
    shape = shapes.full,
    colors = if (isCurrent) filledVariantButtonColors() else filledTonalButtonColors(),
    labelContent = {
        m3Text(
            label.layoutString,
            typography = Typography.LABEL_SMALL
        )
    }
)

internal fun gridOrderLabel(item: ScheduleItem): String =
    item.displayName.replace("第", "").replace("節", "")
        .ifBlank { item.periodNumber?.toString().orEmpty() }

internal fun compactRowLabel(item: ScheduleItem): String =
    "${gridOrderLabel(item)} ${parseSubject(item.subject).take(4)}".trim()

/**
 * "10月12日 星期一" -> "12/10" for the tile title slot: the full date +
 * weekday never fits one line on small round screens and the renderer
 * ellipsizes it ("下次·10月12日 星..."). Day-first to match local reading.
 */
internal fun shortMonthDay(dateDisplay: String): String {
    val datePart = dateDisplay.substringBefore(" ").trim()
    if (!datePart.contains("月") || !datePart.contains("日")) return datePart
    val month = datePart.substringBefore("月").trim()
    val day = datePart.substringAfter("月").substringBefore("日").trim()
    if (month.isEmpty() || day.isEmpty()) return datePart
    return "$day/$month"
}

/** Period groups after optional 連堂合併；shared by the grid, the compact list and the title count. */
internal fun tileGroups(items: List<ScheduleItem>, merge: Boolean): List<ScheduleItem> {
    val periods = items.filter { it.type == ScheduleItemType.PERIOD }
    return if (merge) mergeTileItems(periods) else periods
}

internal fun spacerDp(widthDp: Int, heightDp: Int): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Spacer.Builder()
        .setWidth(dp(widthDp.toFloat()))
        .setHeight(dp(heightDp.toFloat()))
        .build()

/** Pills shown per row in the 2-per-row grid. */
internal const val TILE_GRID_PILL_HEIGHT_DP = 40f
/** Pills auto-shrink between these bounds so every group fits (full-display priority). */
internal const val TILE_PILL_MAX_HEIGHT_DP = 40f
internal const val TILE_PILL_MIN_HEIGHT_DP = 20f

/** Estimated inter-row gap used for viewport budgeting (3 tiers by row count). */
internal const val TILE_ROW_GAP_DP = 6
internal const val TILE_ROW_GAP_TIGHT_DP = 4
internal const val TILE_ROW_GAP_COMPACT_DP = 2

/**
 * Viewport consumed by everything except the period rows: the title slot
 * plus the top/bottom insets the round-screen curve needs.
 *
 * Tiles cannot scroll, so this must be conservative: on a 227dp round
 * screen a plan that exactly equalled (height - 88) still clipped the
 * last row ("8 ENG"), proving 88 underestimates the real chrome.
 * 102 + an explicit 6dp slack leaves every row visible with margin.
 */
internal const val TILE_CHROME_DP = 102

/** Safety margin subtracted before sizing pills so planned != budget edge clips. */
internal const val TILE_SLACK_DP = 6

internal const val TILE_COMPACT_ROW_HEIGHT_DP = 38f

/**
 * Figma "Tiles/Layouts" (M3 Wear OS Tiles Design Kit), page 21 grid metrics:
 * content is inset 20dp from each tile edge (152dp wide on a 192dp screen),
 * pills are separated by 4dp, and every slot is 53dp tall. A 2-slot row is
 * therefore 74dp per pill and a 3-slot row 48dp — matching the kit exactly.
 */
internal const val TILE_GRID_SIDE_INSET_DP = 20
internal const val TILE_GRID_GAP_DP = 4
internal const val TILE_GRID_ROW_HEIGHT_DP = 53f

/** Kit corner radii: plain slots are stadiums, the current slot is a 26dp card. */
internal const val TILE_PILL_RADIUS_DP = 200f
internal const val TILE_CURRENT_RADIUS_DP = 26f

/** Below this height the current slot drops its second line to stay legible. */
internal const val TILE_CURRENT_TWO_LINE_MIN_DP = 30f

/** Narrowest a slot may shrink to in a 3-slot row (kit: 44dp). */
internal const val TILE_GRID_MIN_SLOT_WIDTH_DP = 40f

/** Subject characters kept inside a slot label (kit single-line pills). */
internal const val TILE_GRID_SUBJECT_CHARS = 4

internal fun MaterialScope.periodCardGroups(
    items: List<ScheduleItem>,
    currentKey: String?,
    merge: Boolean,
    screenWidthDp: Int,
    screenHeightDp: Int
): LayoutElementBuilders.LayoutElement {
    // Tiles cannot scroll, so the grid is sized to the viewport: every group
    // is rendered, pills auto-shrink so the last row is never clipped.
    val groups = tileGroups(items, merge)
    if (groups.isEmpty()) {
        return m3Text(
            "今日沒有課程".layoutString,
            typography = Typography.TITLE_SMALL
        )
    }
    val plan = planTileLayout(groups.size, screenHeightDp)
    return gridGroups(groups, currentKey, screenWidthDp, plan)
}

/**
 * Plans the 2-per-row grid so EVERY group fits inside the tile viewport.
 *
 * Tiles cannot scroll (official limitation), so the only way to show all
 * periods is to size the pills to the real viewport: given N groups we need
 * ceil(N/2) rows, and each row gets budget/rows of height. Nothing is ever
 * dropped behind an overflow pill.
 */
internal data class TileLayoutPlan(
    val rows: Int,
    /** Slots per row, e.g. [2, 2, 3] for 7 groups (kit packs a 3-slot last row). */
    val slotsPerRow: List<Int>,
    val pillHeightDp: Float,
    val rowGapDp: Int,
    /** Total planned height; must be <= the viewport budget. */
    val plannedHeightDp: Int
)

/**
 * Row split for [groupCount] groups, following the kit: two slots per row
 * normally, and a three-slot last row whenever the remainder allows it
 * (5 groups -> 2+3, 7 -> 2+2+3). This keeps the number of rows -- and so
 * the pill height -- as small as the width allows.
 */
internal fun tileRowSplit(groupCount: Int): List<Int> {
    if (groupCount <= 3) return listOf(groupCount)
    if (groupCount % 2 == 0) return List(groupCount / 2) { 2 }
    // Odd counts >= 5: rows of 2 with a three-slot last row (no orphan).
    return List((groupCount - 3) / 2) { 2 } + 3
}

internal fun planTileLayout(groupCount: Int, screenHeightDp: Int): TileLayoutPlan {
    val budget = (screenHeightDp - TILE_CHROME_DP).coerceAtLeast(80)
    val split = tileRowSplit(groupCount)
    val rows = split.size
    // Tighter gaps as rows grow so 7-8 groups still fit on 192dp screens.
    val gap = when {
        rows >= 4 -> TILE_ROW_GAP_COMPACT_DP
        rows == 3 -> TILE_ROW_GAP_TIGHT_DP
        else -> TILE_ROW_GAP_DP
    }
    // Size against usable (budget minus slack) so planned never sits exactly
    // on the budget edge where the renderer clips the last row.
    val usable = (budget - TILE_SLACK_DP).coerceAtLeast(60)
    val perRow = (usable - (rows - 1) * gap).toFloat() / rows
    val pill = perRow.coerceIn(TILE_PILL_MIN_HEIGHT_DP, TILE_PILL_MAX_HEIGHT_DP)
    val planned = rows * pill.toInt() + (rows - 1) * gap
    return TileLayoutPlan(
        rows = rows,
        slotsPerRow = split,
        pillHeightDp = pill,
        rowGapDp = gap,
        plannedHeightDp = planned
    )
}

private fun MaterialScope.gridGroups(
    groups: List<ScheduleItem>,
    currentKey: String?,
    screenWidthDp: Int,
    plan: TileLayoutPlan
): LayoutElementBuilders.LayoutElement {
    // Kit geometry: 20dp inset per side, 4dp between slots.
    val contentWidthDp = (screenWidthDp - TILE_GRID_SIDE_INSET_DP * 2).toFloat()
    var index = 0
    val rows = plan.slotsPerRow.map { slots ->
        val row = groups.subList(index, minOf(index + slots, groups.size))
        index += slots
        val gaps = TILE_GRID_GAP_DP * (row.size - 1).coerceAtLeast(0)
        val slotWidthDp = ((contentWidthDp - gaps) / row.size)
            .coerceAtLeast(TILE_GRID_MIN_SLOT_WIDTH_DP)
        gridRow(row, currentKey, slotWidthDp, plan.pillHeightDp)
    }
    return Column.Builder()
        .setWidth(expand())
        .setHeight(wrap())
        .apply {
            rows.forEachIndexed { rowIndex, row ->
                addContent(row)
                if (rowIndex != rows.lastIndex) addContent(spacerDp(1, plan.rowGapDp))
            }
        }
        .build()
}

private fun MaterialScope.gridRow(
    row: List<ScheduleItem>,
    currentKey: String?,
    slotWidthDp: Float,
    pillHeightDp: Float
): LayoutElementBuilders.LayoutElement {
    // Explicit row height — a wrap() Row mis-measures fixed-height buttons and
    // the pill overflows into neighbouring rows (seen on-watch as overlap).
    // A lone slot is centred with twin expand() springs: the main-slot content
    // width is narrower than screenWidthDp (primaryLayout insets), so side
    // spacers computed from the screen width left it ~40px left of center.
    val builder = LayoutElementBuilders.Row.Builder()
        .setWidth(expand())
        .setHeight(dp(pillHeightDp))
    if (row.size == 1) {
        val spring = LayoutElementBuilders.Spacer.Builder()
            .setWidth(expand())
            .setHeight(dp(1f))
            .build()
        builder.addContent(spring)
        builder.addContent(periodSlot(row[0], currentKey, slotWidthDp, pillHeightDp))
        builder.addContent(spring)
        return builder.build()
    }
    row.forEachIndexed { index, item ->
        if (index > 0) builder.addContent(spacerDp(TILE_GRID_GAP_DP, 1))
        builder.addContent(periodSlot(item, currentKey, slotWidthDp, pillHeightDp))
    }
    return builder.build()
}

/**
 * One slot of the kit grid: the current lesson is a 26dp-radius card with the
 * subject over the period label; every other lesson is a stadium pill with one
 * line. Short pills drop to the compact stadium so text stays legible.
 */
private fun MaterialScope.periodSlot(
    item: ScheduleItem,
    currentKey: String?,
    widthDp: Float,
    heightDp: Float
): LayoutElementBuilders.LayoutElement {
    val isCurrent = item.stableKey == currentKey
    val orderLabel = gridOrderLabel(item)
    val subjectShort = parseSubject(item.subject).take(TILE_GRID_SUBJECT_CHARS)
    if (isCurrent && heightDp >= TILE_CURRENT_TWO_LINE_MIN_DP && widthDp >= TILE_NARROW_SLOT_THRESHOLD_DP) {
        return currentPeriodCard(subjectShort, orderLabel, widthDp, heightDp)
    }
    if (heightDp < TILE_SMALL_TEXT_PILL_THRESHOLD_DP || widthDp < TILE_NARROW_SLOT_THRESHOLD_DP) {
        return smallPillCard("$orderLabel $subjectShort", isCurrent, dp(widthDp), heightDp)
    }
    return periodPill("$orderLabel $subjectShort", isCurrent, dp(widthDp), heightDp)
}

/** Kit "Style=Filled Variant, Alignment=Top": 26dp radius, subject over order. */
private fun MaterialScope.currentPeriodCard(
    subjectShort: String,
    orderLabel: String,
    widthDp: Float,
    heightDp: Float
): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Box.Builder()
        .setWidth(dp(widthDp))
        .setHeight(dp(heightDp))
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(colorScheme.primaryContainer.prop)
                        .setCorner(
                            ModifiersBuilders.Corner.Builder()
                                .setRadius(dp(TILE_CURRENT_RADIUS_DP))
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
            Column.Builder()
                .setWidth(expand())
                .setHeight(wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    m3Text(
                        subjectShort.layoutString,
                        typography = Typography.LABEL_MEDIUM
                    )
                )
                .addContent(
                    m3Text(
                        orderLabel.layoutString,
                        typography = Typography.BODY_EXTRA_SMALL
                    )
                )
                .build()
        )
        .build()

private fun mergeTileItems(items: List<ScheduleItem>): List<ScheduleItem> {
    if (items.isEmpty()) return items
    val merged = mutableListOf<ScheduleItem>()
    var current = items[0]
    for (i in 1 until items.size) {
        val next = items[i]
        val canMerge = current.type == next.type &&
            current.type != ScheduleItemType.ASSEMBLY &&
            current.subject == next.subject &&
            current.subject.isNotBlank() &&
            current.end == next.start
        if (canMerge) {
            val newDisplayName = if (
                current.type == ScheduleItemType.PERIOD &&
                current.periodNumber != null && next.periodNumber != null
            ) "第${current.periodNumber}-${next.periodNumber}節" else current.displayName
            current = current.copy(displayName = newDisplayName, end = next.end)
        } else {
            merged.add(current)
            current = next
        }
    }
    merged.add(current)
    return merged
}

internal fun buildTile(root: LayoutElementBuilders.LayoutElement, freshnessMillis: Long): Tile =
    Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(
            Timeline.Builder()
                .addTimelineEntry(
                    TimelineEntry.Builder()
                        .setLayout(Layout.Builder().setRoot(root).build())
                        .build()
                )
                .build()
        )
        .setFreshnessIntervalMillis(freshnessMillis)
        .build()

internal fun buildTimelineTile(
    layouts: List<LayoutElementBuilders.LayoutElement>,
    freshnessMillis: Long
): Tile {
    val now = System.currentTimeMillis()
    val builder = Timeline.Builder()
    layouts.forEachIndexed { index, layout ->
        val start = now + index * 60_000L
        val end = start + 60_000L
        builder.addTimelineEntry(
            TimelineEntry.Builder()
                .setLayout(Layout.Builder().setRoot(layout).build())
                .setValidity(
                    androidx.wear.protolayout.TimelineBuilders.TimeInterval.Builder()
                        .setStartMillis(start)
                        .setEndMillis(end)
                        .build()
                )
                .build()
        )
    }
    return Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(builder.build())
        .setFreshnessIntervalMillis(freshnessMillis)
        .build()
}

internal fun resources(requestedVersion: String): ResourceBuilders.Resources =
    ResourceBuilders.Resources.Builder().setVersion(requestedVersion).build()

internal const val RESOURCES_VERSION = "1"
