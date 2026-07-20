package com.chen.memorizewords.session

import android.content.Context
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.domain.account.UserScopedDataResetContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloatingWordUserScopedDataResetContributor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val floatingWordEntry: FloatingWordEntry
) : UserScopedDataResetContributor {

    override val resetPriority: Int = PRIORITY_STOP_BEFORE_DATA_CLEAR

    override suspend fun clearUserScopedData() {
        floatingWordEntry.dispatchServiceAction(
            context = context,
            action = FloatingWordActions.ACTION_STOP
        )
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
