package com.timetable.phone.widget

import com.timetable.shared.data.local.UiPreferences
import com.timetable.shared.engine.TimetableEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt access for the Glance widget (GlanceAppWidget is not injectable). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun engine(): TimetableEngine
    fun uiPreferences(): UiPreferences
}
