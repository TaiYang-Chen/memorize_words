package com.chen.memorizewords.data.wordbook.local

import androidx.room.TypeConverter
import com.chen.memorizewords.data.wordbook.local.room.model.words.example.WordExampleEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.form.WordFormEntity
import com.chen.memorizewords.domain.word.model.enums.PartOfSpeech
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateTrigger

class WordBookRoomConverters {
    @TypeConverter
    fun fromPartOfSpeech(value: PartOfSpeech?): String? = value?.name

    @TypeConverter
    fun toPartOfSpeech(value: String?): PartOfSpeech? = value?.let { PartOfSpeech.fromString(it) }

    @TypeConverter
    fun fromDifficultyLevel(value: WordExampleEntity.DifficultyLevel?): String? = value?.name

    @TypeConverter
    fun toDifficultyLevel(value: String?): WordExampleEntity.DifficultyLevel? =
        value?.let(WordExampleEntity.DifficultyLevel::valueOf)

    @TypeConverter
    fun fromFormType(value: WordFormEntity.FormType?): String? = value?.name

    @TypeConverter
    fun toFormType(value: String?): WordFormEntity.FormType? =
        WordFormEntity.FormType.fromString(value)

    @TypeConverter
    fun fromWordBookUpdateTrigger(value: WordBookUpdateTrigger?): String? = value?.name

    @TypeConverter
    fun toWordBookUpdateTrigger(value: String?): WordBookUpdateTrigger? =
        value?.let(WordBookUpdateTrigger::valueOf)
}
