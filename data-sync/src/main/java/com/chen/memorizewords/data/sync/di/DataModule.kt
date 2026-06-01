package com.chen.memorizewords.data.sync.di

import android.content.Context
import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.data.sync.local.mmkv.checkin.CheckInConfigDataSource
import com.chen.memorizewords.data.sync.local.mmkv.checkin.CheckInConfigDataSourceImpl
import com.chen.memorizewords.data.sync.local.mmkv.download.UpdateDownloadStore
import com.chen.memorizewords.data.sync.local.mmkv.onboarding.OnboardingSnapshotDataSource
import com.chen.memorizewords.data.sync.local.mmkv.onboarding.OnboardingSnapshotDataSourceImpl
import com.chen.memorizewords.data.sync.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.data.sync.local.mmkv.plan.StudyPlanDataSourceImpl
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideStudyPlanDataSource(
        mmkv: MMKV
    ): StudyPlanDataSource {
        return StudyPlanDataSourceImpl(mmkv)
    }

    @Provides
    @Singleton
    fun provideCheckInConfigDataSource(mmkv: MMKV): CheckInConfigDataSource {
        return CheckInConfigDataSourceImpl(mmkv)
    }

    @Provides
    @Singleton
    fun provideCheckInBusinessCalendar(
        checkInConfigDataSource: CheckInConfigDataSource
    ): CheckInBusinessCalendar {
        return CheckInBusinessCalendar(checkInConfigDataSource)
    }

    @Provides
    @Singleton
    fun provideOnboardingSnapshotDataSource(
        mmkv: MMKV,
        gson: Gson
    ): OnboardingSnapshotDataSource {
        return OnboardingSnapshotDataSourceImpl(mmkv, gson)
    }

    @Provides
    @Singleton
    fun provideMMKV(@ApplicationContext context: Context): MMKV {
        MMKV.initialize(context.applicationContext)
        return MMKV.defaultMMKV(MMKV.MULTI_PROCESS_MODE, null)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideUpdateDownloadStore(
        mmkv: MMKV,
        gson: Gson
    ): UpdateDownloadStore {
        return UpdateDownloadStore(mmkv, gson)
    }
}
