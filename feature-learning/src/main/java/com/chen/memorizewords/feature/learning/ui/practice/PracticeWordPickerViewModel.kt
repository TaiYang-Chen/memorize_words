package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.usecase.GetWordDefinitionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PracticeWordPickerViewModel @Inject constructor(
    private val wordProvider: PracticeWordProvider,
    private val getWordDefinitions: GetWordDefinitionsUseCase
) : BaseViewModel() {

    data class UiState(
        val allWords: List<Word> = emptyList(),
        val filteredWords: List<Word> = emptyList(),
        val definitionTextByWordId: Map<Long, String> = emptyMap(),
        val selectedIds: Set<Long> = emptySet(),
        val query: String = "",
        val isEmpty: Boolean = true,
        val isSearchEmpty: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private var initialized: Boolean = false

    fun loadWords(initialSelectedIds: LongArray? = null) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val words = wordProvider.loadReviewWordsForPicker()
            val definitionTextByWordId = words.associate { word ->
                word.id to buildDefinitionText(getWordDefinitions(word.id).firstOrNull())
            }
            val availableIds = words.mapTo(mutableSetOf()) { it.id }
            val selectedIds = initialSelectedIds
                ?.filter { it in availableIds }
                ?.toSet()
                .orEmpty()
            _uiState.value = UiState(
                allWords = words,
                filteredWords = words,
                definitionTextByWordId = definitionTextByWordId,
                selectedIds = selectedIds,
                query = "",
                isEmpty = words.isEmpty(),
                isSearchEmpty = false
            )
        }
    }

    fun updateQuery(query: String) {
        val current = _uiState.value
        val filtered = if (query.isBlank()) {
            current.allWords
        } else {
            val normalized = query.trim().lowercase()
            current.allWords.filter {
                it.word.lowercase().contains(normalized) ||
                    it.normalizedWord.contains(normalized)
            }
        }
        _uiState.value = current.copy(
            query = query,
            filteredWords = filtered,
            isEmpty = current.allWords.isEmpty(),
            isSearchEmpty = current.allWords.isNotEmpty() && filtered.isEmpty()
        )
    }

    fun toggleSelection(wordId: Long) {
        val current = _uiState.value
        val next = current.selectedIds.toMutableSet().apply {
            if (contains(wordId)) remove(wordId) else add(wordId)
        }
        _uiState.value = current.copy(selectedIds = next)
    }

    fun selectAllVisible() {
        val current = _uiState.value
        val next = current.selectedIds.toMutableSet().apply {
            current.filteredWords.forEach { add(it.id) }
        }
        _uiState.value = current.copy(selectedIds = next)
    }

    fun clearSelection() {
        val current = _uiState.value
        _uiState.value = current.copy(selectedIds = emptySet())
    }

    private fun buildDefinitionText(definition: WordDefinitions?): String {
        if (definition == null) return ""
        val meaning = definition.meaningChinese.trim()
        if (meaning.isEmpty()) return ""
        return "${definition.partOfSpeech.abbr} $meaning"
    }
}
