package com.chen.memorizewords.feature.floatingreview.navigation

import android.content.Context
import android.content.Intent
import com.chen.memorizewords.feature.floatingreview.FloatingReviewActivity
import com.chen.memorizewords.feature.floatingreview.ui.floating.FloatingWordService
import com.chen.memorizewords.core.navigation.FloatingWordEntry
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
}

@Singleton
class DefaultFloatingWordEntry @Inject constructor() : FloatingWordEntry {
    override fun createServiceIntent(context: Context, action: String): Intent {
        return Intent(context, FloatingWordService::class.java).apply {
            this.action = action
        }
    }

    override fun createSettingsIntent(context: Context): Intent {
        return Intent(context, FloatingReviewActivity::class.java)
    }
}
