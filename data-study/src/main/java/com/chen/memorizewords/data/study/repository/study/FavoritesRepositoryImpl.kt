package com.chen.memorizewords.data.study.repository.study

import androidx.room.withTransaction
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.study.local.room.model.study.favorites.toDomain
import com.chen.memorizewords.data.study.local.room.model.study.favorites.toEntity
import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.repository.word.FavoritesRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class FavoritesRepositoryImpl @Inject constructor(
    private val studyDatabase: StudyDatabase,
    private val favoritesDao: WordFavoritesDao,
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val directSyncLauncher: DirectSyncLauncher
) : FavoritesRepository {

    override suspend fun addFavorite(favorites: WordFavorites) {
        val addedAt = System.currentTimeMillis()
        val addedDate = formatFavoriteAddedDate(addedAt)
        studyDatabase.withTransaction {
            favoritesDao.upsert(favorites.toEntity(addedAt = addedAt))
        }
        val snapshot = favorites.copy(addedDate = addedDate)
        directSyncLauncher.launch(
            operation = "favorite_add",
            orderingKey = "favorite:${snapshot.wordId}",
            request = { remoteUserSyncDataSource.addFavorite(snapshot) }
        )
    }

    override suspend fun removeFavorite(wordId: Long) {
        studyDatabase.withTransaction {
            favoritesDao.deleteByWordId(wordId)
        }
        directSyncLauncher.launch(
            operation = "favorite_remove",
            orderingKey = "favorite:$wordId",
            request = { remoteUserSyncDataSource.removeFavorite(wordId) }
        )
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
