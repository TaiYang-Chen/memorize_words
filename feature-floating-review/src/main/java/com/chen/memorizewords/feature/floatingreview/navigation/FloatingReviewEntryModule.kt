package com.chen.memorizewords.feature.floatingreview.navigation

import android.content.Context
import android.content.Intent
import com.chen.memorizewords.feature.floatingreview.FloatingReviewActivity
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.core.navigation.FloatingWordDestination
import com.chen.memorizewords.core.navigation.FloatingWordEntryExtras
import com.chen.memorizewords.core.navigation.CharacterSelectionMode
import com.chen.memorizewords.core.navigation.FloatingWordReturnDestination
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeServiceGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FloatingReviewEntryModule {

    @Binds
    @Singleton
    abstract fun bindFloatingWordEntry(impl: DefaultFloatingWordEntry): FloatingWordEntry

    @Binds
    @Singleton
    abstract fun bindFloatingRuntimeServiceGateway(
        impl: FloatingRuntimeServiceGatewayImpl
    ): FloatingRuntimeServiceGateway
}

@Singleton
class DefaultFloatingWordEntry @Inject constructor() : FloatingWordEntry {
    override fun createSettingsIntent(
        context: Context,
        destination: FloatingWordDestination,
        returnDestination: FloatingWordReturnDestination
    ): Intent {
        return Intent(context, FloatingReviewActivity::class.java).apply {
            putExtra(FloatingWordEntryExtras.EXTRA_DESTINATION, destination.name)
            putExtra(
                FloatingWordEntryExtras.EXTRA_CHARACTER_MODE,
                if (destination == FloatingWordDestination.CHARACTER_SELECTION) {
                    CharacterSelectionMode.ACTIVATE.name
                } else {
                    CharacterSelectionMode.MANAGE.name
                }
            )
            putExtra(FloatingWordEntryExtras.EXTRA_RETURN_DESTINATION, returnDestination.name)
        }
    }
}
