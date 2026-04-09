package com.chen.memorizewords.data.di

import com.chen.memorizewords.data.repository.LearningProgressRepositoryImpl
import com.chen.memorizewords.data.repository.RemoteWordBookRepositoryImpl
import com.chen.memorizewords.data.repository.WordBookRepositoryImpl
import com.chen.memorizewords.data.repository.WordBookUpdateRepositoryImpl
import com.chen.memorizewords.data.repository.WordLearningRepositoryImpl
import com.chen.memorizewords.data.repository.WordRepositoryImpl
import com.chen.memorizewords.data.repository.download.DownloadRepositoryImpl
import com.chen.memorizewords.data.repository.feedback.FeedbackRepositoryImpl
import com.chen.memorizewords.data.repository.floating.FloatingWordDisplayRecordRepositoryImpl
import com.chen.memorizewords.data.repository.floating.FloatingWordSettingsRepositoryImpl
import com.chen.memorizewords.data.repository.practice.PracticeRecordRepositoryImpl
import com.chen.memorizewords.data.repository.practice.ExamPracticeRepositoryImpl
import com.chen.memorizewords.data.repository.practice.PracticeSessionRecordRepositoryImpl
import com.chen.memorizewords.data.repository.practice.PracticeSettingsRepositoryImpl
import com.chen.memorizewords.data.repository.record.LearningRecordRepositoryImpl
import com.chen.memorizewords.data.repository.study.FavoritesRepositoryImpl
import com.chen.memorizewords.data.repository.study.StudyPlanRepositoryImpl
import com.chen.memorizewords.data.repository.sync.SyncRepositoryImpl
import com.chen.memorizewords.data.repository.wordbook.update.WordBookUpdateCoordinatorImpl
import com.chen.memorizewords.data.repository.user.AuthRepositoryImpl
import com.chen.memorizewords.data.repository.user.UserRepositoryImpl
import com.chen.memorizewords.domain.repository.feedback.FeedbackRepository
import com.chen.memorizewords.domain.repository.LearningProgressRepository
import com.chen.memorizewords.domain.repository.StudyPlanRepository
import com.chen.memorizewords.domain.repository.WordBookRepository
import com.chen.memorizewords.domain.repository.WordBookUpdateRepository
import com.chen.memorizewords.domain.repository.WordLearningRepository
import com.chen.memorizewords.domain.repository.download.DownloadRepository
import com.chen.memorizewords.domain.repository.floating.FloatingWordDisplayRecordRepository
import com.chen.memorizewords.domain.repository.floating.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.repository.practice.PracticeRecordRepository
import com.chen.memorizewords.domain.repository.practice.ExamPracticeRepository
import com.chen.memorizewords.domain.repository.practice.PracticeSessionRecordRepository
import com.chen.memorizewords.domain.repository.practice.PracticeSettingsRepository
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import com.chen.memorizewords.domain.repository.shop.RemoteWordBookRepository
import com.chen.memorizewords.domain.repository.word.FavoritesRepository
import com.chen.memorizewords.domain.repository.word.WordRepository
import com.chen.memorizewords.domain.repository.sync.SyncRepository
import com.chen.memorizewords.domain.repository.user.AuthRepository
import com.chen.memorizewords.domain.repository.user.UserRepository
import com.chen.memorizewords.domain.service.wordbook.WordBookUpdateCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindWordLearningRepository(impl: WordLearningRepositoryImpl): WordLearningRepository

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    abstract fun bindStudyPlanRepository(impl: StudyPlanRepositoryImpl): StudyPlanRepository

    @Binds
    abstract fun bindWordBookRepository(impl: WordBookRepositoryImpl): WordBookRepository

    @Binds
    abstract fun bindWordBookUpdateRepository(
        impl: WordBookUpdateRepositoryImpl
    ): WordBookUpdateRepository

    @Binds
    abstract fun bindLearningProgressRepository(impl: LearningProgressRepositoryImpl): LearningProgressRepository

    @Binds
    abstract fun bindWordRepository(impl: WordRepositoryImpl): WordRepository

    @Binds
    abstract fun bindShopWordBookRepository(impl: RemoteWordBookRepositoryImpl): RemoteWordBookRepository

    @Binds
    abstract fun bindLearningRecordRepository(impl: LearningRecordRepositoryImpl): LearningRecordRepository

    @Binds
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    abstract fun bindFeedbackRepository(impl: FeedbackRepositoryImpl): FeedbackRepository

    @Binds
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    abstract fun bindPracticeSettingsRepository(
        impl: PracticeSettingsRepositoryImpl
    ): PracticeSettingsRepository

    @Binds
    abstract fun bindPracticeRecordRepository(
        impl: PracticeRecordRepositoryImpl
    ): PracticeRecordRepository

    @Binds
    abstract fun bindExamPracticeRepository(
        impl: ExamPracticeRepositoryImpl
    ): ExamPracticeRepository

    @Binds
    abstract fun bindPracticeSessionRecordRepository(
        impl: PracticeSessionRecordRepositoryImpl
    ): PracticeSessionRecordRepository

    @Binds
    abstract fun bindFloatingWordSettingsRepository(
        impl: FloatingWordSettingsRepositoryImpl
    ): FloatingWordSettingsRepository

    @Binds
    abstract fun bindFloatingWordDisplayRecordRepository(
        impl: FloatingWordDisplayRecordRepositoryImpl
    ): FloatingWordDisplayRecordRepository

    @Binds
    abstract fun bindWordBookUpdateCoordinator(
        impl: WordBookUpdateCoordinatorImpl
    ): WordBookUpdateCoordinator
}
