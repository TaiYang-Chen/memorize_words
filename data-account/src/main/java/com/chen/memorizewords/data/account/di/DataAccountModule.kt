package com.chen.memorizewords.data.account.di

import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSourceImpl
import com.chen.memorizewords.data.account.local.avatar.AvatarLocalDataSource
import com.chen.memorizewords.data.account.local.avatar.AvatarLocalDataSourceImpl
import com.chen.memorizewords.data.account.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.account.remote.user.RemoteAuthDataSourceImpl
import com.chen.memorizewords.data.account.remoteapi.api.auth.AuthApiService
import com.chen.memorizewords.data.account.repository.AccountSessionRepositoryImpl
import com.chen.memorizewords.data.account.repository.LocalAccountRepositoryImpl
import com.chen.memorizewords.data.account.repository.UserScopedDataCleanerImpl
import com.chen.memorizewords.data.account.repository.user.AuthRepositoryImpl
import com.chen.memorizewords.data.account.repository.user.UserRepositoryImpl
import com.chen.memorizewords.data.account.session.AuthSessionRefreshRemoteDataSource
import com.chen.memorizewords.data.account.session.LocalAuthSessionCleaner
import com.chen.memorizewords.data.account.session.LocalAuthStateCleaner
import com.chen.memorizewords.data.account.session.LocalAuthStateProvider
import com.chen.memorizewords.data.account.session.SessionKickoutNotifierImpl
import com.chen.memorizewords.data.account.session.SessionLocalDataSource
import com.chen.memorizewords.data.account.session.SessionManager
import com.chen.memorizewords.data.account.session.SessionRefreshDataSource
import com.chen.memorizewords.data.account.session.TokenLocalDataSource
import com.chen.memorizewords.data.account.session.UnauthorizedSessionHandler
import com.chen.memorizewords.data.account.time.SystemClockProvider
import com.chen.memorizewords.core.network.remote.RemoteResultAdapter
import com.chen.memorizewords.core.network.remote.UnauthorizedNetworkHandler
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.account.auth.LocalAccountStore
import com.chen.memorizewords.domain.account.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.account.auth.TokenProvider
import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.account.repository.UserScopedDataCleaner
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import com.chen.memorizewords.domain.account.repository.user.UserRepository
import com.chen.memorizewords.domain.account.time.ClockProvider
import com.tencent.mmkv.MMKV
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.content.Context
import javax.inject.Qualifier
import javax.inject.Provider
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class DataAccountRepositoryModule {
    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    abstract fun bindLocalAccountRepository(impl: LocalAccountRepositoryImpl): LocalAccountRepository

    @Binds
    abstract fun bindAccountSessionRepository(impl: AccountSessionRepositoryImpl): AccountSessionRepository

    @Binds
    abstract fun bindUserScopedDataCleaner(impl: UserScopedDataCleanerImpl): UserScopedDataCleaner

    @Binds
    abstract fun bindClockProvider(impl: SystemClockProvider): ClockProvider
}

@Module
@InstallIn(SingletonComponent::class)
object DataAccountModule {
    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthRemoteDataSource(
        authRequest: com.chen.memorizewords.data.account.remoteapi.api.auth.AuthRequest,
        remoteResultAdapter: RemoteResultAdapter
    ): AuthRemoteDataSource {
        return RemoteAuthDataSourceImpl(authRequest, remoteResultAdapter)
    }

    @Provides
    @Singleton
    fun provideAuthLocalDataSource(mmkv: MMKV): AuthLocalDataSource {
        return AuthLocalDataSourceImpl(mmkv)
    }

    @Provides
    @Singleton
    fun provideAvatarLocalDataSource(impl: AvatarLocalDataSourceImpl): AvatarLocalDataSource {
        return impl
    }

    @Provides
    @Singleton
    fun provideLocalAccountStore(authLocalDataSource: AuthLocalDataSource): LocalAccountStore {
        return authLocalDataSource
    }

    @Provides
    @Singleton
    @AccountSessionStorage
    fun provideAccountSessionMMKV(@ApplicationContext context: Context): MMKV {
        MMKV.initialize(context.applicationContext)
        return MMKV.mmkvWithID("account_session", MMKV.SINGLE_PROCESS_MODE)
    }

    @Provides
    @Singleton
    fun provideSessionLocalDataSource(@AccountSessionStorage mmkv: MMKV): SessionLocalDataSource {
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
    ): com.chen.memorizewords.domain.account.auth.UnauthorizedSessionHandler {
        return UnauthorizedSessionHandler(localAuthStateCleaner)
    }

    @Provides
    @Singleton
    fun provideUnauthorizedNetworkHandler(
        localAuthStateCleaner: LocalAuthStateCleaner
    ): UnauthorizedNetworkHandler {
        return UnauthorizedSessionHandler(localAuthStateCleaner)
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AccountSessionStorage
