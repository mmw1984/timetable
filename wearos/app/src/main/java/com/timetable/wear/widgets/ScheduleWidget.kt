package com.timetable.wear.widgets

import android.content.Context
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.WearWidgetBrush
import androidx.glance.wear.color
import androidx.glance.wear.WearWidgetData
import androidx.glance.wear.WearWidgetDocument
import androidx.glance.wear.core.ActiveWearWidgetHandle
import androidx.glance.wear.core.WearWidgetParams
import com.timetable.wear.complications.parseSubject
import com.timetable.wear.data.model.ScheduleItem
import com.timetable.wear.tiles.shortMonthDay
import com.timetable.wear.tiles.tileGroups
import com.timetable.wear.tiles.tileRowSplit
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

data class SlotModel(
    val line1: String,
    val line2: String,
    val line1SizeSp: Int,
    val line2SizeSp: Int,
    val isCurrent: Boolean,
    val widthDp: Float,
    val heightDp: Float,
    val bgArgb: Long,
    val textArgb: Long
)

data class ScheduleModel(
    val title: String,
    val titleSizeSp: Int,
    val rows: List<List<SlotModel>>,
    val rowGapDp: Float,
    val contentWidthDp: Float
)

class ScheduleWidget : GlanceWearWidget() {

    override suspend fun provideWidgetData(
        context: Context,
        params: WearWidgetParams
    ): WearWidgetData {
        android.util.Log.d(
            "ScheduleWidget",
            "params w=${params.widthDp} h=${params.heightDp} type=${params.containerType} " +
                "padH=${params.horizontalPaddingDp} padV=${params.verticalPaddingDp} " +
                "radius=${params.cornerRadiusDp}"
        )
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val engine = entry.engine()
        val prefs = entry.uiPreferences()
        val snapshot = engine.getTileSnapshot() ?: engine.getTodaySnapshot()
        val palette = paletteFor(context)
        val model = snapshot?.let {
            buildModel(it, prefs.mergeConsecutive.first(), params, palette)
        }
        return WearWidgetDocument(background = WearWidgetBrush.color(Color(0xFF000000).rc)) {
            if (model == null) {
                WidgetCenterText("載入中…", 14, palette.onSurface)
            } else {
                ScheduleContent(model)
            }
        }
    }

    override suspend fun onAdded(context: Context, handle: ActiveWearWidgetHandle) {
        startWidgetTicker(context)
        ScheduleWidget().triggerUpdateAll(context)
    }

    private fun buildModel(
        snapshot: com.timetable.wear.engine.TodaySnapshot,
        merge: Boolean,
        params: WearWidgetParams,
        palette: WidgetPalette
    ): ScheduleModel {
        val dayLabel = snapshot.dayCycle?.let { "Day $it" } ?: ""
        val datePart = shortMonthDay(snapshot.dateDisplay)
        val dayTitle = if (datePart.isNotBlank() && dayLabel.isNotBlank()) {
            "$datePart $dayLabel"
        } else {
            datePart.ifBlank { dayLabel }
        }
        val title = if (snapshot.isNextDay) "下次·$dayTitle".trim('·') else dayTitle.ifBlank { "今日課表" }
        val groups = tileGroups(snapshot.scheduleItems, merge)
        // Geometry from the real container (debug FULLSCREEN on 192dp is only
        // ~121x73dp of content: title + compact grid, nothing fixed).
        val contentW = (params.widthDp - 2 * params.horizontalPaddingDp).coerceAtLeast(100f)
        val contentH = (params.heightDp - 2 * params.verticalPaddingDp).coerceAtLeast(60f)
        if (groups.isEmpty()) {
            return ScheduleModel("今日沒有課程", 12, emptyList(), 3f, contentW)
        }
        val gap = 3f
        // No in-content title: the system already shows the widget label,
        // and day/date are not shown on this widget per design. That title
        // slot (~28dp) must be subtracted or the grid overflows (watch7small).
        val gridH = (contentH - 2f - TILE_TITLE_CHROME_DP).coerceAtLeast(30f)
        val split = tileRowSplit(groups.size)
        val pillH = ((gridH - gap * (split.size - 1)) / split.size).coerceAtLeast(18f)
        val currentKey = if (snapshot.isNextDay) {
            null
        } else {
            snapshot.scheduleItems.firstOrNull {
                it.start == snapshot.currentPeriod.start && it.end == snapshot.currentPeriod.end && it.start.isNotBlank()
            }?.stableKey
        }
        var index = 0
        val rows = split.map { slots ->
            val row = groups.subList(index, minOf(index + slots, groups.size))
            index += slots
            val gaps = gap * (row.size - 1).coerceAtLeast(0)
            val slotWidth = ((contentW - gaps) / row.size).coerceAtLeast(30f)
            row.map { slotModel(it, it.stableKey == currentKey, slotWidth, pillH, palette) }
        }
        return ScheduleModel(title, 12, rows, gap, contentW)
    }

