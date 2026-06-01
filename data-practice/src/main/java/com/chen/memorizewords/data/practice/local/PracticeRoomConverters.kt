package com.chen.memorizewords.data.practice.local

import androidx.room.TypeConverter
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.model.ExamCategory
import com.chen.memorizewords.domain.practice.model.ExamItemLastResult
import com.chen.memorizewords.domain.practice.model.ExamQuestionType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PracticeRoomConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromExamQuestionType(value: ExamQuestionType?): String? = value?.name

    @TypeConverter
    fun toExamQuestionType(value: String?): ExamQuestionType? = value?.let(ExamQuestionType::valueOf)

    @TypeConverter
    fun fromExamCategory(value: ExamCategory?): String? = value?.name

    @TypeConverter
    fun toExamCategory(value: String?): ExamCategory? = value?.let(ExamCategory::valueOf)

    @TypeConverter
    fun fromExamItemLastResult(value: ExamItemLastResult?): String? = value?.name

    @TypeConverter
    fun toExamItemLastResult(value: String?): ExamItemLastResult? =
        value?.let(ExamItemLastResult::valueOf)

    @TypeConverter
    fun fromPracticeMode(value: PracticeMode?): String? = value?.name

    @TypeConverter
    fun toPracticeMode(value: String?): PracticeMode? = value?.let(PracticeMode::valueOf)

    @TypeConverter
    fun fromPracticeEntryType(value: PracticeEntryType?): String? = value?.name

    @TypeConverter
    fun toPracticeEntryType(value: String?): PracticeEntryType? =
        value?.let(PracticeEntryType::valueOf)

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return gson.fromJson(value, object : TypeToken<List<String>>() {}.type)
    }

    @TypeConverter
    fun fromIntList(value: List<Int>?): String? = gson.toJson(value)

    @TypeConverter
    fun toIntList(value: String?): List<Int>? {
        return gson.fromJson(value, object : TypeToken<List<Int>>() {}.type)
    }
}
