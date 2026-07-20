package com.chen.memorizewords.feature.floatingreview.di

import com.chen.memorizewords.core.sprite.CompositeSpritePackRepository
import com.chen.memorizewords.core.sprite.DefaultSpriteSessionFactory
import com.chen.memorizewords.core.sprite.SpritePackRepository
import com.chen.memorizewords.core.sprite.SpritePackContractValidator
import com.chen.memorizewords.core.sprite.SpritePackSource
import com.chen.memorizewords.core.sprite.DownloadedSpriteSource
import com.chen.memorizewords.core.sprite.SpriteSessionFactory
import com.chen.memorizewords.feature.floatingreview.ui.floating.pet.FloatingPetActionPolicy
import com.chen.memorizewords.feature.floatingreview.ui.floating.pet.FloatingPetController
import com.chen.memorizewords.feature.floatingreview.ui.floating.pet.FloatingPetPackContractValidator
import com.chen.memorizewords.feature.floatingreview.ui.floating.pet.ManifestFloatingPetActionPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FloatingPetAnimationModule {
    @Provides
    @Singleton
    fun provideSpritePackRepository(
        @DownloadedSpriteSource downloadedSource: SpritePackSource,
        contractValidator: FloatingPetPackContractValidator
    ): SpritePackRepository = CompositeSpritePackRepository(
        sources = listOf(downloadedSource),
        validateCandidate = contractValidator::validate
    )

    @Provides
    @Singleton
    fun provideSpriteSessionFactory(): SpriteSessionFactory = DefaultSpriteSessionFactory(
        replacementFrameByteReserve = FloatingPetPackContractValidator.MAX_FLOATING_PET_FRAME_BYTES
    )

    @Provides
    @Singleton
    fun provideFloatingPetActionPolicy(): FloatingPetActionPolicy =
        ManifestFloatingPetActionPolicy()

    @Provides
    @Singleton
    fun provideSpritePackContractValidator(
        validator: FloatingPetPackContractValidator
    ): SpritePackContractValidator = validator
}
