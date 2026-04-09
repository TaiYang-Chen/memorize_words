package com.chen.memorizewords.feature.learning.ui.fragment

import android.graphics.Rect
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.query.word.WordDetail
import com.chen.memorizewords.domain.query.word.WordReadFacade
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.domain.model.words.word.WordForm
import com.chen.memorizewords.domain.model.words.word.WordQuickLookupResult
import com.chen.memorizewords.domain.model.words.word.WordRoot
import com.chen.memorizewords.feature.learning.adapter.ClickableWordToken
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class WordLearningDetailViewModel @Inject constructor(
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

    private val _wordQuickPopupState = MutableStateFlow<WordQuickPopupUiState?>(null)
    val wordQuickPopupState: StateFlow<WordQuickPopupUiState?> = _wordQuickPopupState.asStateFlow()

    private var quickRequestId: Long = 0L
    private val quickLookupCache = LinkedHashMap<String, WordQuickLookupResult>(64)

    init {
        observeWordChanges()
    }

    fun setWord(word: Word) {
        _currentWord.value = word
    }

    fun requestWordQuickLookup(token: ClickableWordToken, anchor: Rect) {
        val reqId = ++quickRequestId
        val anchorCopy = Rect(anchor)
        _wordQuickPopupState.value = WordQuickPopupUiState(
            requestId = reqId,
            token = token,
            anchorRect = anchorCopy,
            status = WordQuickPopupUiState.Status.LOADING
        )

        val cached = quickLookupCache[token.normalizedWord]
        if (cached != null) {
            emitQuickLookupResult(reqId, token, anchorCopy, cached)
            return
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                wordReadFacade.lookupWordQuick(token.normalizedWord, token.rawWord)
            }
            quickLookupCache[token.normalizedWord] = result
            trimQuickLookupCacheIfNeeded()
            emitQuickLookupResult(reqId, token, anchorCopy, result)
        }
    }

    fun dismissWordQuickPopup() {
        _wordQuickPopupState.value = null
    }

    private fun emitQuickLookupResult(
        requestId: Long,
        token: ClickableWordToken,
        anchor: Rect,
        result: WordQuickLookupResult
    ) {
        if (requestId != quickRequestId) return
        val status = when (result.status) {
            WordQuickLookupResult.Status.FOUND -> WordQuickPopupUiState.Status.SUCCESS
            WordQuickLookupResult.Status.MISSING -> WordQuickPopupUiState.Status.MISSING
            WordQuickLookupResult.Status.ERROR -> WordQuickPopupUiState.Status.ERROR
        }
        _wordQuickPopupState.value = WordQuickPopupUiState(
            requestId = requestId,
            token = token,
            anchorRect = anchor,
            status = status,
            result = result,
            errorMessage = result.errorMessage
        )
    }

    private fun trimQuickLookupCacheIfNeeded(maxSize: Int = 64) {
        while (quickLookupCache.size > maxSize) {
            val iterator = quickLookupCache.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }

    private fun observeWordChanges() {
        viewModelScope.launch {
            _currentWord
                .filterNotNull()
                .distinctUntilChanged { old, new -> old.id == new.id }
                .collect { word ->
                    val detail = withContext(Dispatchers.IO) {
                        wordReadFacade.getWordDetail(word)
                    }
                    applyWordDetail(detail)
                }
        }
    }

    private fun applyWordDetail(detail: WordDetail) {
        _definitions.value = detail.definitions
        _wordExamples.value = detail.examples
        _wordRoots.value = detail.roots
        _wordForm.value = detail.forms
    }
}
