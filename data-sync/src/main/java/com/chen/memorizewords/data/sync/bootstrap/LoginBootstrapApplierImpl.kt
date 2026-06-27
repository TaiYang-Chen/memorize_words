package com.chen.memorizewords.data.sync.bootstrap

import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.domain.account.repository.LoginBootstrapApplier
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords
import com.chen.memorizewords.domain.study.repository.StudyDailyDurationSnapshot
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import com.chen.memorizewords.domain.sync.model.HomeStartupSnapshot
import com.chen.memorizewords.domain.sync.model.LoginBootstrap
import com.chen.memorizewords.domain.sync.model.LoginBootstrapDailyStudyDuration
import com.chen.memorizewords.domain.sync.model.LoginBootstrapStudyRecord
import com.chen.memorizewords.domain.sync.repository.HomeStartupSnapshotRepository
import com.chen.memorizewords.domain.wordbook.repository.CurrentWordBookLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.WordBookSnapshotLocalStatePort
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.repository.onboarding.OnboardingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginBootstrapApplierImpl @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val studyPlanLocalStatePort: StudyPlanLocalStatePort,
    private val currentWordBookLocalStatePort: CurrentWordBookLocalStatePort,
    private val wordBookSnapshotLocalStatePort: WordBookSnapshotLocalStatePort,
    private val studySnapshotLocalStatePort: StudySnapshotLocalStatePort,
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val localAccountRepository: LocalAccountRepository,
    private val homeStartupSnapshotRepository: HomeStartupSnapshotRepository,
    private val checkInBusinessCalendar: CheckInBusinessCalendar
) : LoginBootstrapApplier {

    override suspend fun apply(bootstrap: LoginBootstrap?) {
        if (bootstrap == null || bootstrap.version != SUPPORTED_VERSION) {
            homeStartupSnapshotRepository.clearSnapshot()
            return
        }

        onboardingRepository.replaceCurrentSnapshot(bootstrap.onboarding)
        studyPlanLocalStatePort.overwriteFromRemote(bootstrap.studyPlan)
        currentWordBookLocalStatePort.upsertBookAndSelectionFromRemote(bootstrap.currentWordBook)

        bootstrap.currentWordBookProgress?.let { progress ->
            wordBookSnapshotLocalStatePort.upsertProgressFromRemote(listOf(progress))
        }

        if (bootstrap.todayStudyRecords.isNotEmpty()) {
            studySnapshotLocalStatePort.upsertStudyRecordsFromRemote(
                bootstrap.todayStudyRecords.map { it.toDomain() }
            )
        }

        bootstrap.todayStudyDuration?.let { duration ->
            studySnapshotLocalStatePort.upsertDailyDurationsFromRemote(listOf(duration.toSnapshot()))
        }

        bootstrap.checkInStatus?.let { status ->
            checkInConfigDataSource.saveCachedMakeupCardBalance(status.makeupCardBalance)
            checkInConfigDataSource.saveLastCheckInSyncAt(
                bootstrap.serverTime.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        }

        saveHomeStartupSnapshot(bootstrap)
    }

    private suspend fun saveHomeStartupSnapshot(bootstrap: LoginBootstrap) {
        if (
            bootstrap.currentWordBook == null &&
            bootstrap.onboarding?.phase == OnboardingPhase.NEEDS_WORD_BOOK
        ) {
            homeStartupSnapshotRepository.clearSnapshot()
            return
        }
        val userId = localAccountRepository.getCurrentUserId() ?: return
        val businessDate = bootstrap.businessDate
            ?.takeIf { it.isNotBlank() }
            ?: bootstrap.todayStats?.date?.takeIf { it.isNotBlank() }
            ?: bootstrap.todayStudyDuration?.date?.takeIf { it.isNotBlank() }
            ?: checkInBusinessCalendar.currentBusinessDate()
        val todayRecords = bootstrap.todayStudyRecords.filter { it.date == businessDate }
        val todayStats = bootstrap.todayStats?.takeIf { it.date == businessDate }
        val todayDuration = bootstrap.todayStudyDuration?.takeIf { it.date == businessDate }

        homeStartupSnapshotRepository.saveSnapshot(
            HomeStartupSnapshot(
                userId = userId,
                businessDate = businessDate,
                serverTime = bootstrap.serverTime,
                capturedAtMs = System.currentTimeMillis(),
                currentWordBook = bootstrap.currentWordBook,
                currentWordBookProgress = bootstrap.currentWordBookProgress,
                studyPlan = bootstrap.studyPlan,
                todayNewWordCount = todayStats?.newWordCount
                    ?: todayRecords.count { it.isNewWord },
                todayReviewWordCount = todayStats?.reviewWordCount
                    ?: todayRecords.count { !it.isNewWord },
                todayStudyDurationMs = todayDuration?.totalDurationMs
                    ?: todayStats?.studyDurationMs
                    ?: 0L,
                continuousCheckInDays = todayStats?.continuousCheckInDays
                    ?: bootstrap.checkInStatus?.continuousCheckInDays
                    ?: 0,
                totalStudyDayCount = todayStats?.totalStudyDayCount ?: 0
            )
        )
    }

    private companion object {
        const val SUPPORTED_VERSION = 1
    }
}

private fun LoginBootstrapStudyRecord.toDomain(): DailyStudyRecords {
    return DailyStudyRecords(
        date = date,
        wordId = wordId,
        word = word,
        definition = definition,
        isNewWord = isNewWord
    )
}

private fun LoginBootstrapDailyStudyDuration.toSnapshot(): StudyDailyDurationSnapshot {
    return StudyDailyDurationSnapshot(
        date = date,
        totalDurationMs = totalDurationMs,
        updatedAt = updatedAt,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )
}
