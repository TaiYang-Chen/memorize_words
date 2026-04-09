package com.chen.memorizewords.data.model.study.progress.wordbook

import com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressEntity
import com.chen.memorizewords.domain.model.study.progress.wordbook.WordBookProgress

fun WordBookProgress.toEntity(): WordBookProgressEntity {
    return WordBookProgressEntity(
        wordBookId = this.wordBookId,
        learnedCount = this.learningCount,
        masteredCount = this.masteredCount,
        correctCount = this.correctCount,
        wrongCount = this.wrongCount,
        studyDayCount = this.studyDayCount,
        lastStudyDate = this.lastStudyDate
    )
}

fun WordBookProgressEntity.toDomain(): WordBookProgress {
    return WordBookProgress(
        wordBookId = this.wordBookId,
        wordBookName = "",
        learningCount = this.learnedCount,
        masteredCount = this.masteredCount,
        totalCount = 0,
        correctCount = this.correctCount,
        wrongCount = this.wrongCount,
        studyDayCount = this.studyDayCount,
        lastStudyDate = this.lastStudyDate
    )
}
