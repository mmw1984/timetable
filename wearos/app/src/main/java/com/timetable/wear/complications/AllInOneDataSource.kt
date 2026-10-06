package com.timetable.wear.complications

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.timetable.wear.MainActivity
import com.timetable.wear.data.model.PeriodInfo
import com.timetable.wear.engine.TodaySnapshot
import com.timetable.wear.engine.TimetableEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AllInOneDataSource : AsyncComplicationDataSource() {

    @Inject lateinit var engine: TimetableEngine

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        if (request.complicationType != ComplicationType.LONG_TEXT) {
            listener.onComplicationData(NoDataComplicationData())
            return
        }
        requestScope.launch {
            val snapshot = engine.getTodaySnapshot()
            val now = java.time.LocalTime.now()
            val isSchoolDay = snapshot != null && snapshot.timetableType != com.timetable.wear.data.model.TimetableType.NONE && snapshot.dayCycle != null
            val lastEnd = snapshot?.scheduleItems?.filter { it.type == com.timetable.wear.data.model.ScheduleItemType.PERIOD }?.maxOfOrNull { it.end } ?: ""
            val inSchoolHours = isSchoolDay && isSchoolHours(now, lastEnd)
            val battery = getBatteryLevel(applicationContext)
            val text = if (!isSchoolDay || !inSchoolHours) {
                "電量 ${formatBatteryText(battery)}"
            } else if (snapshot != null && isActiveTimedPeriod(snapshot.currentPeriod)) {
                fullText(snapshot, battery)
            } else {
                "電量 ${formatBatteryText(battery)}"
            }
            listener.onComplicationData(buildLongText(text))
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.LONG_TEXT) buildLongText("PHY 12:00 96%\n下堂: ICT 11:10") else null

    private fun fullText(snapshot: TodaySnapshot, battery: Int): String {
        val period = snapshot.currentPeriod
        val remaining = remainingSecondsFor(period.end).coerceAtLeast(0)
        val elapsed = elapsedSecondsFor(period.start).coerceAtLeast(0)
        val batteryText = formatBatteryText(battery)
        val line1 = if (period.type == PeriodInfo.PeriodType.PERIOD) {
            val subjectShort = parseSubject(period.subject)
            when {
                elapsed < 15 * 60 -> "${formatMinutes(remaining)} $batteryText"
                remaining <= 5 * 60 -> "$subjectShort ${formatMmSs(remaining)} $batteryText"
                else -> "$subjectShort ${formatMmSs(remaining)} $batteryText"
            }
        } else {
            when {
                elapsed < 15 * 60 -> "${formatMinutes(remaining)} $batteryText"
                remaining <= 5 * 60 -> "${period.name} ${formatMmSs(remaining)} $batteryText"
                else -> "${period.name} ${formatMmSs(remaining)} $batteryText"
            }
        }
        val next = snapshot.nextPeriod
        val line2 = when (next?.type) {
            PeriodInfo.PeriodType.PERIOD -> "下堂: ${parseSubject(next.subject)} ${next.start}"
            PeriodInfo.PeriodType.BREAK_TIME, PeriodInfo.PeriodType.ASSEMBLY -> "下堂: ${next.name}"
            else -> if (snapshot.scheduleItems.isEmpty()) "—" else "放學"
        }
        return "$line1\n$line2"
    }

    private fun tapAction(): PendingIntent? = PendingIntent.getActivity(
        applicationContext,
        0,
        Intent(applicationContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun batterySmallImage(level: Int): androidx.wear.watchface.complications.data.SmallImage? {
        return try {
            val res = getBatteryIconRes(applicationContext, level)
            val icon = android.graphics.drawable.Icon.createWithResource(applicationContext, res)
            androidx.wear.watchface.complications.data.SmallImage.Builder(icon, androidx.wear.watchface.complications.data.SmallImageType.ICON).build()
        } catch (_: Exception) { null }
    }

    private fun buildLongText(text: String): LongTextComplicationData {
        val complicationText = PlainComplicationText.Builder(text).build()
        val builder = LongTextComplicationData.Builder(complicationText, complicationText).setTapAction(tapAction())
        // Add battery icon for non-school / battery fallback cases
        if (text.startsWith("電量")) {
            val level = getBatteryLevel(applicationContext)
            val img = if (level >= 0) batterySmallImage(level) else null
            if (img != null) builder.setSmallImage(img)
        }
        return builder.build()
    }
}
