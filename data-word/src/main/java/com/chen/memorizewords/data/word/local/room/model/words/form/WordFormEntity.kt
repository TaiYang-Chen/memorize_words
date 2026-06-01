package com.chen.memorizewords.data.word.local.room.model.words.form

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chen.memorizewords.data.word.local.room.model.words.word.WordEntity

@Entity(
    tableName = "word_forms",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["form_word_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
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
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "form_word_id")
    val formWordId: Long? = null,
    @ColumnInfo(name = "form_type")
    val formType: FormType,
    @ColumnInfo(name = "form_text")
    val formText: String,
) {
    enum class FormType {
        NOUN,
        NOUN_FORM,
        VERB_FORM,
        ADJECTIVE_FORM,
        ADVERB_FORM,
        PLURAL,
        SINGULAR,
        POSSESSIVE,
        THIRD_SINGULAR,
        PAST_TENSE,
        PAST_PARTICIPLE,
        PRESENT_PARTICIPLE,
        COMPARATIVE,
        SUPERLATIVE,
        FORMAL,
        INFORMAL,
        CONTRACTION,
        OTHER;

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
