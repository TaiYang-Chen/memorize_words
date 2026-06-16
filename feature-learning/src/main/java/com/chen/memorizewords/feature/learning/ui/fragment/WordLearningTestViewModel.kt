package com.chen.memorizewords.feature.learning.ui.fragment

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.word.query.WordReadFacade
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WordLearningTestViewModel @Inject constructor(
    private val wordReadFacade: WordReadFacade
) : BaseViewModel() {

    data class TestUiState(
        val mode: LearningTestMode = LearningTestMode.MEANING_CHOICE,
        val isLoading: Boolean = false,
        val prompt: String = "",
        val promptHint: String = "",
        val options: List<OptionData> = emptyList(),
        val selectedIndex: Int? = null
    ) {
        val hasAnswered: Boolean get() = selectedIndex != null
    }

    private data class LoadedData(
        val definitions: List<WordDefinitions>
    )

    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()
    private var activeLoadRequestToken: Int = 0

    fun loadData(wordId: Long, mode: LearningTestMode) {
        val effectiveMode = LearningTestMode.MEANING_CHOICE
        val requestToken = nextLoadRequestToken(activeLoadRequestToken)
        activeLoadRequestToken = requestToken
        _uiState.value = loadingUiState(effectiveMode)
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val definitions = wordReadFacade.generateMultipleChoiceOptions(wordId)
                if (definitions.isEmpty()) {
                    return@withContext LoadedData(definitions = emptyList())
                }

                LoadedData(definitions = definitions)
            }

            if (!shouldApplyLoadResult(activeLoadRequestToken, requestToken)) return@launch
            val definitions = loaded.definitions
            if (definitions.isEmpty()) {
                _uiState.value = TestUiState(
                    mode = effectiveMode,
                    isLoading = false,
                    prompt = "\u9898\u76EE\u52A0\u8F7D\u5931\u8D25",
                    promptHint = "",
                    options = emptyList(),
                    selectedIndex = null
                )
                return@launch
            }

            val options = definitions.map {
                OptionData(
                    partOfSpeech = it.partOfSpeech.abbr,
                    content = it.meaningChinese,
                    isCorrect = it.wordId == wordId
                )
            }

            val prompt = "\u4ECE\u4E0B\u5217\u56DB\u4E2A\u9009\u9879\u4E2D\uFF0C\u9009\u62E9\u6B63\u786E\u91CA\u4E49"
            val promptHint = ""

            if (!shouldApplyLoadResult(activeLoadRequestToken, requestToken)) return@launch
            _uiState.value = TestUiState(
                mode = effectiveMode,
                isLoading = false,
                prompt = prompt,
                promptHint = promptHint,
                options = options,
                selectedIndex = null
            )
        }
    }

    fun selectOption(index: Int): OptionData? {
        val current = _uiState.value
        if (current.selectedIndex != null) return null
        val option = current.options.getOrNull(index) ?: return null
        _uiState.value = current.copy(selectedIndex = index)
        return option
    }
}

internal fun loadingUiState(mode: LearningTestMode): WordLearningTestViewModel.TestUiState {
    return WordLearningTestViewModel.TestUiState(
        mode = mode,
        isLoading = true,
        prompt = "\u9898\u76EE\u52A0\u8F7D\u4E2D...",
        promptHint = "",
        options = emptyList(),
        selectedIndex = null
    )
}

internal fun nextLoadRequestToken(currentToken: Int): Int {
    return currentToken + 1
}

internal fun shouldApplyLoadResult(activeRequestToken: Int, requestToken: Int): Boolean {
    return activeRequestToken == requestToken
}
