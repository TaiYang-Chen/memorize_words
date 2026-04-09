package com.chen.memorizewords.startup

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppStartupTaskModule {

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSessionKickoutStartupTask(
        impl: SessionKickoutStartupTask
    ): ApplicationStartupTask

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindPostLaunchStartupTask(
        impl: PostLaunchStartupTask
    ): ApplicationStartupTask

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindForegroundWordBookStartupTask(
        impl: ForegroundWordBookStartupTask
    ): ApplicationStartupTask

    companion object {
        @Provides
        @Singleton
        fun provideAppStartupTracer(): AppStartupTracer = AppStartupTracer()
    }
}
