package com.chen.memorizewords.data.session

import android.content.Context
import androidx.work.WorkManager
import androidx.room.withTransaction
import com.chen.memorizewords.data.local.mmkv.checkin.CheckInConfigDataSource
import com.chen.memorizewords.data.local.mmkv.download.UpdateDownloadStore
import com.chen.memorizewords.data.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.data.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.floating.FloatingWordDisplayRecordDao
import com.chen.memorizewords.data.local.room.model.practice.daily.DailyPracticeDurationDao
import com.chen.memorizewords.data.local.room.model.practice.session.PracticeSessionRecordDao
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.local.room.model.study.daily.WordStudyRecordsDao
import com.chen.memorizewords.data.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.repository.floating.FloatingWordSettingsRepositoryImpl
import com.chen.memorizewords.data.repository.practice.PracticeSettingsRepositoryImpl
import com.chen.memorizewords.data.repository.download.WordBookDownloadWorkConstants
import com.chen.memorizewords.data.repository.wordbook.update.CurrentWordBookUpdateWorkConstants
import com.chen.memorizewords.data.repository.sync.SyncWorkConstants
import com.tencent.mmkv.MMKV
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataCleaner @Inject constructor(
    @ApplicationContext context: Context,
    private val appDatabase: AppDatabase,
    private val wordLearningStateDao: WordLearningStateDao,
    private val wordBookProgressDao: WordBookProgressDao,
    private val wordStudyRecordsDao: WordStudyRecordsDao,
    private val dailyStudyDurationDao: DailyStudyDurationDao,
    private val wordFavoritesDao: WordFavoritesDao,
    private val checkInRecordDao: CheckInRecordDao,
    private val dailyPracticeDurationDao: DailyPracticeDurationDao,
    private val practiceSessionRecordDao: PracticeSessionRecordDao,
    private val floatingWordDisplayRecordDao: FloatingWordDisplayRecordDao,
    private val wordBookDao: WordBookDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val studyPlanDataSource: StudyPlanDataSource,
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val practiceSettingsRepository: PracticeSettingsRepositoryImpl,
    private val floatingWordSettingsRepository: FloatingWordSettingsRepositoryImpl,
    private val wordBookSyncStateStore: WordBookSyncStateStore,
    private val updateDownloadStore: UpdateDownloadStore,
    private val mmkv: MMKV
) {
    private val appContext = context.applicationContext

    suspend fun clearUserLearningData() {
        cancelUserScopedWork()
        appDatabase.withTransaction {
            wordLearningStateDao.deleteAll()
            wordBookProgressDao.deleteAll()
            wordStudyRecordsDao.deleteAll()
            dailyStudyDurationDao.deleteAll()
            wordFavoritesDao.deleteAll()
            checkInRecordDao.deleteAll()
            dailyPracticeDurationDao.deleteAll()
            practiceSessionRecordDao.deleteAll()
            floatingWordDisplayRecordDao.deleteAll()
            wordBookDao.deleteAll()
            syncOutboxDao.deleteAll()
        }
        studyPlanDataSource.clearStudyPlan()
        checkInConfigDataSource.clearUserScopedState()
        practiceSettingsRepository.clearLocalState()
        floatingWordSettingsRepository.clearLocalState()
        wordBookSyncStateStore.clearLocalState()
        updateDownloadStore.clear()
        clearUserScopedMmkvState()
    }

    private fun cancelUserScopedWork() {
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        listOf(
            SyncWorkConstants.TAG_SYNC_OUTBOX_DRAIN,
            SyncWorkConstants.TAG_POST_LOGIN_BOOTSTRAP,
            SyncWorkConstants.TAG_ADD_MY_WORD_BOOK,
            SyncWorkConstants.TAG_WORD_STATE_SYNC,
            SyncWorkConstants.TAG_WORD_BOOK_PROGRESS_SYNC,
            SyncWorkConstants.TAG_STUDY_PLAN_SYNC,
            SyncWorkConstants.TAG_FAVORITE_SYNC,
            WordBookDownloadWorkConstants.TAG_DOWNLOAD,
            WordBookDownloadWorkConstants.BOOTSTRAP_WORK_NAME,
            CurrentWordBookUpdateWorkConstants.TAG_UPDATE
        ).forEach(workManager::cancelAllWorkByTag)
        listOf(
            SyncWorkConstants.WORK_SYNC_OUTBOX_DRAIN,
            SyncWorkConstants.WORK_POST_LOGIN_BOOTSTRAP,
            WordBookDownloadWorkConstants.BOOTSTRAP_WORK_NAME
        ).forEach(workManager::cancelUniqueWork)
    }

    private fun clearUserScopedMmkvState() {
        val keys = mmkv.allKeys() ?: return
        keys.filter(::isUserScopedMmkvKey)
            .forEach { mmkv.removeValueForKey(it) }
    }
}

internal fun isUserScopedMmkvKey(key: String): Boolean {
    return key.startsWith("Session_") ||
        key.startsWith("practice_") ||
        key.startsWith("floating_word_") ||
        key.startsWith("checkin_") ||
        key.startsWith("wordbook_")
}
