package com.chen.memorizewords.domain.model.words.enums

enum class FormType {
    // 名词变化
    PLURAL,                     // 复数
    POSSESSIVE,                 // 所有格
    SINGULAR,                   // 单数（用于本身就是复数的词）

    // 动词变化
    THIRD_SINGULAR,            // 第三人称单数
    PAST_TENSE,                // 过去式
    PAST_PARTICIPLE,           // 过去分词
    PRESENT_PARTICIPLE,        // 现在分词
    GERUND,                    // 动名词

    // 形容词变化
    COMPARATIVE,               // 比较级
    SUPERLATIVE,               // 最高级

    // 其他变化
    ADVERB,                    // 副词形式
    NOUN,                      // 名词形式
    ADJECTIVE,                 // 形容词形式
    VERB,                      // 动词形式
    CONTRACTION,               // 缩写形式
    FULL_FORM,                 // 完整形式
    SHORT_FORM,                // 简短形式
    INFORMAL,                  // 非正式形式
    FORMAL,                    // 正式形式
    DIMINUTIVE,                // 指小形式
    AUGMENTATIVE,              // 增大形式
    FEMININE,                  // 阴性形式
    MASCULINE,                 // 阳性形式
    NEUTER,                    // 中性形式
    OTHER;                     // 其他形式

    companion object {
        fun fromString(value: String): FormType {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                OTHER
            }
        }
    }
}
