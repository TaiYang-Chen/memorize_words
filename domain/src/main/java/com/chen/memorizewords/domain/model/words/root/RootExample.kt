package com.chen.memorizewords.domain.model.words.root

/**
 * 表示词根含义示例的领域模型。
 * @property id 唯一标识符。
 * @property meaningId 关联的 [RootMeaning] 的 ID。
 * @property exampleSentence 示例句子。
 * @property translation 示例句子的翻译。
 */
data class RootExample(
    /**
     * 唯一标识符，默认为 0。
     */
    val id: Long,
    /**
     * 关联的词根含义的 ID。
     */
    val meaningId: Long,
    /**
     * 包含词根用法的示例句子。
     */
    val exampleSentence: String,
    /**
     * 示例句子的中文翻译。
     */
    val translation: String
)