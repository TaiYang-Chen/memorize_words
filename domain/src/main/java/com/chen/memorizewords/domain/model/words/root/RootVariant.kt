package com.chen.memorizewords.domain.model.words.root

/**
 * 表示词根变体的领域模型。
 * @property id 唯一标识符。
 * @property rootId 关联的 [com.chen.memorizewords.domain.model.words.word.WordRoot] 的 ID。
 * @property variant 词根的变体形式。
 */
data class RootVariant(
    /**
     * 唯一标识符，默认为 0。
     */
    val id: Long,
    /**
     * 关联的词根的 ID。
     */
    val rootId: Long,
    /**
     * 词根的变体形式。
     */
    val variant: String
)