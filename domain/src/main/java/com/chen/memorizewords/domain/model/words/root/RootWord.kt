package com.chen.memorizewords.domain.model.words.root

/**
 * 领域模型，表示一个单词与其包含的一个词根之间的关联。
 *
 * @property wordId 单词的 ID。
 * @property rootId 词根的 ID。
 * @property context 词根在单词中出现的具体形式。
 * @property partOfSpeech 词根在单词中的角色（如前缀、后缀、词根）。
 * @property sequence 词根在单词中的顺序。
 */
data class RootWord(
    val wordId: Long,
    val rootId: Long,
    val context: String,
    val partOfSpeech: String,
    val sequence: Int
)
