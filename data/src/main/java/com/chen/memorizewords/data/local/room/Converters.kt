package com.chen.memorizewords.data.local.room

import androidx.room.TypeConverter
import com.chen.memorizewords.data.local.room.model.words.example.WordExampleEntity
import com.chen.memorizewords.data.local.room.model.words.form.WordFormEntity
import com.chen.memorizewords.domain.model.practice.ExamCategory
import com.chen.memorizewords.domain.model.practice.ExamItemLastResult
import com.chen.memorizewords.domain.model.practice.ExamQuestionType
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.data.repository.sync.SyncOutboxFailureKind
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxState
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateTrigger
import com.chen.memorizewords.domain.model.words.enums.PartOfSpeech
import com.chen.memorizewords.domain.model.study.record.CheckInType
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

    @TypeConverter
    fun fromCheckInType(value: CheckInType?): String? = value?.name

    @TypeConverter
    fun toCheckInType(value: String?): CheckInType? = value?.let(CheckInType::valueOf)

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
    fun toExamItemLastResult(value: String?): ExamItemLastResult? = value?.let(ExamItemLastResult::valueOf)

    @TypeConverter
    fun fromPracticeMode(value: PracticeMode?): String? = value?.name

    @TypeConverter
    fun toPracticeMode(value: String?): PracticeMode? = value?.let(PracticeMode::valueOf)

    @TypeConverter
    fun fromPracticeEntryType(value: PracticeEntryType?): String? = value?.name

    @TypeConverter
    fun toPracticeEntryType(value: String?): PracticeEntryType? = value?.let(PracticeEntryType::valueOf)

    @TypeConverter
    fun fromSyncOutboxOperation(value: SyncOutboxOperation?): String? = value?.name

    @TypeConverter
    fun toSyncOutboxOperation(value: String?): SyncOutboxOperation? = value?.let(SyncOutboxOperation::valueOf)

    @TypeConverter
    fun fromSyncOutboxState(value: SyncOutboxState?): String? = value?.name

    @TypeConverter
    fun toSyncOutboxState(value: String?): SyncOutboxState? = value?.let(SyncOutboxState::valueOf)

    @TypeConverter
    fun fromSyncOutboxFailureKind(value: SyncOutboxFailureKind?): String? = value?.name

    @TypeConverter
    fun toSyncOutboxFailureKind(value: String?): SyncOutboxFailureKind? = value?.let(SyncOutboxFailureKind::valueOf)

    @TypeConverter
    fun fromWordBookUpdateTrigger(value: WordBookUpdateTrigger?): String? = value?.name

    @TypeConverter
    fun toWordBookUpdateTrigger(value: String?): WordBookUpdateTrigger? = value?.let(WordBookUpdateTrigger::valueOf)

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
