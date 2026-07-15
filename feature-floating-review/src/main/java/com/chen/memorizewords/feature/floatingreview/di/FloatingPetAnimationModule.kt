package com.chen.memorizewords.feature.floatingreview.di

import android.content.Context
import com.chen.memorizewords.core.sprite.BundledSpritePackSource
import com.chen.memorizewords.core.sprite.CompositeSpritePackRepository
import com.chen.memorizewords.core.sprite.DefaultSpriteSessionFactory
import com.chen.memorizewords.core.sprite.SpritePackRepository
import com.chen.memorizewords.core.sprite.SpriteSessionFactory
import com.chen.memorizewords.feature.floatingreview.ui.floating.pet.FloatingPetActionPolicy
import com.chen.memorizewords.feature.floatingreview.ui.floating.pet.FloatingPetController
import com.chen.memorizewords.feature.floatingreview.ui.floating.pet.FloatingPetPackContractValidator
import com.chen.memorizewords.feature.floatingreview.ui.floating.pet.ManifestFloatingPetActionPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FloatingPetAnimationModule {
    @Provides
    @Singleton
    fun provideSpritePackRepository(
        @ApplicationContext context: Context,
        contractValidator: FloatingPetPackContractValidator
    ): SpritePackRepository = CompositeSpritePackRepository(
        sources = listOf(BundledSpritePackSource(context.assets)),
        fallbackPackId = FloatingPetController.DEFAULT_PACK_ID,
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
}
