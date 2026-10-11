package com.timetable.wear.widgets

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground ticker: widgets have no tile-style freshness refresh, so this
 * pushes fresh countdown/schedule documents every 30s while it runs. Started
 * when a widget is added (or the app opens); personal-use only.
 */
class WidgetTickService : Service() {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private var tickJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(
            NOTIFICATION_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("課表更新中")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (tickJob?.isActive != true) {
            tickJob = scope.launch {
                while (isActive) {
                    runCatching { CountdownWidget().triggerUpdateAll(this@WidgetTickService) }
                    runCatching { ScheduleWidget().triggerUpdateAll(this@WidgetTickService) }
                    delay(TICK_MILLIS)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "小工具更新", NotificationManager.IMPORTANCE_MIN)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "widget_tick"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MILLIS = 30_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WidgetTickService::class.java))
        }
    }
}
