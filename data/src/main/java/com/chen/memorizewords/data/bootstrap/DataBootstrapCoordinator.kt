package com.chen.memorizewords.data.bootstrap

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chen.memorizewords.data.local.mmkv.checkin.CheckInConfigDataSource
import com.chen.memorizewords.data.local.mmkv.plan.StudyPlanDataSource
import com.chen.memorizewords.data.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.floating.FloatingWordDisplayRecordDao
import com.chen.memorizewords.data.local.room.model.floating.FloatingWordDisplayRecordEntity
import com.chen.memorizewords.data.local.room.model.wordbook.current.CurrentWordBookSelectionEntity
import com.chen.memorizewords.data.local.room.model.practice.daily.DailyPracticeDurationDao
import com.chen.memorizewords.data.local.room.model.practice.daily.DailyPracticeDurationEntity
import com.chen.memorizewords.data.local.room.model.practice.session.PracticeSessionRecordDao
import com.chen.memorizewords.data.local.room.model.practice.session.PracticeSessionRecordEntity
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.local.room.model.study.daily.DailyStudyDurationEntity
import com.chen.memorizewords.data.local.room.model.study.daily.WordStudyRecordsDao
import com.chen.memorizewords.data.local.room.model.study.daily.WordStudyRecordsEntity
import com.chen.memorizewords.data.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.local.room.model.study.favorites.WordFavoriteEntity
import com.chen.memorizewords.data.local.room.model.study.favorites.parseFavoriteAddedAt
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookEntity
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.toEntity
import com.chen.memorizewords.data.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.data.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.data.repository.floating.FloatingWordSettingsRepositoryImpl
import com.chen.memorizewords.data.repository.floating.toDisplayWordEntities
import com.chen.memorizewords.data.repository.practice.PracticeSettingsRepositoryImpl
import com.chen.memorizewords.data.repository.practice.toWordEntities
import com.chen.memorizewords.data.repository.practice.parsePracticeEntryType
import com.chen.memorizewords.data.repository.practice.parsePracticeMode
import com.chen.memorizewords.data.repository.download.WordBookDownloadWorkConstants
import com.chen.memorizewords.data.repository.download.WordBookDownloadWorker
import com.chen.memorizewords.data.repository.sync.CheckInRecordSyncPayload
import com.chen.memorizewords.data.repository.sync.DailyStudyDurationSyncPayload
import com.chen.memorizewords.data.repository.sync.FavoriteSyncPayload
import com.chen.memorizewords.data.repository.sync.FloatingDisplayRecordSyncPayload
import com.chen.memorizewords.data.repository.sync.FloatingSettingsSyncPayload
import com.chen.memorizewords.data.repository.sync.OnboardingStateSyncPayload
import com.chen.memorizewords.data.repository.sync.PracticeDurationSyncPayload
import com.chen.memorizewords.data.repository.sync.PracticeSettingsSyncPayload
import com.chen.memorizewords.data.repository.sync.PracticeSessionSyncPayload
import com.chen.memorizewords.data.repository.sync.StudyPlanSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncWorkConstants
import com.chen.memorizewords.data.repository.sync.WordBookProgressSyncPayload
import com.chen.memorizewords.data.repository.sync.WordBookSelectionSyncPayload
import com.chen.memorizewords.data.repository.sync.WordStateDeleteByBookSyncPayload
import com.chen.memorizewords.data.repository.sync.WordStateUpsertSyncPayload
import com.chen.memorizewords.data.repository.wordbook.persistWordBookPage
import com.chen.memorizewords.domain.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.model.study.record.CheckInType
import com.chen.memorizewords.domain.repository.onboarding.OnboardingRepository
import com.chen.memorizewords.network.api.datasync.CheckInRecordDto
import com.chen.memorizewords.network.api.datasync.DailyStudyDurationDto
import com.chen.memorizewords.network.api.datasync.FavoriteDto
import com.chen.memorizewords.network.api.datasync.StudyRecordDto
import com.chen.memorizewords.network.api.datasync.WordBookProgressDto
import com.chen.memorizewords.network.api.learningsync.FloatingDisplayRecordDto
import com.chen.memorizewords.network.api.learningsync.PracticeDurationDto
import com.chen.memorizewords.network.api.learningsync.PracticeSessionDto
import com.chen.memorizewords.network.dto.wordbook.WordBookDto
import com.chen.memorizewords.network.dto.wordstate.WordStateDto
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.min
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class DataBootstrapCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val appDatabase: AppDatabase,
    private val remoteUserSyncDataSourceProvider: Provider<RemoteUserSyncDataSource>,
    private val remoteLearningSyncDataSourceProvider: Provider<RemoteLearningSyncDataSource>,
    private val remoteWordBookDataSourceProvider: Provider<RemoteWordBookDataSource>,
    private val studyPlanDataSource: StudyPlanDataSource,
    private val wordBookDao: WordBookDao,
    private val wordLearningStateDao: WordLearningStateDao,
    private val wordBookProgressDao: WordBookProgressDao,
    private val wordStudyRecordsDao: WordStudyRecordsDao,
    private val dailyStudyDurationDao: DailyStudyDurationDao,
    private val checkInRecordDao: CheckInRecordDao,
    private val wordFavoritesDao: WordFavoritesDao,
    private val dailyPracticeDurationDao: DailyPracticeDurationDao,
    private val practiceSessionRecordDao: PracticeSessionRecordDao,
    private val floatingWordDisplayRecordDao: FloatingWordDisplayRecordDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val checkInConfigDataSource: CheckInConfigDataSource,
    private val practiceSettingsRepository: PracticeSettingsRepositoryImpl,
    private val floatingWordSettingsRepository: FloatingWordSettingsRepositoryImpl,
    private val onboardingRepository: OnboardingRepository,
    private val wordBookSyncStateStore: WordBookSyncStateStore,
    private val mmkv: MMKV,
    private val gson: Gson
) {

    private val appContext = context.applicationContext
    private val workManager: WorkManager by lazy(LazyThreadSafetyMode.NONE) {
        WorkManager.getInstance(appContext)
    }

    suspend fun bootstrapMyBooksAndEnqueueDownloads() {
        val remoteSync = remoteUserSyncDataSourceProvider.get()

        syncOnboardingFromServer(remoteSync)
        syncStudyPlanFromServer(remoteSync)

        val remoteBooks = remoteSync.getMyWordBooks().getOrThrow()
        wordBookSyncStateStore.updateRemoteVersions(remoteBooks.associate { it.id to it.contentVersion })
        appDatabase.withTransaction {
            alignMyWordBooks(remoteBooks)
        }
        syncWordBookProgress(remoteSync)
        syncStudyHistory(remoteSync)
        syncFavorites(remoteSync)
        syncCheckIn(remoteSync)

        val books = wordBookDao.getAllWordBooks()
        val pausedBookIds = cleanAndLoadPausedBookIds(books.map { it.id }.toSet())
        for (book in books) {
            if (pausedBookIds.contains(book.id)) continue
            enqueueBookPipeline(book)
        }
        Log.i(TAG, "bootstrap books completed count=${books.size}")
    }

    suspend fun syncAfterLoginInOrder() {
        val remoteSync = remoteUserSyncDataSourceProvider.get()
        val remoteLearningSync = remoteLearningSyncDataSourceProvider.get()
        val remoteWordBook = remoteWordBookDataSourceProvider.get()

        syncOnboardingFromServer(remoteSync)
        syncStudyPlanFromServer(remoteSync)

        val remoteBooks = remoteSync.getMyWordBooks().getOrThrow()
        wordBookSyncStateStore.updateRemoteVersions(remoteBooks.associate { it.id to it.contentVersion })
        appDatabase.withTransaction {
            alignMyWordBooks(remoteBooks)
        }
        syncWordBookProgress(remoteSync)
        syncStudyHistory(remoteSync)
        syncFavorites(remoteSync)
        syncCheckIn(remoteSync)
        syncLearningData(remoteLearningSync)

        val books = wordBookDao.getAllWordBooks()
        val remoteVersionById = remoteBooks.associate { it.id to it.contentVersion }
        for (book in books) {
            val downloaded = syncBookWordsAndStates(
                book = book,
                remoteWordBookDataSource = remoteWordBook,
                remoteUserSyncDataSource = remoteSync
            )
            val remoteVersion = remoteVersionById[book.id] ?: 0L
            val downloadedCount = appDatabase.wordBookItemDao().getWordCountByWordBookId(book.id)
            val hasCompleteLocalContent = book.totalWords <= 0 || downloadedCount >= book.totalWords
            val shouldSeedLocalVersion =
                !downloaded &&
                    hasCompleteLocalContent &&
                    wordBookSyncStateStore.getLocalVersion(book.id) <= 0L
            if ((downloaded || shouldSeedLocalVersion) && remoteVersion > 0L) {
                wordBookSyncStateStore.setLocalVersion(book.id, remoteVersion)
            }
        }

        Log.i(TAG, "login sync completed count=${books.size}")
    }

    fun scheduleBootstrapWork() {
        val request = OneTimeWorkRequestBuilder<DataBootstrapWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WordBookDownloadWorkConstants.BOOTSTRAP_WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(
            WordBookDownloadWorkConstants.BOOTSTRAP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun schedulePostLoginBootstrapWork() {
        val request = OneTimeWorkRequestBuilder<PostLoginBootstrapWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(SyncWorkConstants.TAG_POST_LOGIN_BOOTSTRAP)
            .build()
        workManager.enqueueUniqueWork(
            SyncWorkConstants.WORK_POST_LOGIN_BOOTSTRAP,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun alignMyWordBooks(remoteBooks: List<WordBookDto>) {
        val remoteBookIds = remoteBooks.map { it.id }.toSet()
        val localBookIds = wordBookDao.getAllWordBookIds()
        val removedBookIds = localBookIds.filterNot { remoteBookIds.contains(it) }

        if (removedBookIds.isNotEmpty()) {
            removedBookIds.forEach { bookId ->
                wordLearningStateDao.deleteLearningWordByBookId(bookId)
                wordBookProgressDao.deleteByBookId(bookId)
                workManager.cancelUniqueWork(WordBookDownloadWorkConstants.uniqueWorkName(bookId))
            }
            wordBookSyncStateStore.deleteByBookIds(removedBookIds)
            wordBookDao.deleteByIds(removedBookIds)
        }

        if (remoteBooks.isNotEmpty()) {
            wordBookDao.insertWordBooks(remoteBooks.map { it.toEntity() })
            remoteBooks.forEach { book ->
                wordBookProgressDao.ensureProgressRow(bookId = book.id)
            }
            val selectedBookId = remoteBooks.firstOrNull { it.isSelected }?.id ?: remoteBooks.first().id
            appDatabase.currentWordBookSelectionDao()
                .upsert(CurrentWordBookSelectionEntity(bookId = selectedBookId))
        } else {
            appDatabase.currentWordBookSelectionDao().deleteAll()
        }
        applyPendingLocalWordBookSelectionOverride()
    }

    private fun enqueueBookPipeline(book: WordBookEntity) {
        val remoteVersion = wordBookSyncStateStore.getRemoteVersion(book.id)
        val downloadWork = OneTimeWorkRequestBuilder<WordBookDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    WordBookDownloadWorkConstants.KEY_BOOK_ID to book.id,
                    WordBookDownloadWorkConstants.KEY_BOOK_TITLE to book.title,
                    WordBookDownloadWorkConstants.KEY_TOTAL_WORDS to book.totalWords,
                    WordBookDownloadWorkConstants.KEY_REPORT_MY_BOOK to false,
                    WordBookDownloadWorkConstants.KEY_FORCE_REFRESH to false,
                    WordBookDownloadWorkConstants.KEY_TARGET_VERSION to remoteVersion
                )
            )
            .addTag(WordBookDownloadWorkConstants.TAG_DOWNLOAD)
            .addTag(WordBookDownloadWorkConstants.bookTag(book.id))
            .build()

        workManager.beginUniqueWork(
            WordBookDownloadWorkConstants.uniqueWorkName(book.id),
            ExistingWorkPolicy.KEEP,
            downloadWork
        ).enqueue()
    }

    private suspend fun syncBookWordsAndStates(
        book: WordBookEntity,
        remoteWordBookDataSource: RemoteWordBookDataSource,
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ): Boolean {
        val bookId = book.id
        val bookWordItemDao = appDatabase.wordBookItemDao()
        var downloadedCount = bookWordItemDao.getWordCountByWordBookId(bookId)
        var total = book.totalWords
        var pageSize = resolvePageSize(total)
        var didDownload = false

        if (total <= 0 || downloadedCount < total) {
            var page = downloadedCount / pageSize
            while (true) {
                val pageData = remoteWordBookDataSource
                    .getBookWords(bookId, page, pageSize)
                    .getOrThrow()

                if (total <= 0) {
                    total = if (pageData.total > 0) pageData.total.toInt() else book.totalWords
                    pageSize = resolvePageSize(total)
                }

                val items = pageData.items
                if (items.isEmpty()) break

                appDatabase.persistWordBookPage(bookId, items)
                didDownload = true

                downloadedCount = bookWordItemDao.getWordCountByWordBookId(bookId)
                if (total > 0 && downloadedCount >= total) {
                    break
                }
                page++
            }
        }

        syncWordStates(bookId, pageSize, remoteUserSyncDataSource)
        return didDownload
    }

    private suspend fun syncWordStates(
        bookId: Long,
        pageSize: Int,
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        val states = mutableListOf<WordLearningStateEntity>()
        var page = 0
        var loaded = 0
        while (true) {
            val pageData = remoteUserSyncDataSource
                .getWordStates(bookId = bookId, page = page, count = pageSize)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break

            states += items.map { it.toEntity(bookId) }
            loaded += items.size

            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }

        appDatabase.withTransaction {
            wordLearningStateDao.deleteLearningWordByBookId(bookId)
            if (states.isNotEmpty()) {
                wordLearningStateDao.upsertAll(states)
            }
            applyPendingLocalWordStateOverrides(bookId)
        }
    }

    private suspend fun syncWordBookProgress(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        val remoteProgress = remoteUserSyncDataSource.getWordBookProgressList().getOrThrow()
        appDatabase.withTransaction {
            wordBookProgressDao.deleteAll()
            if (remoteProgress.isNotEmpty()) {
                wordBookProgressDao.upsertAll(remoteProgress.map { it.toEntity() })
            }
            wordBookDao.getAllWordBookIds().forEach { bookId ->
                wordBookProgressDao.ensureProgressRow(bookId)
            }
            applyPendingLocalWordBookProgressOverrides()
        }
    }

    private suspend fun syncFavorites(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        val favorites = mutableListOf<WordFavoriteEntity>()
        var page = 0
        var loaded = 0
        while (true) {
            val pageData = remoteUserSyncDataSource
                .getFavorites(page = page, count = LOGIN_SYNC_PAGE_SIZE)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break

            favorites += items.map { it.toEntity() }
            loaded += items.size

            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }

        appDatabase.withTransaction {
            wordFavoritesDao.deleteAll()
            if (favorites.isNotEmpty()) {
                wordFavoritesDao.upsertAll(favorites)
            }
            applyPendingLocalFavoriteOverrides()
        }
    }

    private suspend fun syncStudyHistory(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        syncStudyRecords(remoteUserSyncDataSource)
        syncDailyStudyDurations(remoteUserSyncDataSource)
    }

    private suspend fun syncCheckIn(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        syncCheckInConfig(remoteUserSyncDataSource)
        syncCheckInStatus(remoteUserSyncDataSource)
        syncCheckInRecords(remoteUserSyncDataSource)
    }

    private suspend fun syncLearningData(
        remoteLearningSyncDataSource: RemoteLearningSyncDataSource
    ) {
        syncPracticeSettings(remoteLearningSyncDataSource)
        syncPracticeHistory(remoteLearningSyncDataSource)
        syncFloatingSettings(remoteLearningSyncDataSource)
        syncFloatingDisplayRecords(remoteLearningSyncDataSource)
    }

    private suspend fun syncPracticeSettings(
        remoteLearningSyncDataSource: RemoteLearningSyncDataSource
    ) {
        val remoteSettings = remoteLearningSyncDataSource.getPracticeSettings().getOrThrow()
            ?: com.chen.memorizewords.domain.model.practice.PracticeSettings()
        practiceSettingsRepository.overwriteFromRemote(remoteSettings)
        applyPendingLocalPracticeSettingsOverride()
    }

    private suspend fun syncPracticeHistory(
        remoteLearningSyncDataSource: RemoteLearningSyncDataSource
    ) {
        val durations = mutableListOf<DailyPracticeDurationEntity>()
        var page = 0
        var loaded = 0
        while (true) {
            val pageData = remoteLearningSyncDataSource
                .getPracticeDurations(page = page, count = LOGIN_SYNC_PAGE_SIZE)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break

            durations += items.map { it.toEntity() }
            loaded += items.size
            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }

        val sessions = mutableListOf<PracticeSessionBundle>()
        page = 0
        loaded = 0
        while (true) {
            val pageData = remoteLearningSyncDataSource
                .getPracticeSessions(page = page, count = LOGIN_SYNC_PAGE_SIZE)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break

            sessions += items.map { it.toBundle() }
            loaded += items.size
            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }

        appDatabase.withTransaction {
            dailyPracticeDurationDao.deleteAll()
            if (durations.isNotEmpty()) {
                dailyPracticeDurationDao.upsertAll(durations)
            }
            practiceSessionRecordDao.deleteAll()
            if (sessions.isNotEmpty()) {
                practiceSessionRecordDao.upsertAll(sessions.map { it.record })
                val words = sessions.flatMap { bundle ->
                    bundle.wordIds.toWordEntities(bundle.record.id)
                }
                if (words.isNotEmpty()) {
                    practiceSessionRecordDao.upsertWords(words)
                }
            }
            applyPendingLocalPracticeHistoryOverrides()
        }
    }

    private suspend fun syncFloatingSettings(
        remoteLearningSyncDataSource: RemoteLearningSyncDataSource
    ) {
        val remoteSettings = remoteLearningSyncDataSource.getFloatingSettings().getOrThrow()
            ?: com.chen.memorizewords.domain.model.floating.FloatingWordSettings()
        val localSettings = enforceLocalFloatingPermission(
            settings = remoteSettings,
            canDrawOverlays = Settings.canDrawOverlays(appContext)
        )
        floatingWordSettingsRepository.overwriteFromRemote(localSettings)
        applyPendingLocalFloatingSettingsOverride()
    }

    private suspend fun syncFloatingDisplayRecords(
        remoteLearningSyncDataSource: RemoteLearningSyncDataSource
    ) {
        val records = mutableListOf<FloatingDisplayRecordBundle>()
        var page = 0
        var loaded = 0
        while (true) {
            val pageData = remoteLearningSyncDataSource
                .getFloatingDisplayRecords(page = page, count = LOGIN_SYNC_PAGE_SIZE)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break

            records += items.map { it.toBundle() }
            loaded += items.size
            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }

        appDatabase.withTransaction {
            floatingWordDisplayRecordDao.deleteAll()
            if (records.isNotEmpty()) {
                floatingWordDisplayRecordDao.upsertAll(records.map { it.record })
                val words = records.flatMap { bundle ->
                    bundle.wordIds.toDisplayWordEntities(bundle.record.date)
                }
                if (words.isNotEmpty()) {
                    floatingWordDisplayRecordDao.upsertWords(words)
                }
            }
            applyPendingLocalFloatingDisplayRecordOverrides()
        }
    }

    private suspend fun syncStudyRecords(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        var page = 0
        var loaded = 0
        while (true) {
            val pageData = remoteUserSyncDataSource
                .getStudyRecords(page = page, count = LOGIN_SYNC_PAGE_SIZE)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break

            appDatabase.withTransaction {
                wordStudyRecordsDao.upsertAll(items.map { it.toEntity() })
            }
            loaded += items.size

            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }
    }

    private suspend fun syncDailyStudyDurations(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        var page = 0
        var loaded = 0
        while (true) {
            val pageData = remoteUserSyncDataSource
                .getDailyStudyDurations(page = page, count = LOGIN_SYNC_PAGE_SIZE)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break

            appDatabase.withTransaction {
                dailyStudyDurationDao.upsertAll(items.map { it.toEntity() })
            }
            loaded += items.size

            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }
        applyPendingLocalDailyStudyDurationOverrides()
    }

    private suspend fun syncCheckInConfig(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        remoteUserSyncDataSource.getCheckInConfig()
            .onSuccess { config ->
                config ?: return@onSuccess
                checkInConfigDataSource.saveDayBoundaryOffsetMinutes(config.dayBoundaryOffsetMinutes)
                checkInConfigDataSource.saveTimezoneId(config.timezoneId)
            }
            .onFailure { throwable ->
                Log.w(TAG, "skip check-in config sync after login", throwable)
            }
    }

    private suspend fun syncCheckInStatus(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        remoteUserSyncDataSource.getCheckInStatus()
            .onSuccess { status ->
                status ?: return@onSuccess
                checkInConfigDataSource.saveCachedMakeupCardBalance(status.makeupCardBalance)
                checkInConfigDataSource.saveLastCheckInSyncAt(System.currentTimeMillis())
            }
            .onFailure { throwable ->
                Log.w(TAG, "skip check-in status sync after login", throwable)
            }
    }

    private suspend fun syncCheckInRecords(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        val pendingLocalRecords = loadPendingLocalCheckInRecords()
        val remoteRecords = mutableListOf<CheckInRecordEntity>()
        var page = 0
        var loaded = 0
        try {
            while (true) {
                val pageData = remoteUserSyncDataSource
                    .getCheckInRecords(page = page, count = LOGIN_SYNC_PAGE_SIZE)
                    .getOrThrow()
                val items = pageData.items
                if (items.isEmpty()) break

                remoteRecords += items.map { it.toEntity() }
                loaded += items.size

                if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                    break
                }
                page++
            }
        } catch (throwable: Throwable) {
            Log.w(TAG, "skip check-in records sync after login", throwable)
            return
        }

        appDatabase.withTransaction {
            checkInRecordDao.deleteAll()
            if (remoteRecords.isNotEmpty()) {
                checkInRecordDao.upsertAll(remoteRecords)
            }
            if (pendingLocalRecords.isNotEmpty()) {
                checkInRecordDao.upsertAll(pendingLocalRecords)
            }
        }
        checkInConfigDataSource.saveLastCheckInSyncAt(System.currentTimeMillis())
    }

    private suspend fun loadPendingLocalCheckInRecords(): List<CheckInRecordEntity> {
        return syncOutboxDao.getByBizType(SyncOutboxBizType.CHECKIN_RECORD)
            .mapNotNull { entity ->
                runCatching {
                    gson.fromJson(entity.payload, CheckInRecordSyncPayload::class.java).toEntity()
                }.getOrNull()
            }
    }

    private suspend fun applyPendingLocalWordStateOverrides(bookId: Long) {
        val pendingDeletes = syncOutboxDao.getByBizType(SyncOutboxBizType.WORD_STATE_DELETE_BY_BOOK)
            .mapNotNull { entity ->
                runCatching {
                    gson.fromJson(
                        entity.payload,
                        WordStateDeleteByBookSyncPayload::class.java
                    )
                }.getOrNull()
            }
            .filter { it.bookId == bookId }

        if (pendingDeletes.isNotEmpty()) {
            wordLearningStateDao.deleteLearningWordByBookId(bookId)
        }

        val pendingUpserts = syncOutboxDao.getByBizType(SyncOutboxBizType.WORD_STATE_UPSERT)
            .mapNotNull { entity ->
                runCatching {
                    gson.fromJson(entity.payload, WordStateUpsertSyncPayload::class.java)
                }.getOrNull()
            }
            .filter { it.bookId == bookId }

        if (pendingUpserts.isNotEmpty()) {
            wordLearningStateDao.upsertAll(pendingUpserts.map { it.toEntity() })
        }
    }

    private fun WordStateDto.toEntity(fallbackBookId: Long): WordLearningStateEntity {
        return WordLearningStateEntity(
            wordId = wordId,
            bookId = if (bookId > 0L) bookId else fallbackBookId,
            totalLearnCount = totalLearnCount,
            lastLearnTime = lastLearnTime,
            nextReviewTime = nextReviewTime,
            masteryLevel = masteryLevel,
            userStatus = userStatus,
            repetition = repetition,
            interval = interval,
            efactor = efactor
        )
    }

    private fun FavoriteDto.toEntity(): WordFavoriteEntity {
        return WordFavoriteEntity(
            wordId = wordId,
            addedAt = parseFavoriteAddedAt(addedDate)
        )
    }

    private fun StudyRecordDto.toEntity(): WordStudyRecordsEntity {
        return WordStudyRecordsEntity(
            date = date,
            wordId = wordId,
            word = word,
            definition = definition,
            isNewWord = isNewWord
        )
    }

    private fun DailyStudyDurationDto.toEntity(): DailyStudyDurationEntity {
        return DailyStudyDurationEntity(
            date = date,
            totalDurationMs = totalDurationMs,
            updatedAt = updatedAt,
            isNewPlanCompleted = isNewPlanCompleted,
            isReviewPlanCompleted = isReviewPlanCompleted
        )
    }

    private suspend fun syncStudyPlanFromServer(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        remoteUserSyncDataSource.getStudyPlan()
            .onSuccess { remotePlan ->
                if (remotePlan == null) {
                    studyPlanDataSource.clearStudyPlan()
                } else {
                    studyPlanDataSource.saveStudyPlan(remotePlan)
                }
            }
            .onFailure { throwable ->
                Log.w(TAG, "skip study plan sync after login", throwable)
            }
        applyPendingLocalStudyPlanOverride()
    }

    private suspend fun syncOnboardingFromServer(
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        remoteUserSyncDataSource.getOnboardingState()
            .onSuccess { remoteSnapshot ->
                onboardingRepository.replaceCurrentSnapshot(remoteSnapshot)
            }
            .onFailure { throwable ->
                Log.w(TAG, "skip onboarding sync after login", throwable)
            }
        applyPendingLocalOnboardingOverride()
    }

    private suspend fun applyPendingLocalStudyPlanOverride() {
        val pendingPlan = syncOutboxDao.getByBizType(SyncOutboxBizType.STUDY_PLAN)
            .lastOrNull()
            ?.let { entity ->
                runCatching {
                    gson.fromJson(entity.payload, StudyPlanSyncPayload::class.java)
                }.getOrNull()
            } ?: return

        studyPlanDataSource.saveStudyPlan(
            com.chen.memorizewords.domain.model.study.StudyPlan(
                dailyNewCount = pendingPlan.dailyNewWords,
                dailyReviewCount = pendingPlan.dailyReviewWords,
                testMode = runCatching {
                    com.chen.memorizewords.domain.model.learning.LearningTestMode.valueOf(
                        pendingPlan.testMode
                    )
                }.getOrDefault(com.chen.memorizewords.domain.model.learning.LearningTestMode.MEANING_CHOICE),
                wordOrderType = runCatching {
                    com.chen.memorizewords.domain.repository.WordOrderType.valueOf(
                        pendingPlan.wordOrderType
                    )
                }.getOrDefault(com.chen.memorizewords.domain.repository.WordOrderType.RANDOM)
            )
        )
    }

    private suspend fun applyPendingLocalOnboardingOverride() {
        val pendingState = syncOutboxDao.getByBizType(SyncOutboxBizType.ONBOARDING_STATE)
            .lastOrNull()
            ?.let { entity ->
                runCatching {
                    gson.fromJson(entity.payload, OnboardingStateSyncPayload::class.java)
                }.getOrNull()
            } ?: return

        onboardingRepository.replaceCurrentSnapshot(
            OnboardingSnapshot(
                phase = runCatching { OnboardingPhase.valueOf(pendingState.phase) }
                    .getOrDefault(OnboardingPhase.NEEDS_WORD_BOOK),
                selectedWordBookId = pendingState.selectedWordBookId,
                revision = pendingState.revision,
                updatedAt = pendingState.updatedAt,
                completedAt = pendingState.completedAt
            )
        )
    }

    private suspend fun applyPendingLocalWordBookSelectionOverride() {
        val pendingSelection = syncOutboxDao.getByBizType(SyncOutboxBizType.WORD_BOOK_SELECTION)
            .lastOrNull()
            ?.let { entity ->
                runCatching {
                    gson.fromJson(entity.payload, WordBookSelectionSyncPayload::class.java)
                }.getOrNull()
            } ?: return
        appDatabase.currentWordBookSelectionDao()
            .upsert(CurrentWordBookSelectionEntity(bookId = pendingSelection.bookId))
    }

    private suspend fun applyPendingLocalWordBookProgressOverrides() {
        syncOutboxDao.getByBizType(SyncOutboxBizType.WORD_BOOK_PROGRESS)
            .mapNotNull { entity ->
                runCatching {
                    gson.fromJson(entity.payload, WordBookProgressSyncPayload::class.java)
                }.getOrNull()
            }
            .forEach { payload ->
                wordBookProgressDao.upsert(
                    com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressEntity(
                        wordBookId = payload.bookId,
                        correctCount = payload.correctCount,
                        wrongCount = payload.wrongCount,
                        studyDayCount = payload.studyDayCount,
                        lastStudyDate = payload.lastStudyDate
                    )
                )
            }
    }

    private suspend fun applyPendingLocalFavoriteOverrides() {
        syncOutboxDao.getByBizType(SyncOutboxBizType.FAVORITE)
            .forEach { entity ->
                val payload = runCatching {
                    gson.fromJson(entity.payload, FavoriteSyncPayload::class.java)
                }.getOrNull() ?: return@forEach

                when (entity.operation) {
                    SyncOutboxOperation.UPSERT -> {
                        val addedAt = payload.addedAt
                            ?: payload.addedDate
                                ?.takeIf { it.isNotBlank() }
                                ?.let(::parseFavoriteAddedAt)
                        if (addedAt != null) {
                            wordFavoritesDao.upsert(
                                WordFavoriteEntity(
                                    wordId = payload.wordId,
                                    addedAt = addedAt
                                )
                            )
                        }
                    }

                    SyncOutboxOperation.DELETE -> {
                        wordFavoritesDao.deleteByWordId(payload.wordId)
                    }
                }
            }
    }

    private suspend fun applyPendingLocalPracticeSettingsOverride() {
        val payload = syncOutboxDao.getByBizType(SyncOutboxBizType.PRACTICE_SETTINGS)
            .lastOrNull()
            ?.let { entity ->
                runCatching {
                    gson.fromJson(entity.payload, PracticeSettingsSyncPayload::class.java)
                }.getOrNull()
            } ?: return

        practiceSettingsRepository.overwriteFromRemote(
            com.chen.memorizewords.domain.model.practice.PracticeSettings(
                selectedBookId = payload.selectedBookId,
                intervalSeconds = payload.intervalSeconds,
                loopEnabled = payload.loopEnabled,
                showPhonetic = payload.showPhonetic,
                showMeaning = payload.showMeaning,
                playbackMode = runCatching {
                    com.chen.memorizewords.domain.model.practice.AudioLoopPlaybackMode.valueOf(
                        payload.playbackMode
                    )
                }.getOrDefault(com.chen.memorizewords.domain.model.practice.AudioLoopPlaybackMode.WORD_ONLY),
                playTimes = payload.playTimes.coerceAtLeast(1)
            )
        )
    }

    private suspend fun applyPendingLocalPracticeHistoryOverrides() {
        syncOutboxDao.getByBizType(SyncOutboxBizType.PRACTICE_DURATION)
            .mapNotNull { entity ->
                runCatching {
                    gson.fromJson(entity.payload, PracticeDurationSyncPayload::class.java)
                }.getOrNull()
            }
            .forEach { payload ->
                dailyPracticeDurationDao.upsertAll(
                    listOf(
                        DailyPracticeDurationEntity(
                            date = payload.date,
                            totalDurationMs = payload.totalDurationMs,
                            updatedAt = payload.updatedAt
                        )
                    )
                )
            }

        syncOutboxDao.getByBizType(SyncOutboxBizType.PRACTICE_SESSION)
            .mapNotNull { entity ->
                runCatching {
                    gson.fromJson(entity.payload, PracticeSessionSyncPayload::class.java)
                }.getOrNull()
            }
            .forEach { payload ->
                val record = PracticeSessionRecordEntity(
                    id = payload.id,
                    date = payload.date,
                    mode = parsePracticeMode(payload.mode),
                    entryType = parsePracticeEntryType(payload.entryType),
                    entryCount = payload.entryCount,
                    durationMs = payload.durationMs,
                    createdAt = payload.createdAt,
                    questionCount = payload.questionCount,
                    completedCount = payload.completedCount,
                    correctCount = payload.correctCount,
                    submitCount = payload.submitCount
                )
                practiceSessionRecordDao.upsertAll(listOf(record))
                practiceSessionRecordDao.deleteWordsBySessionIds(listOf(record.id))
                val words = payload.wordIds.toWordEntities(record.id)
                if (words.isNotEmpty()) {
                    practiceSessionRecordDao.upsertWords(words)
                }
            }
    }

    private suspend fun applyPendingLocalFloatingSettingsOverride() {
        val payload = syncOutboxDao.getByBizType(SyncOutboxBizType.FLOATING_SETTINGS)
            .lastOrNull()
            ?.let { entity ->
                runCatching {
                    gson.fromJson(entity.payload, FloatingSettingsSyncPayload::class.java)
                }.getOrNull()
            } ?: return

        val fieldConfigType = object : com.google.gson.reflect.TypeToken<List<com.chen.memorizewords.domain.model.floating.FloatingWordFieldConfig>>() {}.type
        val selectedIdsType = object : com.google.gson.reflect.TypeToken<List<Long>>() {}.type
        floatingWordSettingsRepository.overwriteFromRemote(
            com.chen.memorizewords.domain.model.floating.FloatingWordSettings(
                enabled = payload.enabled,
                sourceType = runCatching {
                    com.chen.memorizewords.domain.model.floating.FloatingWordSourceType.valueOf(payload.sourceType)
                }.getOrDefault(com.chen.memorizewords.domain.model.floating.FloatingWordSourceType.CURRENT_BOOK),
                orderType = runCatching {
                    com.chen.memorizewords.domain.model.floating.FloatingWordOrderType.valueOf(payload.orderType)
                }.getOrDefault(com.chen.memorizewords.domain.model.floating.FloatingWordOrderType.RANDOM),
                fieldConfigs = gson.fromJson(payload.fieldConfigsJson, fieldConfigType) ?: emptyList(),
                selectedWordIds = gson.fromJson(payload.selectedWordIdsJson, selectedIdsType) ?: emptyList(),
                floatingBallX = payload.floatingBallX,
                floatingBallY = payload.floatingBallY,
                autoStartOnBoot = payload.autoStartOnBoot,
                autoStartOnAppLaunch = payload.autoStartOnAppLaunch,
                ballOpacityPercent = payload.ballOpacityPercent,
                cardOpacityPercent = payload.cardOpacityPercent,
                dockConfig = payload.dockConfigJson?.let {
                    gson.fromJson(it, com.chen.memorizewords.domain.model.floating.FloatingDockConfig::class.java)
                } ?: com.chen.memorizewords.domain.model.floating.FloatingDockConfig(),
                dockState = payload.dockStateJson?.let {
                    gson.fromJson(it, com.chen.memorizewords.domain.model.floating.FloatingDockState::class.java)
                }
            )
        )
    }

    private suspend fun applyPendingLocalFloatingDisplayRecordOverrides() {
        syncOutboxDao.getByBizType(SyncOutboxBizType.FLOATING_DISPLAY_RECORD)
            .mapNotNull { entity ->
                runCatching {
                    gson.fromJson(entity.payload, FloatingDisplayRecordSyncPayload::class.java)
                }.getOrNull()
            }
            .forEach { payload ->
                val record = FloatingWordDisplayRecordEntity(
                    date = payload.date,
                    displayCount = payload.displayCount,
                    updatedAt = payload.updatedAt
                )
                floatingWordDisplayRecordDao.upsertAll(listOf(record))
                floatingWordDisplayRecordDao.deleteWordsByDates(listOf(record.date))
                val words = payload.wordIds.toDisplayWordEntities(record.date)
                if (words.isNotEmpty()) {
                    floatingWordDisplayRecordDao.upsertWords(words)
                }
            }
    }

    private suspend fun applyPendingLocalDailyStudyDurationOverrides() {
        val pendingDurations = syncOutboxDao.getByBizType(SyncOutboxBizType.DAILY_STUDY_DURATION)
            .mapNotNull { entity ->
                runCatching {
                    gson.fromJson(entity.payload, DailyStudyDurationSyncPayload::class.java)
                }.getOrNull()
            }
            .map { payload ->
                DailyStudyDurationEntity(
                    date = payload.date,
                    totalDurationMs = payload.totalDurationMs,
                    updatedAt = payload.updatedAt,
                    isNewPlanCompleted = payload.isNewPlanCompleted,
                    isReviewPlanCompleted = payload.isReviewPlanCompleted
                )
            }
        if (pendingDurations.isNotEmpty()) {
            dailyStudyDurationDao.upsertAll(pendingDurations)
        }
    }

    private fun cleanAndLoadPausedBookIds(validBookIds: Set<Long>): Set<Long> {
        val pausedBookIds = loadPausedBookIds().filter { validBookIds.contains(it) }.toSet()
        mmkv.encode(
            WordBookDownloadWorkConstants.KEY_PAUSED_BOOK_IDS,
            pausedBookIds.map { it.toString() }.toSet()
        )
        return pausedBookIds
    }

    private fun loadPausedBookIds(): Set<Long> {
        val values = mmkv.decodeStringSet(
            WordBookDownloadWorkConstants.KEY_PAUSED_BOOK_IDS,
            emptySet()
        ) ?: emptySet()
        return values.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun resolvePageSize(totalHint: Int): Int {
        if (totalHint > 0) {
            return min(LOGIN_SYNC_PAGE_SIZE, totalHint)
        }
        return LOGIN_SYNC_PAGE_SIZE
    }
}

internal fun enforceLocalFloatingPermission(
    settings: com.chen.memorizewords.domain.model.floating.FloatingWordSettings,
    canDrawOverlays: Boolean
): com.chen.memorizewords.domain.model.floating.FloatingWordSettings {
    if (canDrawOverlays || !settings.enabled) return settings
    return settings.copy(enabled = false)
}

private const val TAG = "DataBootstrapCoordinator"
private const val LOGIN_SYNC_PAGE_SIZE = 50
private val PRACTICE_SETTINGS_OUTBOX_TYPES = listOf(
    SyncOutboxBizType.PRACTICE_SETTINGS
)
private val PRACTICE_HISTORY_OUTBOX_TYPES = listOf(
    SyncOutboxBizType.PRACTICE_DURATION,
    SyncOutboxBizType.PRACTICE_SESSION
)
private val FLOATING_SETTINGS_OUTBOX_TYPES = listOf(
    SyncOutboxBizType.FLOATING_SETTINGS
)
private val FLOATING_HISTORY_OUTBOX_TYPES = listOf(
    SyncOutboxBizType.FLOATING_DISPLAY_RECORD
)

private fun CheckInRecordDto.toEntity(): CheckInRecordEntity {
    return CheckInRecordEntity(
        date = date,
        type = parseCheckInType(type),
        signedAt = signedAt,
        updatedAt = updatedAt
    )
}

private fun CheckInRecordSyncPayload.toEntity(): CheckInRecordEntity {
    return CheckInRecordEntity(
        date = date,
        type = parseCheckInType(type),
        signedAt = signedAt,
        updatedAt = updatedAt
    )
}

private fun PracticeDurationDto.toEntity(): DailyPracticeDurationEntity {
    return DailyPracticeDurationEntity(
        date = date,
        totalDurationMs = totalDurationMs,
        updatedAt = updatedAt
    )
}

private fun PracticeSessionDto.toBundle(): PracticeSessionBundle {
    return PracticeSessionBundle(
        record = PracticeSessionRecordEntity(
            id = id,
            date = date,
            mode = parsePracticeMode(mode),
            entryType = parsePracticeEntryType(entryType),
            entryCount = entryCount,
            durationMs = durationMs,
            createdAt = createdAt,
            questionCount = questionCount,
            completedCount = completedCount,
            correctCount = correctCount,
            submitCount = submitCount
        ),
        wordIds = wordIds
    )
}

private fun FloatingDisplayRecordDto.toBundle(): FloatingDisplayRecordBundle {
    return FloatingDisplayRecordBundle(
        record = FloatingWordDisplayRecordEntity(
            date = date,
            displayCount = displayCount,
            updatedAt = updatedAt
        ),
        wordIds = wordIds
    )
}

private fun WordBookProgressDto.toEntity() = com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressEntity(
    wordBookId = bookId,
    correctCount = correctCount,
    wrongCount = wrongCount,
    studyDayCount = studyDayCount,
    lastStudyDate = lastStudyDate.ifBlank { null }
)

private fun parseCheckInType(value: String): CheckInType {
    return runCatching { CheckInType.valueOf(value) }.getOrDefault(CheckInType.AUTO)
}

private data class PracticeSessionBundle(
    val record: PracticeSessionRecordEntity,
    val wordIds: List<Long>
)

private data class FloatingDisplayRecordBundle(
    val record: FloatingWordDisplayRecordEntity,
    val wordIds: List<Long>
)

private fun WordStateUpsertSyncPayload.toEntity(): WordLearningStateEntity {
    return WordLearningStateEntity(
        wordId = wordId,
        bookId = bookId,
        totalLearnCount = totalLearnCount,
        lastLearnTime = lastLearnTime,
        nextReviewTime = nextReviewTime,
        masteryLevel = masteryLevel,
        userStatus = userStatus,
        repetition = repetition,
        interval = interval,
        efactor = efactor
    )
}
