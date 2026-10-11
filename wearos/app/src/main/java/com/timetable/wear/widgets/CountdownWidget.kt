package com.timetable.wear.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.remote.creation.compose.action.pendingIntentAction
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
import androidx.compose.remote.creation.compose.modifier.fillMaxHeight
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.width
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
import com.timetable.wear.MainActivity
import com.timetable.wear.complications.formatCountdownMinutes
import com.timetable.wear.complications.isActiveTimedPeriod
import com.timetable.wear.complications.mergedPeriodBlock
import com.timetable.wear.complications.parseSubject
import com.timetable.wear.complications.remainingSecondsFor
import com.timetable.wear.complications.secondsUntil
import com.timetable.wear.data.model.PeriodInfo
import com.timetable.wear.data.model.ScheduleItemType
import com.timetable.wear.data.model.TimetableType
import com.timetable.wear.tiles.shortMonthDay
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

/** Display model for the countdown widget (computed once per update). */
data class CountdownModel(
    val title: String,
    /** Single subject line, e.g. "第1-2節 CHIN". */
    val subjectLine: String,
    val countdown: String,
    val dayLabel: String,
    val nextLabel: String,
    // Geometry derived from the real container size.
    val titleSizeSp: Int = 12,
    val countdownSizeSp: Int = 18,
    val subjectSizeSp: Int = 10,
    val pillSizeSp: Int = 11,
    val rowGapDp: Float = 3f,
    val leftWidthDp: Float = 59f,
    val rightWidthDp: Float = 59f,
    val leftHeightDp: Float = 55f,
    val pillHeightDp: Float = 26f,
    /** Half of the shorter side: clips a true stadium (CircleShape renders an ellipse). */
    val leftRadiusDp: Float = 27f,
    /** M3 Expressive dynamic colors resolved on-device per update. */
    val palette: WidgetPalette = staticPalette()
)

class CountdownWidget : GlanceWearWidget() {

    override suspend fun provideWidgetData(
        context: Context,
        params: WearWidgetParams
    ): WearWidgetData {
        android.util.Log.d(
            "CountdownWidget",
            "params w=${params.widthDp} h=${params.heightDp} type=${params.containerType} " +
                "padH=${params.horizontalPaddingDp} padV=${params.verticalPaddingDp} " +
                "radius=${params.cornerRadiusDp}"
        )
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val engine = entry.engine()
        val prefs = entry.uiPreferences()
        val snapshot = engine.getTileSnapshot() ?: engine.getTodaySnapshot()
        val palette = paletteFor(context)
        val model = snapshot?.let { buildModel(it, prefs.mergeConsecutive.first(), params, palette) }
        return WearWidgetDocument(background = WearWidgetBrush.color(Color(0xFF000000).rc)) {
            if (model == null) {
                WidgetCenterText("載入中…", 14, palette.onSurface)
            } else {
                CountdownContent(model)
            }
        }
    }

    override suspend fun onAdded(context: Context, handle: ActiveWearWidgetHandle) {
        startWidgetTicker(context)
        CountdownWidget().triggerUpdateAll(context)
    }

