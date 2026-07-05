package com.chen.memorizewords.feature.wordbook.create

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.wordbook.usecase.CreateMyWordBookUseCase
import com.chen.memorizewords.feature.wordbook.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CreateWordBookViewModel @Inject constructor(
    private val createMyWordBookUseCase: CreateMyWordBookUseCase
) : BaseViewModel() {

    sealed interface Route {
        data object Created : Route
    }

    private val _uiState = MutableStateFlow(CreateWordBookUiState())
    val uiState: StateFlow<CreateWordBookUiState> = _uiState.asStateFlow()

    fun onTitleChanged(value: String) {
        _uiState.update { it.copy(title = value, errorMessage = null) }
    }

    fun onCategoryChanged(value: String) {
        _uiState.update { it.copy(category = value, errorMessage = null) }
    }

    fun onDescriptionChanged(value: String) {
        _uiState.update { it.copy(description = value, errorMessage = null) }
    }

    fun onWordsChanged(value: String) {
        _uiState.update {
            it.copy(
                wordsText = value,
                wordStats = parseWords(value),
                errorMessage = null
            )
        }
    }

    fun normalizeWordsText() {
        val stats = uiState.value.wordStats
        _uiState.update {
            it.copy(
                wordsText = stats.words.joinToString(separator = "\n"),
                wordStats = parseWords(stats.words.joinToString(separator = "\n")),
                errorMessage = null
            )
        }
    }

    fun clearWords() {
        _uiState.update {
            it.copy(
                wordsText = "",
                wordStats = WordInputStats(),
                errorMessage = null
            )
        }
    }

    fun submit() {
        val state = uiState.value
        val validationError = validate(state)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            showToast(validationError)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            createMyWordBookUseCase(
                title = state.title.trim(),
                category = state.category.trim().ifBlank { DEFAULT_CATEGORY },
                description = state.description.trim(),
                words = state.wordStats.words
            ).onSuccess {
                showToast(CREATE_SUCCESS)
                navigateRoute(Route.Created)
            }.onFailure {
                val message = it.message?.takeIf { value -> value.isNotBlank() } ?: CREATE_FAILED
                _uiState.update { current -> current.copy(errorMessage = message) }
                showToast(message)
            }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    private fun validate(state: CreateWordBookUiState): String? {
        return when {
            state.title.trim().isEmpty() -> TITLE_REQUIRED
            state.title.trim().length > TITLE_MAX_LENGTH -> "title length must be <= $TITLE_MAX_LENGTH"
            state.category.trim().length > CATEGORY_MAX_LENGTH -> "category length must be <= $CATEGORY_MAX_LENGTH"
            state.description.trim().length > DESCRIPTION_MAX_LENGTH -> "description length must be <= $DESCRIPTION_MAX_LENGTH"
            state.wordStats.words.isEmpty() -> WORDS_REQUIRED
            state.wordStats.words.size > WORD_MAX_COUNT -> WORDS_TOO_MANY
            state.wordStats.hasTooLongWord -> WORD_TOO_LONG
            else -> null
        }
    }

    companion object {
        const val TITLE_MAX_LENGTH = 50
        const val CATEGORY_MAX_LENGTH = 20
        const val DESCRIPTION_MAX_LENGTH = 200
        const val WORD_MAX_COUNT = 1000
        const val WORD_MAX_LENGTH = 80

        private const val DEFAULT_CATEGORY = "\u81ea\u5b9a\u4e49"
        private const val TITLE_REQUIRED = "\u8bf7\u8f93\u5165\u8bcd\u4e66\u540d\u79f0"
        private const val WORDS_REQUIRED = "\u8bf7\u81f3\u5c11\u8f93\u5165 1 \u4e2a\u5355\u8bcd"
        private const val WORD_TOO_LONG = "\u5355\u4e2a\u5355\u8bcd\u4e0d\u80fd\u8d85\u8fc7 80 \u4e2a\u5b57\u7b26"
        private const val WORDS_TOO_MANY = "\u6700\u591a\u4e00\u6b21\u521b\u5efa 1000 \u4e2a\u5355\u8bcd"
        private const val CREATE_SUCCESS = "\u521b\u5efa\u6210\u529f"
        private const val CREATE_FAILED = "\u521b\u5efa\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
    }
}

data class CreateWordBookUiState(
    val title: String = "",
    val category: String = "",
    val description: String = "",
    val wordsText: String = "",
    val wordStats: WordInputStats = WordInputStats(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

data class WordInputStats(
    val words: List<String> = emptyList(),
    val duplicateLineCount: Int = 0,
    val blankLineCount: Int = 0,
    val hasTooLongWord: Boolean = false
) {
    val validWordCount: Int = words.size
}

fun parseWords(input: String): WordInputStats {
    if (input.isEmpty()) return WordInputStats()
    val words = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    var duplicateCount = 0
    var blankCount = 0
    var hasTooLongWord = false
    input.lineSequence().forEach { line ->
        val word = line.trim()
        if (word.isEmpty()) {
            blankCount++
            return@forEach
        }
        if (word.length > CreateWordBookViewModel.WORD_MAX_LENGTH) {
            hasTooLongWord = true
        }
        val normalized = word.lowercase()
        if (seen.add(normalized)) {
            words += word
        } else {
            duplicateCount++
        }
    }
    return WordInputStats(
        words = words,
        duplicateLineCount = duplicateCount,
        blankLineCount = blankCount,
        hasTooLongWord = hasTooLongWord
    )
}
