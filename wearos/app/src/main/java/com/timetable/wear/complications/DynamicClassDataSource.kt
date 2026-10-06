package com.timetable.wear.complications

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.timetable.wear.MainActivity
import com.timetable.wear.data.model.TimetableType
import com.timetable.wear.engine.TimetableEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 動態 complication：
 * - 非上課日 或 上課日但不在 07:30–末節後15m 內 → 長條 (RANGED_VALUE) + 文字 96 (電量，不含 %)
 * - 上課日 07:30–末節後15m 內 → 無長條 (SHORT_TEXT)：
 *   elapsed <15m → "12m 96%"（無科目）
 *   elapsed ≥15m → "PHY 12:00 96%"
 *   remaining ≤5m → "PHY 03:15 96%"（AOD 顯示 "PHY 03:-- 96%"，由錶面低頻更新體現，數據側固定回 MM:SS）
 * - 僅課堂顯示 %，長條模式不顯示 %
 */
@AndroidEntryPoint
class DynamicClassDataSource : AsyncComplicationDataSource() {

    @Inject lateinit var engine: TimetableEngine

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        requestScope.launch {
            val snapshot = engine.getTodaySnapshot()
            val now = java.time.LocalTime.now()
            val isSchoolDay = snapshot != null && snapshot.timetableType != TimetableType.NONE && snapshot.dayCycle != null
            val lastEnd = snapshot?.scheduleItems?.filter { it.type == com.timetable.wear.data.model.ScheduleItemType.PERIOD }?.maxOfOrNull { it.end } ?: ""
            val inSchoolHours = isSchoolDay && isSchoolHours(now, lastEnd)

            val data = when {
                !isSchoolDay || !inSchoolHours -> {
                    // 非上課天/非上課時段：長條顯示電量 0-6/充電/省電黃，短文字顯示「目前沒有課堂」
                    if (request.complicationType == ComplicationType.RANGED_VALUE) {
                        batteryBarOrShort(ComplicationType.RANGED_VALUE)
                    } else {
                        val t = PlainComplicationText.Builder("目前沒有課堂").build()
                        val tap = tapAction()
                        val smallImage = getBatteryLevel(applicationContext).let { lvl -> if (lvl >= 0) batterySmallImage(lvl) else null }
                        val builder = ShortTextComplicationData.Builder(t, t).setTapAction(tap)
                        if (smallImage != null) builder.setSmallImage(smallImage)
                        builder.build()
                    }
                }
                snapshot != null && isActiveTimedPeriod(snapshot.currentPeriod) -> classShortText(snapshot, request.complicationType)
                else -> {
                    val t = PlainComplicationText.Builder("目前沒有課堂").build()
                    val tap = tapAction()
                    ShortTextComplicationData.Builder(t, t).setTapAction(tap).build()
                }
            }
            listener.onComplicationData(data)
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.RANGED_VALUE -> {
            val text = PlainComplicationText.Builder("96").build()
            RangedValueComplicationData.Builder(96f, 0f, 100f, text).setText(text).build()
        }
        ComplicationType.SHORT_TEXT -> {
            val text = PlainComplicationText.Builder("PHY 12:00 96%").build()
            ShortTextComplicationData.Builder(text, text).build()
        }
        else -> null
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

    private fun batteryBarOrShort(type: ComplicationType): ComplicationData {
        val battery = getBatteryLevel(applicationContext)
        val level = battery.coerceIn(0, 100)
        val tap = tapAction()
        val smallImage = if (battery >= 0) batterySmallImage(level) else null
        return when (type) {
            ComplicationType.RANGED_VALUE -> {
                // 長條 + 旁邊文字 96 + 電池圖示（0-6 條、充電、滿電、省電黃）
                val text = PlainComplicationText.Builder(if (battery < 0) "—" else "$level").build()
                val builder = RangedValueComplicationData.Builder(level.toFloat(), 0f, 100f, text).setText(text).setTapAction(tap)
                if (smallImage != null) builder.setSmallImage(smallImage)
                builder.build()
            }
            ComplicationType.SHORT_TEXT -> {
                val t = PlainComplicationText.Builder(if (battery < 0) "—" else "$level").build()
                val builder = ShortTextComplicationData.Builder(t, t).setTapAction(tap)
                if (smallImage != null) builder.setSmallImage(smallImage)
                builder.build()
            }
            else -> NoDataComplicationData()
        }
    }

    private fun classShortText(snapshot: com.timetable.wear.engine.TodaySnapshot, type: ComplicationType): ComplicationData {
        val period = snapshot.currentPeriod
        val remaining = remainingSecondsFor(period.end).coerceAtLeast(0)
        val elapsed = elapsedSecondsFor(period.start).coerceAtLeast(0)
        val battery = getBatteryLevel(applicationContext)
        val batteryText = formatBatteryText(battery)

        val textStr = when (period.type) {
            com.timetable.wear.data.model.PeriodInfo.PeriodType.PERIOD -> {
                val short = parseSubject(period.subject)
                when {
                    elapsed < 15 * 60 -> "${formatMinutes(remaining)} $batteryText"
                    remaining <= 5 * 60 -> "$short ${formatMmSs(remaining)} $batteryText"
                    else -> "$short ${formatMmSs(remaining)} $batteryText"
                }
            }
            else -> {
                when {
                    elapsed < 15 * 60 -> "${formatMinutes(remaining)} $batteryText"
                    remaining <= 5 * 60 -> "${period.name} ${formatMmSs(remaining)} $batteryText"
                    else -> "${period.name} ${formatMmSs(remaining)} $batteryText"
                }
            }
        }

        val tap = tapAction()
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                val t = PlainComplicationText.Builder(textStr).build()
                ShortTextComplicationData.Builder(t, t).setTapAction(tap).build()
            }
            ComplicationType.RANGED_VALUE -> {
                // 上課時段不應顯示長條，降級為 SHORT_TEXT 文案但以 RANGED_VALUE 包裝（錶面若強制 RANGED_VALUE 會顯示條，盡量避免）
                // 此處回 SHORT_TEXT 樣式的 RANGED_VALUE，錶面會顯示條但我們已在外層避免此分支；保險起見回 SHORT_TEXT
                val t = PlainComplicationText.Builder(textStr).build()
                ShortTextComplicationData.Builder(t, t).setTapAction(tap).build()
            }
            else -> NoDataComplicationData()
        }
    }
}
