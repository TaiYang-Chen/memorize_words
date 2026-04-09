package com.chen.memorizewords.data.local.room

import androidx.room.TypeConverter
import com.chen.memorizewords.data.local.room.model.words.example.WordExampleEntity
import com.chen.memorizewords.data.local.room.model.words.form.WordFormEntity
import com.chen.memorizewords.domain.model.words.enums.PartOfSpeech
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.let

class Converters {
    private val gson = Gson()

    // Enum converters
    @TypeConverter
    fun fromPartOfSpeech(value: PartOfSpeech?): String? = value?.name

    @TypeConverter
    fun toPartOfSpeech(value: String?): PartOfSpeech? = value?.let { PartOfSpeech.fromString(it) }

    @TypeConverter
    fun fromDifficultyLevel(value: WordExampleEntity.DifficultyLevel?): String? = value?.name

    @TypeConverter
    fun toDifficultyLevel(value: String?): WordExampleEntity.DifficultyLevel? = value?.let { WordExampleEntity.DifficultyLevel.valueOf(it) }

    @TypeConverter
    fun fromFormType(value: WordFormEntity.FormType?): String? = value?.name

    @TypeConverter
    fun toFormType(value: String?): WordFormEntity.FormType? = WordFormEntity.FormType.fromString(value)

    // List<String> converter
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return gson.fromJson(value, object : TypeToken<List<String>>() {}.type)
    }

    // List<Long> converter
    @TypeConverter
    fun fromLongList(value: List<Long>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        return gson.fromJson(value, object : TypeToken<List<Long>>() {}.type)
    }

    // List<Int> converter
    @TypeConverter
    fun fromIntList(value: List<Int>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int>? {
        return gson.fromJson(value, object : TypeToken<List<Int>>() {}.type)
    }
}
