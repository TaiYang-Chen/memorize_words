package com.chen.memorizewords.feature.learning.ui.fragment

import android.graphics.Rect
import com.chen.memorizewords.domain.model.words.word.WordQuickLookupResult
import com.chen.memorizewords.feature.learning.adapter.ClickableWordToken

data class WordQuickPopupUiState(
    val requestId: Long,
    val token: ClickableWordToken,
    val anchorRect: Rect,
    val status: Status,
    val result: WordQuickLookupResult? = null,
    val errorMessage: String? = null
) {
    enum class Status {
        LOADING,
        SUCCESS,
        MISSING,
        ERROR
    }
}

