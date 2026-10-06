package com.timetable.wear.tiles

import androidx.wear.protolayout.ActionBuilders
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
    isCurrent: Boolean
) = textButton(
    // 2-per-row grid: 3 short rows always fit vertically; textButton's
    // tight padding fits "1-2MACO" where DataCard slots truncate it.
    onClick = openAppClickable(),
    width = expand(),
    height = dp(48f),
    shape = shapes.full,
    colors = if (isCurrent) filledVariantButtonColors() else filledTonalButtonColors(),
    labelContent = {
        m3Text(
            "$orderLabel$subjectShort".layoutString,
            typography = Typography.LABEL_SMALL
        )
    }
)

internal fun MaterialScope.periodCardGroups(
    items: List<ScheduleItem>,
    currentKey: String?
): LayoutElementBuilders.LayoutElement {
    // M3E: merged periods (user chose 合併連堂) as full-width pill rows;
    // one period per row so nothing ever truncates, current highlighted.
    val merged = mergeTileItems(items.filter { it.type == ScheduleItemType.PERIOD })
    if (merged.isEmpty()) {
        return m3Text(
            "今日沒有課程".layoutString,
            typography = Typography.TITLE_SMALL
        )
    }
    return Column.Builder()
        .setWidth(expand())
        .setHeight(wrap())
        .apply {
            merged.chunked(2).forEach { row ->
                addContent(
                    buttonGroup(
                        width = expand(),
                        height = wrap()
                    ) {
                        row.forEach { item ->
                            buttonGroupItem {
                                periodDataCard(
                                    orderLabel = item.displayName.replace("第", "").replace("節", "")
                                        .ifBlank { item.periodNumber?.toString().orEmpty() },
                                    subjectShort = parseSubject(item.subject).take(4),
                                    isCurrent = item.stableKey == currentKey
                                )
                            }
                        }
                    }
                )
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
internal const val MAX_TILE_ROWS = 12
