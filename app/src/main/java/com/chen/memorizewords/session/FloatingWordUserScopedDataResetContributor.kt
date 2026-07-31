package com.chen.memorizewords.session

import com.chen.memorizewords.domain.account.UserScopedDataResetContributor
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloatingWordUserScopedDataResetContributor @Inject constructor(
    private val floatingRuntimeController: FloatingRuntimeController
) : UserScopedDataResetContributor {

    override val resetPriority: Int = PRIORITY_STOP_BEFORE_DATA_CLEAR

    override suspend fun clearUserScopedData() {
        floatingRuntimeController.requestStop()
    }

    private companion object {
        const val PRIORITY_STOP_BEFORE_DATA_CLEAR = -10_000
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FloatingWordUserScopedDataResetModule {
    @Binds
    @IntoSet
    abstract fun bindFloatingWordUserScopedDataResetContributor(
        impl: FloatingWordUserScopedDataResetContributor
    ): UserScopedDataResetContributor
}
