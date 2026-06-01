package com.chen.memorizewords.domain.practice.service
import com.chen.memorizewords.domain.practice.PracticeAvailability
import com.chen.memorizewords.domain.practice.PracticeDailyDurationStats
import com.chen.memorizewords.domain.practice.PracticeRecordRepository
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSessionRecordRepository
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.domain.practice.PracticeSettingsRepository
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class PracticeFacade @Inject constructor(
    private val practiceWordProvider: PracticeWordProvider,
    private val practiceSettingsRepository: PracticeSettingsRepository,
    private val practiceRecordRepository: PracticeRecordRepository,
    private val practiceSessionRecordRepository: PracticeSessionRecordRepository
) {
    fun observeSettings(): Flow<PracticeSettings> = practiceSettingsRepository.observeSettings()

    suspend fun getSettings(): PracticeSettings = practiceSettingsRepository.getSettings()

    suspend fun saveSettings(settings: PracticeSettings) {
        practiceSettingsRepository.saveSettings(settings)
    }

    suspend fun loadWords(
        selectedIds: LongArray?,
        randomCount: Int,
        defaultLimit: Int
    ): List<Word> = practiceWordProvider.loadWords(selectedIds, randomCount, defaultLimit)

    suspend fun getPracticeAvailability(): PracticeAvailability =
        practiceWordProvider.getPracticeAvailability()

    suspend fun resolveBookId(): Long? = practiceWordProvider.resolveBookId()

    suspend fun loadReviewWordsForPicker(): List<Word> =
        practiceWordProvider.loadReviewWordsForPicker()

    suspend fun addPracticeDuration(durationMs: Long) {
        practiceRecordRepository.addPracticeDuration(durationMs)
    }

    fun getTodayPracticeDurationMs(): Flow<Long> = practiceRecordRepository.getTodayPracticeDurationMs()

    fun getPracticeTotalDurationMs(): Flow<Long> = practiceRecordRepository.getPracticeTotalDurationMs()

    fun getContinuousPracticeDays(): Flow<Int> = practiceRecordRepository.getContinuousPracticeDays()

    fun getRecentPracticeDurationStats(dayCount: Int): Flow<List<PracticeDailyDurationStats>> =
        practiceRecordRepository.getRecentPracticeDurationStats(dayCount)

    fun getRecentSessionRecords(dayCount: Int): Flow<List<PracticeSessionRecord>> =
        practiceSessionRecordRepository.getRecentSessionRecords(dayCount)

    suspend fun getSessionRecord(recordId: Long): PracticeSessionRecord? =
        practiceSessionRecordRepository.getSessionRecord(recordId)

    suspend fun saveSessionRecord(record: PracticeSessionRecord) {
        practiceSessionRecordRepository.saveSessionRecord(record)
    }
}
