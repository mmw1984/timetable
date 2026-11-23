package com.timetable.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.android.EntryPointAccessors
import com.timetable.phone.MainActivity
import com.timetable.shared.data.model.PeriodInfo
import com.timetable.shared.engine.TodaySnapshot
import com.timetable.shared.util.formatMinutes
import com.timetable.shared.util.parseSubject
import com.timetable.shared.util.remainingSecondsFor

/**
 * Home-screen widget: current class + rough remaining time + next class.
 * Refreshed on schedule updates, widget updates (>=30min) and app resume.
 */
class NextClassWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            ).engine().getTileSnapshot()
        }.getOrNull()

        provideContent {
            GlanceTheme {
                WidgetContent(snapshot)
            }
        }
    }

    @Composable
    private fun WidgetContent(snapshot: TodaySnapshot?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (snapshot == null) {
                Text(
                    text = "載入中…",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp
                    )
                )
                return@Column
            }

            val current = snapshot.currentPeriod
            val header = buildString {
                append(snapshot.dateDisplay)
                snapshot.dayCycle?.let { append(" · Day $it") }
            }
            Text(
                text = header,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(4.dp))

            if (current.type == PeriodInfo.PeriodType.NONE) {
                Text(
                    text = "今日冇課",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parseSubject(current.subject),
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
                val line = buildString {
                    if (current.name.isNotBlank()) append(current.name)
                    if (current.start.isNotBlank() && current.end.isNotBlank()) {
                        append("  ${current.start}–${current.end}")
                    }
                    val remaining = remainingSecondsFor(current.end)
                    if (remaining > 0 && current.end.isNotBlank()) {
                        append(" · 尚餘 ${formatMinutes(remaining)}")
                    }
                }
                Text(
                    text = line,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp
                    ),
                    maxLines = 1
                )
            }

            Spacer(GlanceModifier.height(4.dp))
            val next = snapshot.nextPeriod
            if (next != null) {
                Text(
                    text = "下堂: ${parseSubject(next.subject)}" +
                        if (next.start.isNotBlank()) " ${next.start}" else "",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
