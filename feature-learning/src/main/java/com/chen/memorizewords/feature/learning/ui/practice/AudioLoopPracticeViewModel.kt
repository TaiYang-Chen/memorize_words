package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.domain.service.practice.PracticeFacade
import com.chen.memorizewords.domain.usecase.wordbook.GetCurrentWordBookUseCase
import com.chen.memorizewords.domain.usecase.wordbook.GetMyWordBooksWithProgressUseCase
import com.chen.memorizewords.domain.model.practice.PracticeSettings
import com.chen.memorizewords.domain.model.wordbook.WordBookInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AudioLoopPracticeViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val practiceFacade: PracticeFacade,
    private val getMyWordBooksWithProgressUseCase: GetMyWordBooksWithProgressUseCase,
    private val getCurrentWordBookUseCase: GetCurrentWordBookUseCase
) : ViewModel() {

    data class UiState(
        val settings: PracticeSettings = PracticeSettings(),
        val books: List<WordBookInfo> = emptyList(),
        val currentBookName: String = ""
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        observeBooks()
    }

    fun saveSettings(settings: PracticeSettings) {
        viewModelScope.launch {
            practiceFacade.saveSettings(settings)
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            practiceFacade.observeSettings().collect { settings ->
                val current = _uiState.value
                _uiState.value = current.copy(settings = settings)
                refreshBookName(settings.selectedBookId, current.books)
            }
        }
    }

    private fun observeBooks() {
        viewModelScope.launch {
            getMyWordBooksWithProgressUseCase().collect { books ->
                val current = _uiState.value
                _uiState.value = current.copy(books = books)
                refreshBookName(current.settings.selectedBookId, books)
            }
        }
    }

    private fun refreshBookName(selectedBookId: Long, books: List<WordBookInfo>) {
        viewModelScope.launch {
            val resolvedName = if (selectedBookId > 0L) {
                books.firstOrNull { it.bookId == selectedBookId }?.title
                    ?: resourceProvider.getString(
                        R.string.practice_audio_loop_book_fallback,
                        selectedBookId
                    )
            } else {
                getCurrentWordBookUseCase()?.title
                    ?: resourceProvider.getString(R.string.practice_audio_loop_no_book_selected)
            }
            _uiState.value = _uiState.value.copy(currentBookName = resolvedName)
        }
    }
}
