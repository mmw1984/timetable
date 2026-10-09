package com.timetable.wear.di

import android.content.Context
import com.timetable.wear.data.local.BundledTimetableSource
import com.timetable.wear.data.local.AssetBundledTimetableSource
import com.timetable.wear.data.local.TimetableCache
import com.timetable.wear.data.local.TimetableCacheStore
import com.timetable.wear.data.local.UiPreferences
import com.timetable.wear.data.remote.TimetableFetcher
import com.timetable.wear.data.remote.TimetableRemoteSource
import com.timetable.wear.data.repository.TimetableRepository
import com.timetable.wear.data.repository.ComplicationRefreshRequester
import com.timetable.wear.data.repository.WearComplicationRefreshRequester
import com.timetable.wear.engine.TimetableEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
        complicationRefreshRequester: ComplicationRefreshRequester,
        bundledSource: BundledTimetableSource,
    ): TimetableRepository {
        return TimetableRepository(fetcher, cache, complicationRefreshRequester, bundledSource)
    }

    @Provides
    @Singleton
    fun provideComplicationRefreshRequester(
        @ApplicationContext context: Context
    ): ComplicationRefreshRequester = WearComplicationRefreshRequester(context)

    @Provides
    @Singleton
    fun provideTimetableEngine(repository: TimetableRepository): TimetableEngine {
        return TimetableEngine(repository)
    }
}
