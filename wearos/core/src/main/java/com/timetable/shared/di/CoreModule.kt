package com.timetable.shared.di

import android.content.Context
import com.timetable.shared.data.local.BundledTimetableSource
import com.timetable.shared.data.local.AssetBundledTimetableSource
import com.timetable.shared.data.local.TimetableCache
import com.timetable.shared.data.local.TimetableCacheStore
import com.timetable.shared.data.local.UiPreferences
import com.timetable.shared.data.remote.TimetableFetcher
import com.timetable.shared.data.remote.TimetableRemoteSource
import com.timetable.shared.data.repository.TimetableRepository
import com.timetable.shared.data.repository.ScheduleUpdateListener
import com.timetable.shared.engine.TimetableEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideTimetableCache(@ApplicationContext context: Context): TimetableCacheStore {
        return TimetableCache(context)
    }

    @Provides
    @Singleton
    fun provideUiPreferences(@ApplicationContext context: Context): UiPreferences {
        return UiPreferences(context)
    }

    @Provides
    @Singleton
    fun provideTimetableFetcher(): TimetableRemoteSource {
        return TimetableFetcher()
    }

    @Provides
    @Singleton
    fun provideBundledTimetableSource(
        @ApplicationContext context: Context
    ): BundledTimetableSource = AssetBundledTimetableSource(context)

    @Provides
    @Singleton
    fun provideTimetableRepository(
        fetcher: TimetableRemoteSource,
        cache: TimetableCacheStore,
        scheduleUpdateListener: ScheduleUpdateListener,
        bundledSource: BundledTimetableSource,
    ): TimetableRepository {
        return TimetableRepository(fetcher, cache, scheduleUpdateListener, bundledSource)
    }


    @Provides
    @Singleton
    fun provideTimetableEngine(
        repository: TimetableRepository,
        uiPreferences: UiPreferences
    ): TimetableEngine {
        return TimetableEngine(repository, uiPreferences)
    }
}
