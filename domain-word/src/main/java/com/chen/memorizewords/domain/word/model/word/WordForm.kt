package com.chen.memorizewords.domain.word.model.word
import com.chen.memorizewords.domain.word.model.enums.FormType

data class WordForm(
    val id: Long,
    val wordId: Long,
    val formWordId: Long? = null,
    val formType: FormType,
    val formText: String
)