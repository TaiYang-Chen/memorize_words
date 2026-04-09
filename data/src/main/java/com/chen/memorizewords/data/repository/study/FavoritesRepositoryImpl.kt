package com.chen.memorizewords.data.repository.study

import androidx.room.withTransaction
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.local.room.model.study.favorites.toDomain
import com.chen.memorizewords.data.local.room.model.study.favorites.toEntity
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.repository.sync.FavoriteSyncPayload
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.common.PageSlice
import com.chen.memorizewords.domain.model.study.favorites.WordFavorites
import com.chen.memorizewords.domain.repository.word.FavoritesRepository
import com.google.gson.Gson
import javax.inject.Inject

class FavoritesRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val favoritesDao: WordFavoritesDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : FavoritesRepository {

    override suspend fun addFavorite(favorites: WordFavorites) {
        appDatabase.withTransaction {
            favoritesDao.upsert(favorites.toEntity())
            syncOutboxDao.upsert(
                syncOutboxEntity(
                    bizType = SyncOutboxBizType.FAVORITE,
                    bizKey = "favorite:${favorites.wordId}",
                    operation = SyncOutboxOperation.UPSERT,
                    payload = gson.toJson(
                        FavoriteSyncPayload(
                            wordId = favorites.wordId,
                            word = favorites.word,
                            definitions = favorites.definitions,
                            phonetic = favorites.phonetic,
                            addedDate = favorites.addedDate
                        )
                    )
                )
            )
        }
        syncOutboxWorkScheduler.scheduleDrain()
    }

    override suspend fun removeFavorite(wordId: Long) {
        appDatabase.withTransaction {
            favoritesDao.deleteByWordId(wordId)
            syncOutboxDao.upsert(
                syncOutboxEntity(
                    bizType = SyncOutboxBizType.FAVORITE,
                    bizKey = "favorite:$wordId",
                    operation = SyncOutboxOperation.DELETE,
                    payload = gson.toJson(FavoriteSyncPayload(wordId = wordId))
                )
            )
        }
        syncOutboxWorkScheduler.scheduleDrain()
    }

    override suspend fun isFavorite(wordId: Long): Boolean {
        return favoritesDao.getByWordId(wordId) != null
    }

    override suspend fun getFavoritesPage(pageIndex: Int, pageSize: Int): PageSlice<WordFavorites> {
        val safePageIndex = pageIndex.coerceAtLeast(0)
        val safePageSize = pageSize.coerceAtLeast(1)
        val offset = safePageIndex * safePageSize
        val totalCount = favoritesDao.countAll()
        val rows = favoritesDao.getPagedRows(limit = safePageSize, offset = offset)
        return PageSlice(
            items = rows.map { it.toDomain() },
            hasNext = offset + rows.size < totalCount
        )
    }
}
