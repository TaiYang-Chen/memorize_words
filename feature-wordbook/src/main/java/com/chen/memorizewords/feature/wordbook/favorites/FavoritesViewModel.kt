package com.chen.memorizewords.feature.wordbook.favorites

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.common.paging.PageSlicePagingSource
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.domain.study.model.favorites.FavoriteDefinitionFormatter
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.usecase.word.study.GetFavoritesPageUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.RemoveFavoriteUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.RemoveFavoritesUseCase
import com.chen.memorizewords.feature.wordbook.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    getFavoritesPageUseCase: GetFavoritesPageUseCase,
    private val removeFavoriteUseCase: RemoveFavoriteUseCase,
    private val removeFavoritesUseCase: RemoveFavoritesUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val _selectionState = MutableStateFlow(FavoritesSelectionUiState())
    val selectionState: StateFlow<FavoritesSelectionUiState> = _selectionState.asStateFlow()

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
            wordId = wordId,
            date = addedDate,
            word = word,
            meaning = FavoriteDefinitionFormatter.abbreviatePartsOfSpeech(definitions)
        )
    }

    fun onFavoriteClicked(item: FavoritesWord) {
        if (selectionState.value.isSelectionMode) {
            toggleSelected(item.wordId)
        } else {
            navigate(AppRoute.OpenWord(wordId = item.wordId, fromFloating = false))
        }
    }

    fun onFavoriteLongClicked(item: FavoritesWord): Boolean {
        _selectionState.update { it.select(item.wordId) }
        return true
    }

    fun requestRemoveFavorite(item: FavoritesWord) {
        showConfirmDialog(
            action = "$ACTION_REMOVE_FAVORITE:${item.wordId}",
            title = resourceProvider.getString(R.string.feature_wordbook_favorites_delete_title),
            message = resourceProvider.getString(
                R.string.feature_wordbook_favorites_delete_message,
                item.word
            ),
            confirmText = resourceProvider.getString(R.string.feature_wordbook_favorites_delete),
            cancelText = resourceProvider.getString(R.string.feature_wordbook_favorites_cancel)
        )
    }

    fun requestRemoveSelectedFavorites() {
        val selectedCount = selectionState.value.selectedCount
        if (selectedCount <= 0) return
        showConfirmDialog(
            action = ACTION_REMOVE_SELECTED_FAVORITES,
            title = resourceProvider.getString(R.string.feature_wordbook_favorites_delete_selected_title),
            message = resourceProvider.getString(
                R.string.feature_wordbook_favorites_delete_selected_message,
                selectedCount
            ),
            confirmText = resourceProvider.getString(R.string.feature_wordbook_favorites_delete),
            cancelText = resourceProvider.getString(R.string.feature_wordbook_favorites_cancel)
        )
    }

    fun onRemoveFavoriteConfirmed(wordId: Long) {
        viewModelScope.launch {
            runCatching {
                removeFavoriteUseCase(wordId)
            }.onSuccess {
                _selectionState.update { it.unselect(wordId) }
                showToast(resourceProvider.getString(R.string.feature_wordbook_favorites_deleted))
                emitEffect(FavoritesEffect.RefreshList)
            }.onFailure {
                showToast(resourceProvider.getString(R.string.feature_wordbook_favorites_delete_failed))
            }
        }
    }

    fun onRemoveSelectedFavoritesConfirmed() {
        val selectedIds = selectionState.value.selectedIds
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                removeFavoritesUseCase(selectedIds)
            }.onSuccess {
                clearSelection()
                showToast(
                    resourceProvider.getString(
                        R.string.feature_wordbook_favorites_selected_deleted,
                        selectedIds.size
                    )
                )
                emitEffect(FavoritesEffect.RefreshList)
            }.onFailure {
                showToast(resourceProvider.getString(R.string.feature_wordbook_favorites_delete_failed))
            }
        }
    }

    fun clearSelection() {
        _selectionState.value = FavoritesSelectionUiState()
    }

    private fun toggleSelected(wordId: Long) {
        _selectionState.update { it.toggle(wordId) }
    }

    companion object {
        const val ACTION_REMOVE_FAVORITE = "remove_favorite"
        const val ACTION_REMOVE_SELECTED_FAVORITES = "remove_selected_favorites"
        const val FAVORITES_PAGE_SIZE = 30
    }
}

data class FavoritesSelectionUiState(
    val selectedIds: Set<Long> = emptySet()
) {
    val selectedCount: Int = selectedIds.size
    val isSelectionMode: Boolean = selectedIds.isNotEmpty()

    fun select(wordId: Long): FavoritesSelectionUiState {
        if (wordId <= 0L) return this
        return copy(selectedIds = selectedIds + wordId)
    }

    fun unselect(wordId: Long): FavoritesSelectionUiState {
        return copy(selectedIds = selectedIds - wordId)
    }

    fun toggle(wordId: Long): FavoritesSelectionUiState {
        if (wordId <= 0L) return this
        return if (wordId in selectedIds) {
            unselect(wordId)
        } else {
            select(wordId)
        }
    }
}

sealed interface FavoritesEffect : UiEffect {
    data object RefreshList : FavoritesEffect
}
