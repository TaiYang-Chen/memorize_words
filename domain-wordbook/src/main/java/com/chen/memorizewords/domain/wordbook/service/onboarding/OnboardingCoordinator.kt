package com.chen.memorizewords.domain.wordbook.service.onboarding
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingError
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import com.chen.memorizewords.domain.wordbook.service.WordBookShopFacade
import com.chen.memorizewords.domain.wordbook.usecase.SaveStudyPlanUseCase
import com.chen.memorizewords.domain.wordbook.usecase.SetCurrentWordBookUseCase
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OnboardingCoordinator @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val wordBookShopFacade: WordBookShopFacade,
    private val setCurrentWordBookUseCase: SetCurrentWordBookUseCase,
    private val saveStudyPlanUseCase: SaveStudyPlanUseCase
) {
    private val mutationMutex = Mutex()

    suspend fun completeOnboarding(
        selectedBook: WordBook,
        studyPlan: StudyPlan
    ): Result<OnboardingSnapshot> {
        if (mutationMutex.isLocked) {
            return Result.failure(OnboardingOperationException(OnboardingError.SyncDeferred))
        }
        return runCatching {
            mutationMutex.withLock {
                saveStudyPlanUseCase(studyPlan)
                // Onboarding completion can leave the screen immediately, so avoid WorkManager's
                // foreground service path here to prevent background stop-service exceptions.
                wordBookShopFacade.downloadBook(selectedBook, runInForeground = false)
                setCurrentWordBookUseCase(selectedBook.id)
                onboardingRepository.completeOnboarding(selectedBook.id)
            }
        }.mapError()
    }
}

class OnboardingOperationException(
    val onboardingError: OnboardingError
) : IllegalStateException()

private fun Result<OnboardingSnapshot>.mapError(): Result<OnboardingSnapshot> {
    return exceptionOrNull()?.let { throwable ->
        val error = throwable.toOnboardingError()
        Result.failure(OnboardingOperationException(error))
    } ?: this
}

private fun Throwable.toOnboardingError(): OnboardingError {
    return when (this) {
        is OnboardingOperationException -> onboardingError
        is IllegalStateException,
        is NoSuchElementException -> OnboardingError.RequiredDataUnavailable
        else -> OnboardingError.LocalPersistenceFailed
    }
}
