package com.timetable.wear.di

import android.content.Context
import com.timetable.shared.data.repository.ScheduleUpdateListener
import com.timetable.wear.complications.WearScheduleUpdateListener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WearModule {

    @Provides
    @Singleton
    fun provideScheduleUpdateListener(
        @ApplicationContext context: Context
    ): ScheduleUpdateListener = WearScheduleUpdateListener(context)
}
