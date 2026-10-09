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
    width: ContainerDimension = expand()
) = textButton(
    // 2-per-row grid: 3 short rows always fit vertically; textButton's
    // tight padding fits "1-2MACO" where DataCard slots truncate it.
    onClick = openAppClickable(),
    width = width,
    height = dp(TILE_GRID_PILL_HEIGHT_DP),
    shape = shapes.full,
    colors = if (isCurrent) filledVariantButtonColors() else filledTonalButtonColors(),
    labelContent = {
        m3Text(
            "$orderLabel$subjectShort".layoutString,
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

/** Group count at which the tile switches to the compact single-column list. */
internal const val TILE_COMPACT_LIST_THRESHOLD = 7

/**
 * Compact rows (TILE_MAX_COMPACT_ROWS - 1 groups + one "+N 更多" overflow
 * pill). Tiles cannot scroll, so this budget must fit the smallest viewport.
 */
internal const val TILE_MAX_COMPACT_ROWS = 5
internal const val TILE_COMPACT_ROW_HEIGHT_DP = 38f

/** Side padding assumed when centering a lone orphan pill. */
internal const val TILE_GRID_SIDE_PADDING_DP = 32

internal fun MaterialScope.periodCardGroups(
    items: List<ScheduleItem>,
    currentKey: String?,
    merge: Boolean,
    screenWidthDp: Int
): LayoutElementBuilders.LayoutElement {
    // Layout depends on the group count because tiles cannot scroll:
    // anything taller than the viewport is clipped. 7+ groups switch to a
    // compact list with an overflow entry; a lone orphan row is centered
    // instead of stretched full width (that looked broken).
    // (Supersedes the old take(MAX_TILE_ROWS) cap: with 8 periods max it
    // never triggered, and it dropped content without an entry point.)
    val groups = tileGroups(items, merge)
    if (groups.isEmpty()) {
        return m3Text(
            "今日沒有課程".layoutString,
            typography = Typography.TITLE_SMALL
        )
    }
    if (groups.size >= TILE_COMPACT_LIST_THRESHOLD) {
        val visible = groups.take(TILE_MAX_COMPACT_ROWS - 1)
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
    // 6 or fewer groups: 2-per-row grid.
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
                        // NOTE: explicit 48dp height — a wrap() Row mis-measures
                        // fixed-height buttons and the pill overflows into
                        // neighbouring rows (seen on-watch as overlap).
                        LayoutElementBuilders.Row.Builder()
                            .setWidth(expand())
                            .setHeight(dp(TILE_GRID_PILL_HEIGHT_DP))
                            .addContent(spacerDp(sideDp, 1))
                            .addContent(
                                periodDataCard(
                                    orderLabel = gridOrderLabel(item),
                                    subjectShort = parseSubject(item.subject).take(4),
                                    isCurrent = item.stableKey == currentKey,
                                    width = dp(halfWidthDp.toFloat())
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
                                        isCurrent = item.stableKey == currentKey
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
