package com.chen.memorizewords.data.local.room.model.words.form

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_forms",
    indices = [
        Index("word_id"),
        Index("form_word_id"),
        Index("form_type"),
        Index("form_text")
    ]
)
data class WordFormEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    // ==== 关联信息 ====
    @ColumnInfo(name = "word_id")
    val wordId: Long,  // 原形单词ID

    @ColumnInfo(name = "form_word_id")
    val formWordId: Long? = null,  // 词形变化后的单词ID（如果存在于单词表中）

    // ==== 词形信息 ====
    @ColumnInfo(name = "form_type")
    val formType: FormType,  // 变化类型

    @ColumnInfo(name = "form_text")
    val formText: String,  // 变化后的词形
) {
    // ==== 枚举类 ====
    enum class FormType {
        // 基础词性转换
        NOUN,           // 名词形式
        NOUN_FORM,           // 名词形式
        VERB_FORM,           // 动词形式
        ADJECTIVE_FORM,      // 形容词形式
        ADVERB_FORM,         // 副词形式

        // 名词具体变化
        PLURAL,              // 复数
        SINGULAR,            // 单数
        POSSESSIVE,          // 所有格

        // 动词具体变化
        THIRD_SINGULAR,      // 第三人称单数
        PAST_TENSE,          // 过去式
        PAST_PARTICIPLE,     // 过去分词
        PRESENT_PARTICIPLE,  // 现在分词

        // 形容词具体变化
        COMPARATIVE,         // 比较级
        SUPERLATIVE,         // 最高级

        // 语体风格
        FORMAL,              // 正式形式
        INFORMAL,            // 非正式形式
        CONTRACTION,         // 缩写形式

        // 其他
        OTHER                // 其他形式
        ;

        companion object {
            fun fromString(value: String?): FormType? {
                if (value.isNullOrBlank()) {
                    return null
                }
                val normalized = value.trim()
                    .uppercase()
                    .replace('-', '_')
                    .replace(' ', '_')

                return runCatching { valueOf(normalized) }.getOrElse {
                    when (normalized) {
                        "PAST" -> PAST_TENSE
                        "ADVERB" -> ADVERB_FORM
                        "ADJECTIVE" -> ADJECTIVE_FORM
                        "VERB" -> VERB_FORM
                        "GERUND" -> PRESENT_PARTICIPLE
                        "THIRD_PERSON_SINGULAR" -> THIRD_SINGULAR
                        else -> OTHER
                    }
                }
            }
        }
    }
}
