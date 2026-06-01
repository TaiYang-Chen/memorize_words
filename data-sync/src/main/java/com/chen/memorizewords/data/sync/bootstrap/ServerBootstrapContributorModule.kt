package com.chen.memorizewords.data.sync.bootstrap

import com.chen.memorizewords.domain.sync.ServerBootstrapContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class ServerBootstrapContributorModule {

    @Multibinds
    abstract fun bindServerBootstrapContributors(): Set<ServerBootstrapContributor>

    @Binds
    @IntoSet
    abstract fun bindPrimaryServerBootstrapContributor(
        impl: PrimaryServerBootstrapContributor
    ): ServerBootstrapContributor
}
