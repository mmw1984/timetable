package com.timetable.phone.di

import android.content.Context
import com.timetable.phone.widget.PhoneScheduleUpdateListener
import com.timetable.shared.data.repository.ScheduleUpdateListener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PhoneModule {

    @Provides
    @Singleton
    fun provideScheduleUpdateListener(
        @ApplicationContext context: Context
    ): ScheduleUpdateListener = PhoneScheduleUpdateListener(context)
}
