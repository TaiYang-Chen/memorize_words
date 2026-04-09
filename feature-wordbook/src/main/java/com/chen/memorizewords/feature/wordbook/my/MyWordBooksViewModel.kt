package com.chen.memorizewords.feature.wordbook.my

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.wordbook.WordBookInfo
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateUiState
import com.chen.memorizewords.domain.service.wordbook.WordBookUpdateCoordinator
import com.chen.memorizewords.domain.usecase.wordbook.GetMyWordBooksWithProgressUseCase
import com.chen.memorizewords.domain.usecase.wordbook.SetCurrentWordBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MyWordBooksViewModel @Inject constructor(
    private val getMyWordBooksWithProgressUseCase: GetMyWordBooksWithProgressUseCase,
    private val setCurrentWordBookUseCase: SetCurrentWordBookUseCase,
    private val updateCoordinator: WordBookUpdateCoordinator
) : BaseViewModel() {

    sealed interface Route {
        data object ToMyWordBooks : Route
        data object ToShop : Route
    }

    private val filter = MutableStateFlow("All")
    val currentFilter: StateFlow<String> = filter.asStateFlow()

    val wordBookCardState: StateFlow<List<WordBookInfo>> =
        getMyWordBooksWithProgressUseCase()
            .combine(filter) { list, currentFilter ->
                when (currentFilter) {
                    "Studying" -> list.filter { it.masteredWords != it.totalWords }
                    "Completed" -> list.filter { it.masteredWords == it.totalWords }
                    else -> list
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val updateUiState: StateFlow<WordBookUpdateUiState> =
        updateCoordinator.observeUiState()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = WordBookUpdateUiState()
            )

    fun onPageStarted() {
        viewModelScope.launch {
            updateCoordinator.onWordBookPageEntered()
        }
    }

    fun onPickMyWordBooks() {
        navigateRoute(Route.ToMyWordBooks)
    }

    fun onPickShopFragment() {
        navigateRoute(Route.ToShop)
    }

    fun setFilter(value: String) {
        filter.value = value
    }

    fun onSetCurrentWordBook(bookId: Long) {
        viewModelScope.launch {
            setCurrentWordBookUseCase(bookId)
            updateCoordinator.onWordBookPageEntered()
        }
    }

    fun onUpdateNowClick() {
        viewModelScope.launch {
            updateCoordinator.confirmUpdate()
        }
    }

    fun onRemindLaterClick() {
        viewModelScope.launch {
            updateCoordinator.remindLater()
        }
    }

    fun onIgnoreVersionClick() {
        viewModelScope.launch {
            updateCoordinator.ignoreVersion()
        }
    }

    fun onToggleDetails() {
        viewModelScope.launch {
            if (updateUiState.value.detailsVisible) {
                updateCoordinator.dismissDetails()
            } else {
                updateCoordinator.showDetails()
            }
        }
    }

    fun onToggleSettings() {
        viewModelScope.launch {
            if (updateUiState.value.settingsVisible) {
                updateCoordinator.dismissSettings()
            } else {
                updateCoordinator.showSettings()
            }
        }
    }

    fun onForegroundAlertsChanged(enabled: Boolean) {
        viewModelScope.launch {
            updateCoordinator.updateForegroundAlertsEnabled(enabled)
        }
    }

    fun onSilentUpdateChanged(enabled: Boolean) {
        viewModelScope.launch {
            updateCoordinator.updateSilentUpdateEnabled(enabled)
        }
    }
}
