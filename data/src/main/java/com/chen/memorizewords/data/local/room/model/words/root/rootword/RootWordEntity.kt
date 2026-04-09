package com.chen.memorizewords.data.local.room.model.words.root.rootword

import androidx.room.Entity
import androidx.room.Index

/**
 * 关联词根和单词的实体，用于表示它们之间的多对多关系。
 *
 * 一个单词可能由多个词根组成，例如 "transport" 由 "trans-" 和 "port" 构成。
 * 这张表就记录了这种关系。
 *
 * 举例说明:
 * 假设单词 "transport" 的 wordId 为 123。
 * 词根 "trans" (含义: across) 的 rootId 为 1。
 * 词根 "port" (含义: to carry) 的 rootId 为 2。
 *
 * 数据库中会有两条记录:
 * 1. RootWord(wordId = 123, rootId = 1, context = "trans", partOfSpeech = "prefix", sequence = 1)
 * 2. RootWord(wordId = 123, rootId = 2, context = "port", partOfSpeech = "root", sequence = 2)
 *
 * @property wordId 单词的 ID, 外键关联到 [WordEntity] 表。
 * @property rootId 词根的 ID, 外键关联到 [WordRootEntity] 表。
 * @property context 词根在单词中出现的具体形式或上下文。例如，在 "transport" 中，词根 "port" 的 context 就是 "port"。
 * @property partOfSpeech 词根在单词中扮演的角色，例如前缀(prefix)、后缀(suffix)或核心词根(root)。
 * @property sequence 词根在单词中出现的顺序，从 1 开始。对于 "transport"，"trans" 的 sequence 是 1，"port" 的 sequence 是 2。
 */

@Entity(
    tableName = "word_root_relation",
    // 复合主键，确保一个单词中每个词根的顺序是唯一的
    primaryKeys = ["wordId", "sequence"],
    indices = [
        Index(value = ["wordId"]),
        Index(value = ["rootId"])
    ]
)
data class RootWordEntity(
    /**
     * @see Word.id
     */
    val wordId: Long,

    /**
     * @see WordRoot.id
     */
    val rootId: Long,

    /**
     * 词根在单词中的具体文本。
     * 例如: 在 "transport" 中，对应 "trans" 或 "port"。
     */
    val context: String,

    /**
     * 词根在单词中的成分类型。
     * 例如: "prefix", "suffix", "root"。
     */
    val partOfSpeech: String,

    /**
     * 词根在单词中的排列顺序，从 1 开始。
     */
    val sequence: Int
)
