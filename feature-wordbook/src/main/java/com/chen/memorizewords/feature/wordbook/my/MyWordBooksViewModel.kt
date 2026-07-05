package com.chen.memorizewords.feature.wordbook.my

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateUiState
import com.chen.memorizewords.domain.wordbook.service.WordBookUpdateCoordinator
import com.chen.memorizewords.domain.wordbook.usecase.DeleteMyWordBookUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetMyWordBooksWithProgressUseCase
import com.chen.memorizewords.domain.wordbook.usecase.SetCurrentWordBookUseCase
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
    private val deleteMyWordBookUseCase: DeleteMyWordBookUseCase,
    private val updateCoordinator: WordBookUpdateCoordinator
) : BaseViewModel() {

    sealed interface Route {
        data object ToMyWordBooks : Route
        data object ToShop : Route
        data object ToCreate : Route
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

    fun onCreateWordBookClick() {
        navigateRoute(Route.ToCreate)
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

    fun onRequestDeleteWordBook(wordBook: WordBookInfo) {
        if (wordBook.isSelected) {
            showToast(CURRENT_WORD_BOOK_DELETE_BLOCKED)
            return
        }
        emitEvent(
            UiEvent.Dialog.ConfirmBottom(
                action = "$ACTION_DELETE_WORD_BOOK:${wordBook.bookId}",
                title = DELETE_WORD_BOOK_TITLE,
                message = DELETE_WORD_BOOK_MESSAGE
            )
        )
    }

    fun onDeleteWordBookConfirmed(bookId: Long) {
        val wordBook = wordBookCardState.value.firstOrNull { it.bookId == bookId }
        if (wordBook?.isSelected == true) {
            showToast(CURRENT_WORD_BOOK_DELETE_BLOCKED)
            return
        }
        viewModelScope.launch {
            deleteMyWordBookUseCase(bookId)
                .onSuccess {
                    showToast(DELETE_WORD_BOOK_SUCCESS)
                }
                .onFailure { failure ->
                    val message = if (failure is IllegalStateException) {
                        CURRENT_WORD_BOOK_DELETE_BLOCKED
                    } else {
                        DELETE_WORD_BOOK_FAILED
                    }
                    showToast(message)
                }
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

    companion object {
        const val ACTION_DELETE_WORD_BOOK = "delete_word_book"
        private const val DELETE_WORD_BOOK_TITLE = "\u786E\u8BA4\u5220\u9664\u8BCD\u4E66"
        private const val DELETE_WORD_BOOK_MESSAGE =
            "\u5220\u9664\u540E\u5C06\u4ECE\u8D26\u53F7\u8BCD\u4E66\u5217\u8868\u79FB\u9664\uFF0C\u4E0D\u4F1A\u5220\u9664\u516C\u5171\u8BCD\u5E93\u5185\u5BB9\u3002"
        private const val DELETE_WORD_BOOK_SUCCESS = "\u5220\u9664\u6210\u529F"
        private const val DELETE_WORD_BOOK_FAILED = "\u5220\u9664\u5931\u8D25\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5"
        private const val CURRENT_WORD_BOOK_DELETE_BLOCKED =
            "\u6B63\u5728\u5B66\u4E60\u7684\u8BCD\u4E66\u4E0D\u80FD\u5220\u9664\uFF0C\u8BF7\u5148\u5207\u6362\u8BCD\u4E66"
    }
}