    private fun buildModel(
        snapshot: com.timetable.wear.engine.TodaySnapshot,
        merge: Boolean,
        params: WearWidgetParams,
        palette: WidgetPalette
    ): CountdownModel {
        // Geometry from the real container (debug FULLSCREEN on 192dp is only
        // ~121x73dp of content, far smaller than the 192dp kit canvas).
        val contentW = (params.widthDp - 2 * params.horizontalPaddingDp).coerceAtLeast(100f)
        val contentH = (params.heightDp - 2 * params.verticalPaddingDp).coerceAtLeast(60f)
        val gap = 3f
        // No in-content title: the system already shows the widget label.
        // That title slot (~28dp) must be subtracted or the bottom pill is
        // clipped by the viewport (seen on watch7small: 137x89dp container).
        val rowH = (contentH - 2f - TILE_TITLE_CHROME_DP).coerceAtLeast(40f)
        val leftW = ((contentW - gap) / 2).coerceAtLeast(50f)
        val rightW = (contentW - gap - leftW).coerceAtLeast(45f)
        val pillH = ((rowH - gap) / 2).coerceAtLeast(20f)
        fun geo(
            title: String,
            subjectLine: String,
            countdown: String,
            dayLabel: String,
            nextLabel: String
        ) = CountdownModel(
            title = title,
            subjectLine = subjectLine,
            countdown = countdown,
            countdownSizeSp = if (countdown.length > 5) 13 else 18,
            dayLabel = dayLabel,
            nextLabel = nextLabel,
            rowGapDp = gap,
            leftWidthDp = leftW,
            rightWidthDp = rightW,
            leftHeightDp = rowH,
            pillHeightDp = pillH,
            leftRadiusDp = minOf(leftW, rowH) / 2,
            palette = palette
        )
        if (snapshot.isNextDay) {
            val day = snapshot.dayCycle?.let { "Day $it" } ?: snapshot.dateDisplay
            // Day-first short date: the full "10月12日 星期一" never fits the
            // ~59dp left box in one line (same rule as tile titles).
            return geo("下次上課", shortMonthDay(snapshot.dateDisplay).ifBlank { snapshot.dateDisplay }, "--:--", day, "已放學")
        }
        if (snapshot.timetableType == TimetableType.NONE || snapshot.dayCycle == null) {
            return geo(
                "課堂倒數", "今日沒有課程", "--:--",
                snapshot.dateDisplay.ifBlank { "今日" }, "已放學"
            )
        }
        val current = snapshot.currentPeriod
        val isTimed = isActiveTimedPeriod(current) && current.end.isNotBlank()
        val block = if (isTimed) mergedPeriodBlock(snapshot.scheduleItems, current, merge) else null
        val subjectLine: String
        val countdown: String
        if (isTimed) {
            val end = if (current.type == PeriodInfo.PeriodType.PERIOD) {
                block?.end ?: current.end
            } else {
                current.end
            }
            val remaining = remainingSecondsFor(end).coerceAtLeast(0)
            countdown = formatCountdownMinutes(remaining)
            subjectLine = when (current.type) {
                PeriodInfo.PeriodType.PERIOD -> {
                    val blockName = block?.displayName ?: current.name
                    "$blockName ${parseSubject(current.subject)}".trim()
                }
                else -> current.name.ifBlank { "空堂時間" }
            }
        } else {
            val next = snapshot.nextPeriod
            val remaining = next?.let { secondsUntil(it.start).coerceAtLeast(0) } ?: 0
            countdown = if (next == null) "--:--" else formatCountdownMinutes(remaining)
            subjectLine = next?.let {
                when (it.type) {
                    // Kit left box is countdown + subject only: the start time
                    // cannot fit the ~59dp box next to "下一堂 SUBJECT".
                    // "下一堂" is for classes only: breaks/lunch/assembly
                    // show their own name with no prefix.
                    PeriodInfo.PeriodType.PERIOD -> "下一堂 ${parseSubject(it.subject)}"
                    else -> it.name.ifBlank { "休息" }
                }
            } ?: "已放學"
        }
        val dayLabel = snapshot.dayCycle?.let { "Day $it" } ?: snapshot.dateDisplay.ifBlank { "今日課表" }
        val nextLabel = if (block != null) {
            val after = snapshot.scheduleItems
                .filter { it.start.isNotBlank() && it.start > block.end }
                .minByOrNull { it.start }
            when {
                after == null -> "已放學"
                after.type == ScheduleItemType.PERIOD -> parseSubject(after.subject).take(4).trim()
                else -> after.displayName
            }
        } else {
            snapshot.nextPeriod?.let { next ->
                when (next.type) {
                    PeriodInfo.PeriodType.PERIOD -> parseSubject(next.subject).take(4).trim()
                    else -> next.name
                }
            } ?: "已放學"
        }
        return geo("課堂倒數", subjectLine, countdown, dayLabel, nextLabel)
    }
}

