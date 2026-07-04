package com.chen.memorizewords.data.study.repository.study

import androidx.room.withTransaction
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.study.local.room.model.study.favorites.toDomain
import com.chen.memorizewords.data.study.local.room.model.study.favorites.toEntity
import com.chen.memorizewords.domain.sync.FavoriteSyncPayload
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.repository.word.FavoritesRepository
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class FavoritesRepositoryImpl @Inject constructor(
    private val studyDatabase: StudyDatabase,
    private val favoritesDao: WordFavoritesDao,
    private val SyncOutboxWriter: SyncOutboxWriter,    private val gson: Gson
) : FavoritesRepository {

    override suspend fun addFavorite(favorites: WordFavorites) {
        val addedAt = System.currentTimeMillis()
        val addedDate = formatFavoriteAddedDate(addedAt)
        studyDatabase.withTransaction {
            favoritesDao.upsert(favorites.toEntity(addedAt = addedAt))
            SyncOutboxWriter.enqueueLatest(
                bizType = OutboxTopic.FAVORITE,
                bizKey = "favorite:${favorites.wordId}",
                operation = SyncOperation.UPSERT,
                payload = gson.toJson(
                    FavoriteSyncPayload(
                        wordId = favorites.wordId,
                        word = favorites.word,
                        definitions = favorites.definitions,
                        phonetic = favorites.phonetic,
                        addedDate = addedDate,
                        addedAt = addedAt
                    )
                )
            )
        }
    }

    override suspend fun removeFavorite(wordId: Long) {
        studyDatabase.withTransaction {
            favoritesDao.deleteByWordId(wordId)
            SyncOutboxWriter.enqueueLatest(
                bizType = OutboxTopic.FAVORITE,
                bizKey = "favorite:$wordId",
                operation = SyncOperation.DELETE,
                payload = gson.toJson(FavoriteSyncPayload(wordId = wordId))
            )
        }
    }

    override suspend fun isFavorite(wordId: Long): Boolean {
        return favoritesDao.getByWordId(wordId) != null
    }

    override suspend fun getAllFavoriteWordIds(): List<Long> {
        return favoritesDao.getAllWordIds()
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

private fun formatFavoriteAddedDate(addedAt: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
        isLenient = false
    }.format(addedAt)
}
