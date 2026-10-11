package com.timetable.wear.widgets

import androidx.glance.wear.AssociateWithGlanceWearWidget
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.GlanceWearWidgetService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Schedule Glance widget for watch-face widget slots (small containers).
 * The tiles carousel is served by the ProtoLayout
 * [com.timetable.wear.tiles.FullScheduleTileService] (full canvas).
 */
@AndroidEntryPoint
@AssociateWithGlanceWearWidget(ScheduleWidget::class)
class ScheduleWidgetService : GlanceWearWidgetService() {
    override val widget: GlanceWearWidget = ScheduleWidget()
}
