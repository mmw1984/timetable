package com.timetable.wear.widgets

import androidx.glance.wear.AssociateWithGlanceWearWidget
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.GlanceWearWidgetService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Countdown Glance widget for watch-face widget slots (small containers).
 * The tiles carousel is served by the ProtoLayout
 * [com.timetable.wear.tiles.CountdownTileService] (full canvas).
 */
@AndroidEntryPoint
@AssociateWithGlanceWearWidget(CountdownWidget::class)
class CountdownWidgetService : GlanceWearWidgetService() {
    override val widget: GlanceWearWidget = CountdownWidget()
}
