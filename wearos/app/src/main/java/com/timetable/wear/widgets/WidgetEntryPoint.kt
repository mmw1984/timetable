package com.timetable.wear.widgets

import com.timetable.shared.data.local.UiPreferences
import com.timetable.shared.engine.TimetableEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt access for widgets (Glance widgets are not injectable services). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun engine(): TimetableEngine
    fun uiPreferences(): UiPreferences
}