/**
 * Figma page 20 (M3 Tiles Design Kit): title over a 152dp row — 74dp light
 * box (countdown + subject) beside two stacked 74x53dp pills (day + next).
 */
@RemoteComposable
@Composable
fun CountdownContent(m: CountdownModel) {
    RemoteBox(
        modifier = RemoteModifier.fillMaxSize().clickable(openAppAction()),
        contentAlignment = RemoteAlignment.Center
    ) {
        RemoteColumn(
            modifier = RemoteModifier.fillMaxSize(),
            verticalArrangement = RemoteArrangement.Center,
            horizontalAlignment = RemoteAlignment.CenterHorizontally
        ) {
            RemoteRow(
                modifier = RemoteModifier.width((m.leftWidthDp + m.rowGapDp + m.rightWidthDp).rdp),
                verticalAlignment = RemoteAlignment.CenterVertically
            ) {
                // Left light box: a tall stadium (kit page 20), not a rounded
                // square — CircleShape renders an ellipse on tall boxes, so
                // use an explicit half-side radius for a true capsule.
                RemoteBox(
                    modifier = RemoteModifier.width(m.leftWidthDp.rdp).height(m.leftHeightDp.rdp)
                        .clip(RemoteRoundedCornerShape(m.leftRadiusDp.rdp))
                        .background(Color(m.palette.leftBg).rc),
                    contentAlignment = RemoteAlignment.Center
                ) {
                    RemoteColumn(
                        modifier = RemoteModifier.fillMaxWidth(),
                        horizontalAlignment = RemoteAlignment.CenterHorizontally
                    ) {
                        RemoteText(
                            text = m.countdown.rs,
                            modifier = RemoteModifier.width((m.leftWidthDp - 10f).rdp),
                            color = Color(m.palette.leftText).rc,
                            fontSize = m.countdownSizeSp.rsp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        RemoteBox(modifier = RemoteModifier.height(2.rdp).fillMaxWidth()) {}
                        RemoteText(
                            text = m.subjectLine.rs,
                            modifier = RemoteModifier.width((m.leftWidthDp - 10f).rdp),
                            color = Color(m.palette.leftText).rc,
                            fontSize = m.subjectSizeSp.rsp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                RemoteBox(modifier = RemoteModifier.width(m.rowGapDp.rdp).fillMaxHeight()) {}
                // Right stacked pills (true stadiums via half-height radius).
                RemoteColumn(modifier = RemoteModifier.width(m.rightWidthDp.rdp)) {
                    WidgetPill(m.dayLabel, m.pillSizeSp, m.palette.pillTopBg, m.palette.pillTopText, m.rightWidthDp, m.pillHeightDp, m.pillHeightDp / 2)
                    RemoteBox(modifier = RemoteModifier.height(m.rowGapDp.rdp).fillMaxWidth()) {}
                    WidgetPill(m.nextLabel, m.pillSizeSp, m.palette.pillBottomBg, m.palette.pillBottomText, m.rightWidthDp, m.pillHeightDp, m.pillHeightDp / 2)
                }
            }
        }
    }
}

/** Kit stadium pill, single centered line, sized by the model. */
@RemoteComposable
@Composable
fun WidgetPill(text: String, sizeSp: Int, bgArgb: Long, textArgb: Long, widthDp: Float, heightDp: Float, radiusDp: Float) {
    RemoteBox(
        modifier = RemoteModifier.width(widthDp.rdp).height(heightDp.rdp)
            .clip(RemoteRoundedCornerShape(radiusDp.rdp))
            .background(Color(bgArgb).rc),
        contentAlignment = RemoteAlignment.Center
    ) {
        RemoteText(
            text = text.rs,
            modifier = RemoteModifier.width((widthDp - 8f).rdp),
            color = Color(textArgb).rc,
            fontSize = sizeSp.rsp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
