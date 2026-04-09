package com.chen.memorizewords.feature.home.navigation

import android.content.Context
import android.content.Intent
import com.chen.memorizewords.feature.home.HomeActivity
import com.chen.memorizewords.core.navigation.HomeEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeEntryModule {

    @Binds
    @Singleton
    abstract fun bindHomeEntry(impl: DefaultHomeEntry): HomeEntry
}

@Singleton
class DefaultHomeEntry @Inject constructor() : HomeEntry {
    override fun createHomeIntent(context: Context): Intent {
        return Intent(context, HomeActivity::class.java)
    }
}
