package com.chen.memorizewords.data.di

import android.content.Context
import com.chen.memorizewords.data.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.local.mmkv.auth.AuthLocalDataSourceImpl
import com.chen.memorizewords.data.local.mmkv.checkin.CheckInConfigDataSource
import com.chen.memorizewords.data.local.mmkv.checkin.CheckInConfigDataSourceImpl
import com.chen.memorizewords.data.local.mmkv.download.UpdateDownloadStore
import com.chen.memorizewords.data.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.data.local.mmkv.plan.StudyPlanDataSourceImpl
import com.chen.memorizewords.data.remote.RemoteResultAdapter
import com.chen.memorizewords.data.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.remote.user.RemoteAuthDataSourceImpl
import com.chen.memorizewords.data.session.AuthSessionRefreshRemoteDataSource
import com.chen.memorizewords.data.session.LocalAuthStateProvider
import com.chen.memorizewords.data.session.LocalAuthStateCleaner
import com.chen.memorizewords.data.session.LocalAuthSessionCleaner
import com.chen.memorizewords.data.session.LocalUserDataOwnerDataSource
import com.chen.memorizewords.data.session.LocalUserDataOwnerDataSourceImpl
import com.chen.memorizewords.data.session.MMKVInitializer
import com.chen.memorizewords.data.session.SessionManager
import com.chen.memorizewords.data.session.SessionLocalDataSource
import com.chen.memorizewords.data.session.SessionRefreshDataSource
import com.chen.memorizewords.data.session.SessionKickoutNotifierImpl
import com.chen.memorizewords.data.session.TokenLocalDataSource
import com.chen.memorizewords.data.session.UnauthorizedSessionHandler
import com.chen.memorizewords.data.session.UserDataCleaner
import com.chen.memorizewords.domain.auth.AuthStateProvider
import com.chen.memorizewords.domain.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.auth.TokenProvider
import com.chen.memorizewords.network.api.auth.AuthRequest
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Provider

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAuthRemoteDataSource(
        authRequest: AuthRequest,
        remoteResultAdapter: RemoteResultAdapter
    ): AuthRemoteDataSource {
        return RemoteAuthDataSourceImpl(authRequest, remoteResultAdapter)
    }

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
    fun provideAuthLocalDataSource(mmkv: MMKV): AuthLocalDataSource {
        return AuthLocalDataSourceImpl(mmkv)
    }

    @Provides
    @Singleton
    fun provideLocalUserDataOwnerDataSource(mmkv: MMKV): LocalUserDataOwnerDataSource {
        return LocalUserDataOwnerDataSourceImpl(mmkv)
    }

    @Provides
    @Singleton
    fun provideMMKV(@ApplicationContext context: Context): MMKV {
        MMKVInitializer.initialize(context)
        return MMKV.defaultMMKV(MMKV.MULTI_PROCESS_MODE, null)
    }

    @Provides
    @Singleton
    fun provideSessionLocalDataSource(mmkv: MMKV): SessionLocalDataSource {
        return TokenLocalDataSource(mmkv)
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        sessionLocalDataSource: SessionLocalDataSource,
        sessionRefreshDataSourceProvider: Provider<SessionRefreshDataSource>,
        localAuthStateCleaner: LocalAuthStateCleaner
    ): SessionManager {
        return SessionManager(
            local = sessionLocalDataSource,
            refreshRemoteDataSourceProvider = sessionRefreshDataSourceProvider,
            localAuthSessionCleaner = localAuthStateCleaner
        )
    }

    @Provides
    @Singleton
    fun provideTokenProvider(sessionManager: SessionManager): TokenProvider {
        return sessionManager
    }

    @Provides
    @Singleton
    fun provideAuthStateProvider(localAuthStateProvider: LocalAuthStateProvider): AuthStateProvider {
        return localAuthStateProvider
    }

    @Provides
    @Singleton
    fun provideSessionKickoutNotifier(): SessionKickoutNotifier {
        return SessionKickoutNotifierImpl()
    }

    @Provides
    @Singleton
    fun provideLocalAuthStateCleaner(
        localAuthSessionCleaner: LocalAuthSessionCleaner
    ): LocalAuthStateCleaner {
        return localAuthSessionCleaner
    }

    @Provides
    @Singleton
    fun provideSessionRefreshDataSource(
        authSessionRefreshRemoteDataSource: AuthSessionRefreshRemoteDataSource
    ): SessionRefreshDataSource {
        return authSessionRefreshRemoteDataSource
    }

    @Provides
    @Singleton
    fun provideUnauthorizedSessionHandler(
        localAuthStateCleaner: LocalAuthStateCleaner
    ): UnauthorizedSessionHandler {
        return UnauthorizedSessionHandler(localAuthStateCleaner)
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
