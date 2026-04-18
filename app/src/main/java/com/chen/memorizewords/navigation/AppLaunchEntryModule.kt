package com.chen.memorizewords.navigation

import android.content.Context
import android.content.Intent
import com.chen.memorizewords.SplashActivity
import com.chen.memorizewords.core.navigation.AppLaunchEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppLaunchEntryModule {

    @Binds
    @Singleton
    abstract fun bindAppLaunchEntry(impl: DefaultAppLaunchEntry): AppLaunchEntry
}

@Singleton
class DefaultAppLaunchEntry @Inject constructor() : AppLaunchEntry {
    override fun createLaunchIntent(context: Context): Intent {
        return Intent(context, SplashActivity::class.java)
    }
}
