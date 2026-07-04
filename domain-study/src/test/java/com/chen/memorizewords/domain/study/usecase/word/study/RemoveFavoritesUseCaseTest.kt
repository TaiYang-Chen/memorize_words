package com.chen.memorizewords.domain.study.usecase.word.study

import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.repository.word.FavoritesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class RemoveFavoritesUseCaseTest {

    @Test
    fun `removes distinct valid word ids in order`() = runBlocking {
        val repository = FakeFavoritesRepository()
        val useCase = RemoveFavoritesUseCase(
            RemoveFavoriteUseCase(repository)
        )

        useCase(listOf(3L, 0L, 2L, 3L, -1L, 1L))

        assertEquals(listOf(3L, 2L, 1L), repository.removedWordIds)
    }

    private class FakeFavoritesRepository : FavoritesRepository {
        val removedWordIds = mutableListOf<Long>()

        override suspend fun addFavorite(favorites: WordFavorites) = Unit

        override suspend fun removeFavorite(wordId: Long) {
            removedWordIds += wordId
        }

        override suspend fun isFavorite(wordId: Long): Boolean = false

        override suspend fun getAllFavoriteWordIds(): List<Long> = emptyList()

        override suspend fun getFavoritesPage(
            pageIndex: Int,
            pageSize: Int
        ): PageSlice<WordFavorites> = PageSlice(emptyList(), hasNext = false)
    }
}
