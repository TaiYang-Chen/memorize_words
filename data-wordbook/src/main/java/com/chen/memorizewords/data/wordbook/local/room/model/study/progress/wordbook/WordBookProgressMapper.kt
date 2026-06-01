package com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook

import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress

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
