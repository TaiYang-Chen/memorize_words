package com.chen.memorizewords.feature.wordbook.favorites

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.chen.memorizewords.core.common.paging.PageSlicePagingSource
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.study.favorites.WordFavorites
import com.chen.memorizewords.domain.usecase.word.study.GetFavoritesPageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    getFavoritesPageUseCase: GetFavoritesPageUseCase
) : BaseViewModel() {

    val pagingData: Flow<PagingData<FavoritesWord>> =
        Pager(
            config = PagingConfig(
                pageSize = FAVORITES_PAGE_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                PageSlicePagingSource { pageIndex, pageSize ->
                    getFavoritesPageUseCase(
                        pageIndex = pageIndex,
                        pageSize = pageSize
                    )
                }
            }
        ).flow
            .map { paging -> paging.map { it.toUiModel() } }
            .cachedIn(viewModelScope)

    private fun WordFavorites.toUiModel(): FavoritesWord {
        return FavoritesWord(
            date = addedDate,
            word = word,
            partOfSpeech = "",
            meaning = definitions
        )
    }

    private companion object {
        const val FAVORITES_PAGE_SIZE = 30
    }
}
