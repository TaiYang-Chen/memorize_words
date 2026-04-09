package com.chen.memorizewords.domain.model.words.word

import com.chen.memorizewords.domain.model.words.enums.FormType

data class WordForm(
    val id: Long,
    val wordId: Long,
    val formWordId: Long? = null,
    val formType: FormType,
    val formText: String
)