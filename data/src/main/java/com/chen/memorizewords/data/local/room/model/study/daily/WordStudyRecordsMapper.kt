package com.chen.memorizewords.data.local.room.model.study.daily

import com.chen.memorizewords.domain.model.study.record.DailyStudyRecords

fun DailyStudyRecords.toEntity(): WordStudyRecordsEntity {
    return WordStudyRecordsEntity(
        date = this.date,
        wordId = this.wordId,
        word = this.word,
        definition = this.definition,
        isNewWord = this.isNewWord
    )
}

fun WordStudyRecordsEntity.toDomain(): DailyStudyRecords {
    return DailyStudyRecords(
        date = this.date,
        wordId = this.wordId,
        word = this.word,
        definition = this.definition,
        isNewWord = this.isNewWord
    )
}