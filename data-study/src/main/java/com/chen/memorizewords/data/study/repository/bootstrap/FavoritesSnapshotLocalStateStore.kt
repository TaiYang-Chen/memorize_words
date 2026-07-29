package com.chen.memorizewords.data.study.repository.bootstrap

import androidx.room.withTransaction
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.room.model.study.favorites.WordFavoritesDao
import com.chen.memorizewords.data.study.local.room.model.study.favorites.parseFavoriteAddedAt
import com.chen.memorizewords.data.study.local.room.model.study.favorites.toEntity
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.repository.FavoritesSnapshotPort
import javax.inject.Inject

class FavoritesSnapshotLocalStateStore @Inject constructor(
    private val database: StudyDatabase,
    private val favoritesDao: WordFavoritesDao
) : FavoritesSnapshotPort {
    override suspend fun overwriteFavoritesFromRemote(favorites: List<WordFavorites>) {
        database.withTransaction {
            favoritesDao.deleteAll()
            if (favorites.isNotEmpty()) {
                favoritesDao.upsertAll(
                    favorites.map { favorite ->
                        favorite.toEntity(addedAt = parseFavoriteAddedAt(favorite.addedDate))
                    }
                )
            }
        }
    }
}
