package com.chen.memorizewords.feature.learning.ui.fragment

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.domain.query.word.WordDetail
import com.chen.memorizewords.domain.query.word.WordReadFacade
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.domain.model.words.word.WordForm
import com.chen.memorizewords.domain.model.words.word.WordRoot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class WordEntryDetailViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val wordReadFacade: WordReadFacade
) : BaseViewModel() {

    private val _currentWord = MutableStateFlow<Word?>(null)
    val currentWord: StateFlow<Word?> = _currentWord.asStateFlow()

    private val _definitions = MutableStateFlow<List<WordDefinitions>>(emptyList())
    val definitions: StateFlow<List<WordDefinitions>> = _definitions.asStateFlow()

    private val _wordExamples = MutableStateFlow<List<WordExample>>(emptyList())
    val wordExamples: StateFlow<List<WordExample>> = _wordExamples.asStateFlow()

    private val _wordRoots = MutableStateFlow<List<WordRoot>>(emptyList())
    val wordRoots: StateFlow<List<WordRoot>> = _wordRoots.asStateFlow()

    private val _wordForm = MutableStateFlow<List<WordForm>>(emptyList())
    val wordForm: StateFlow<List<WordForm>> = _wordForm.asStateFlow()

    private var loadedWordId: Long = -1L

    fun loadWord(wordId: Long, wordText: String) {
        if (wordId > 0L && wordId == loadedWordId) return
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                when {
                    wordId > 0L -> wordReadFacade.getWordDetailById(wordId)
                    wordText.isNotBlank() -> wordReadFacade.getWordDetailByWordString(wordText)
                    else -> null
                }
            }

            if (detail == null) {
                showToast(resourceProvider.getString(R.string.word_detail_unavailable))
                return@launch
            }

            applyWordDetail(detail)
        }
    }

    private fun applyWordDetail(detail: WordDetail) {
        loadedWordId = detail.word.id
        _currentWord.value = detail.word
        _definitions.value = detail.definitions
        _wordExamples.value = detail.examples
        _wordRoots.value = detail.roots
        _wordForm.value = detail.forms
    }
}
