package com.chen.memorizewords.core.session.logout

import com.chen.memorizewords.domain.account.model.LogoutOutcome
import com.chen.memorizewords.domain.account.usecase.user.LogoutUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

fun interface SessionLogoutExecutor {
    suspend fun execute(force: Boolean): Result<LogoutOutcome>
}

@Singleton
class LogoutUseCaseSessionLogoutExecutor @Inject constructor(
    private val logoutUseCase: LogoutUseCase
) : SessionLogoutExecutor {
    override suspend fun execute(force: Boolean): Result<LogoutOutcome> {
        return logoutUseCase(force)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionLogoutExecutorModule {

    @Binds
    @Singleton
    abstract fun bindSessionLogoutExecutor(
        implementation: LogoutUseCaseSessionLogoutExecutor
    ): SessionLogoutExecutor
}
