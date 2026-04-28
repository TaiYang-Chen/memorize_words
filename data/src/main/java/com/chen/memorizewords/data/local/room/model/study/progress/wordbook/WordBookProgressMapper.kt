package com.chen.memorizewords.data.model.study.progress.wordbook

import com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressSummary
import com.chen.memorizewords.domain.model.study.progress.wordbook.WordBookProgress

fun WordBookProgressSummary.toDomain(): WordBookProgress {
    return WordBookProgress(
        wordBookId = wordBookId,
        wordBookName = "",
        learningCount = learningCount,
        masteredCount = masteredCount,
        totalCount = 0,
        correctCount = correctCount,
        wrongCount = wrongCount,
        studyDayCount = studyDayCount,
        lastStudyDate = lastStudyDate.orEmpty()
    )
}
