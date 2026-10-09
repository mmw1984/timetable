package com.timetable.wear.tiles

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.ContainerDimension
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
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
import androidx.wear.protolayout.material3.buttonGroup
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

internal fun MaterialScope.periodDataCard(
    orderLabel: String,
    subjectShort: String,
    isCurrent: Boolean,
    width: ContainerDimension = expand(),
    heightDp: Float = TILE_GRID_PILL_HEIGHT_DP
) = textButton(
    // 2-per-row grid: 3 short rows always fit vertically; textButton's
    // tight padding fits "1-2 MACO" where DataCard slots truncate it.
    onClick = openAppClickable(),
    width = width,
    height = dp(heightDp),
    shape = shapes.full,
    colors = if (isCurrent) filledVariantButtonColors() else filledTonalButtonColors(),
    labelContent = {
        m3Text(
            "$orderLabel $subjectShort".layoutString,
            typography = Typography.LABEL_SMALL
        )
    }
)

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

/** Period groups after optional 連堂合併；shared by the grid, the compact list and the title count. */
internal fun tileGroups(items: List<ScheduleItem>, merge: Boolean): List<ScheduleItem> {
    val periods = items.filter { it.type == ScheduleItemType.PERIOD }
    return if (merge) mergeTileItems(periods) else periods
}

private fun spacerDp(widthDp: Int, heightDp: Int): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Spacer.Builder()
        .setWidth(dp(widthDp.toFloat()))
        .setHeight(dp(heightDp.toFloat()))
        .build()

/** Pills shown per row in the 2-per-row grid (6 or fewer groups). */
internal const val TILE_GRID_PILL_HEIGHT_DP = 48f

/** Shorter grid pills when 3 rows are needed (5-6 groups) so small viewports fit. */
internal const val TILE_GRID_PILL_HEIGHT_SHORT_DP = 40f

/** Estimated inter-row gap used for viewport budgeting. */
internal const val TILE_ROW_GAP_DP = 8

/** Viewport reserved for title slot + margins; the rest is the list budget. */
internal const val TILE_CHROME_DP = 64

internal const val TILE_COMPACT_ROW_HEIGHT_DP = 38f

/** Side padding assumed when centering a lone orphan pill. */
internal const val TILE_GRID_SIDE_PADDING_DP = 32

internal fun MaterialScope.periodCardGroups(
    items: List<ScheduleItem>,
    currentKey: String?,
    merge: Boolean,
    screenWidthDp: Int,
    screenHeightDp: Int
): LayoutElementBuilders.LayoutElement {
    // Layout depends on the group count AND the viewport height because tiles
    // cannot scroll: anything taller than the viewport is clipped top and
    // bottom (seen on small round screens as cut-off bubbles). Small screens
    // get shorter pills and fewer compact rows instead of clipped content.
    val groups = tileGroups(items, merge)
    if (groups.isEmpty()) {
        return m3Text(
            "今日沒有課程".layoutString,
            typography = Typography.TITLE_SMALL
        )
    }
    val budget = (screenHeightDp - TILE_CHROME_DP).coerceAtLeast(96)
    val gridRows = (groups.size + 1) / 2
    val gridPillH = if (gridRows <= 2) TILE_GRID_PILL_HEIGHT_DP else TILE_GRID_PILL_HEIGHT_SHORT_DP
    val gridH = (gridRows * (gridPillH + TILE_ROW_GAP_DP)).toInt()
    if (groups.size <= 6 && gridH <= budget) {
        return gridGroups(groups, currentKey, screenWidthDp, gridPillH)
    }
    // Compact single-column list sized to the budget; always leaves room
    // for the "+N 更多" overflow pill so content is never silently clipped.
    val maxRows = compactRowBudget(screenHeightDp)
    val visible = if (groups.size <= maxRows) groups else groups.take((maxRows - 1).coerceAtLeast(1))
    val overflow = groups.size - visible.size
    return Column.Builder()
        .setWidth(expand())
        .setHeight(wrap())
        .apply {
            visible.forEach { item ->
                addContent(compactRowCard(compactRowLabel(item), item.stableKey == currentKey))
            }
            if (overflow > 0) {
                addContent(compactRowCard("＋${overflow} 更多", false))
            }
        }
        .build()
}

/** How many compact rows (including the overflow pill) fit the height budget. Pure logic, unit-tested. */
internal fun compactRowBudget(screenHeightDp: Int): Int {
    val budget = (screenHeightDp - TILE_CHROME_DP).coerceAtLeast(96)
    return (budget / (TILE_COMPACT_ROW_HEIGHT_DP + TILE_ROW_GAP_DP)).toInt().coerceAtLeast(2)
}

private fun MaterialScope.gridGroups(
    groups: List<ScheduleItem>,
    currentKey: String?,
    screenWidthDp: Int,
    pillHeightDp: Float
): LayoutElementBuilders.LayoutElement {
    val halfWidthDp = ((screenWidthDp - TILE_GRID_SIDE_PADDING_DP) / 2).coerceAtLeast(72)
    val sideDp = (halfWidthDp / 2).coerceAtLeast(8)
    return Column.Builder()
        .setWidth(expand())
        .setHeight(wrap())
        .apply {
            groups.chunked(2).forEach { row ->
                if (row.size == 1) {
                    val item = row[0]
                    addContent(
                        // NOTE: explicit row height — a wrap() Row mis-measures
                        // fixed-height buttons and the pill overflows into
                        // neighbouring rows (seen on-watch as overlap).
                        LayoutElementBuilders.Row.Builder()
                            .setWidth(expand())
                            .setHeight(dp(pillHeightDp))
                            .addContent(spacerDp(sideDp, 1))
                            .addContent(
                                periodDataCard(
                                    orderLabel = gridOrderLabel(item),
                                    subjectShort = parseSubject(item.subject).take(4),
                                    isCurrent = item.stableKey == currentKey,
                                    width = dp(halfWidthDp.toFloat()),
                                    heightDp = pillHeightDp
                                )
                            )
                            .addContent(spacerDp(sideDp, 1))
                            .build()
                    )
                } else {
                    addContent(
                        buttonGroup(
                            width = expand(),
                            height = wrap()
                        ) {
                            row.forEach { item ->
                                buttonGroupItem {
                                    periodDataCard(
                                        orderLabel = gridOrderLabel(item),
                                        subjectShort = parseSubject(item.subject).take(4),
                                        isCurrent = item.stableKey == currentKey,
                                        heightDp = pillHeightDp
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
        .build()
}

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
