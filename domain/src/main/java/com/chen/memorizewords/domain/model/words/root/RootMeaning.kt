package com.chen.memorizewords.domain.model.words.root

/**
 * 表示词根含义的领域模型。
 * @property id 唯一标识符。
 * @property rootId 关联的 [com.chen.memorizewords.domain.model.words.word.WordRoot] 的 ID。
 * @property meaning 具体的含义。
 * @property examples 该含义的示例列表。
 */
data class RootMeaning(
    /**
     * 唯一标识符，默认为 0。
     */
    val id: Long,
    /**
     * 关联的词根的 ID。
     */
    val rootId: Long,
    /**
     * 在该词性下的具体含义。
     */
    val meaning: String,
    /**
     * 与该含义相关的示例列表。
     */
    val examples: List<RootExample>
)