    private fun slotModel(
        item: ScheduleItem,
        isCurrent: Boolean,
        widthDp: Float,
        heightDp: Float,
        palette: WidgetPalette
    ): SlotModel {
        val order = item.displayName.replace("第", "").replace("節", "").ifBlank {
            item.periodNumber?.toString().orEmpty()
        }
        val subject = parseSubject(item.subject).take(4)
        val bg = if (isCurrent) palette.pillBottomBg else palette.pillTopBg
        val fg = if (isCurrent) palette.pillBottomText else palette.pillTopText
        // Current is a two-line card when there is room, else a highlighted pill.
        return if (isCurrent && heightDp >= 30f && widthDp >= 55f) {
            SlotModel(subject, order, 14, 10, true, widthDp, heightDp, bg, fg)
        } else if (widthDp >= 48f) {
            SlotModel("$order $subject", "", 11, 10, isCurrent, widthDp, heightDp, bg, fg)
        } else {
            SlotModel(subject, "", 10, 10, isCurrent, widthDp, heightDp, bg, fg)
        }
    }
}

/** Figma page 21: title over a 4dp-gap grid of stadium pills / current cards. */
@RemoteComposable
@Composable
fun ScheduleContent(m: ScheduleModel) {
    RemoteBox(
        modifier = RemoteModifier.fillMaxSize().clickable(openAppAction()),
        contentAlignment = RemoteAlignment.Center
    ) {
        RemoteColumn(
            modifier = RemoteModifier.fillMaxSize(),
            verticalArrangement = RemoteArrangement.Center,
            horizontalAlignment = RemoteAlignment.CenterHorizontally
        ) {
            m.rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    RemoteBox(modifier = RemoteModifier.height(m.rowGapDp.rdp).fillMaxWidth()) {}
                }
                RemoteRow(
                    modifier = RemoteModifier.width(m.contentWidthDp.rdp)
                ) {
                    row.forEachIndexed { slotIndex, slot ->
                        if (slotIndex > 0) {
                            RemoteBox(modifier = RemoteModifier.width(4.rdp)) {}
                        }
                        ScheduleSlot(slot)
                    }
                }
            }
        }
    }
}

@RemoteComposable
@Composable
fun ScheduleSlot(s: SlotModel) {
    RemoteBox(
        modifier = RemoteModifier.width(s.widthDp.rdp).height(s.heightDp.rdp)
            .clip(if (s.line2.isNotBlank()) RemoteRoundedCornerShape(26.rdp) else RemoteCircleShape)
            .background(Color(s.bgArgb).rc),
        contentAlignment = RemoteAlignment.Center
    ) {
        RemoteColumn(modifier = RemoteModifier.fillMaxWidth(), horizontalAlignment = RemoteAlignment.CenterHorizontally) {
            RemoteText(
                text = s.line1.rs,
                modifier = RemoteModifier.width((s.widthDp - 8f).rdp),
                color = Color(s.textArgb).rc,
                fontSize = s.line1SizeSp.rsp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (s.line2.isNotBlank()) {
                RemoteText(
                    text = s.line2.rs,
                    modifier = RemoteModifier.width((s.widthDp - 8f).rdp),
                    color = Color(s.textArgb).rc,
                    fontSize = s.line2SizeSp.rsp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
