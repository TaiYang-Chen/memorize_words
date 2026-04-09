package com.chen.memorizewords.domain.model.words.word

import com.chen.memorizewords.domain.model.words.root.RootMeaning
import com.chen.memorizewords.domain.model.words.root.RootVariant

/**
 * 表示词根的领域模型。
 * 这是应用程序在业务逻辑层中使用的模型。
 * @property id 唯一标识符。
 * @property rootWord 词根的核心词汇。
 * @property coreMeaning 词根的核心含义。
 * @property etymology 词源信息。
 * @property sourceLanguage 来源语言。
 * @property difficulty 难度级别。
 * @property tags 标签。
 * @property meanings 词根的含义列表。
 * @property variants 词根的变体列表。
 */
data class WordRoot(
    /**
     * 唯一标识符，默认为 0，表示新对象。
     */
    val id: Long,
    /**
     * 词根的核心词汇，例如 "port"。
     */
    val rootWord: String,
    /**
     * 词根的核心含义，例如 "to carry"。
     */
    val coreMeaning: String,
    /**
     * 词源信息，可以为 null。
     */
    val etymology: String?,
    /**
     * 词根的来源语言，例如 "Latin"。
     */
    val sourceLanguage: String,
    /**
     * 难度级别，默认为 1。
     */
    val difficulty: Int = 1,
    /**
     * 标签，可以为 null。
     */
    val tags: String? = null,
    /**
     * 词根的含义列表，默认为空列表。
     */
    val meanings: List<RootMeaning> = emptyList(),
    /**
     * 词根的变体列表，默认为空列表。
     */
    val variants: List<RootVariant> = emptyList()
)