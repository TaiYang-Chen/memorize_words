package com.chen.memorizewords.domain.model.practice

data class ExamItemState(
    val examItemId: Long,
    val favorite: Boolean = false,
    val wrongBook: Boolean = false,
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val lastResult: ExamItemLastResult? = null,
    val lastAnsweredAt: Long? = null
)

data class WordExamItem(
    val id: Long,
    val wordId: Long,
    val questionType: ExamQuestionType,
    val examCategory: ExamCategory,
    val paperName: String,
    val difficultyLevel: Int,
    val sortOrder: Int,
    val groupKey: String?,
    val contentText: String,
    val contextText: String?,
    val options: List<String> = emptyList(),
    val answers: List<String> = emptyList(),
    val leftItems: List<String> = emptyList(),
    val rightItems: List<String> = emptyList(),
    val answerIndexes: List<Int> = emptyList(),
    val analysisText: String? = null,
    val state: ExamItemState? = null
)

data class ExamPracticeWord(
    val wordId: Long,
    val word: String,
    val examItems: List<WordExamItem>,
    val totalCount: Int,
    val favoriteCount: Int,
    val wrongCount: Int,
    val objectiveCount: Int,
    val isReadOnlyCache: Boolean = false
)

data class ExamPracticeAnswerSubmission(
    val itemId: Long,
    val answers: List<String> = emptyList(),
    val answerIndexes: List<Int> = emptyList(),
    val viewedAnswer: Boolean = false,
    val submitCount: Int = 0
)

data class ExamPracticeSessionSubmission(
    val wordId: Long,
    val sessionId: Long? = null,
    val durationMs: Long = 0L,
    val questionCount: Int = 0,
    val completedCount: Int = 0,
    val correctCount: Int = 0,
    val submitCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val items: List<ExamPracticeAnswerSubmission> = emptyList()
)
