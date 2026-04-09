package com.chen.memorizewords.data.repository.practice

import androidx.room.withTransaction
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.practice.exam.ExamPracticeDao
import com.chen.memorizewords.data.local.room.model.practice.exam.ExamPracticeItemEntity
import com.chen.memorizewords.data.local.room.model.practice.exam.ExamPracticeItemStateEntity
import com.chen.memorizewords.data.local.room.model.practice.exam.ExamPracticeWordMetaEntity
import com.chen.memorizewords.data.remote.practice.RemoteExamPracticeDataSource
import com.chen.memorizewords.domain.model.practice.ExamCategory
import com.chen.memorizewords.domain.model.practice.ExamItemLastResult
import com.chen.memorizewords.domain.model.practice.ExamItemState
import com.chen.memorizewords.domain.model.practice.ExamPracticeAnswerSubmission
import com.chen.memorizewords.domain.model.practice.ExamPracticeSessionSubmission
import com.chen.memorizewords.domain.model.practice.ExamPracticeWord
import com.chen.memorizewords.domain.model.practice.ExamQuestionType
import com.chen.memorizewords.domain.model.practice.WordExamItem
import com.chen.memorizewords.domain.repository.practice.ExamPracticeRepository
import com.chen.memorizewords.network.api.practice.ExamItemStateDto
import com.chen.memorizewords.network.api.practice.ExamPracticeSessionItemAnswerDto
import com.chen.memorizewords.network.api.practice.ExamPracticeSessionSubmitRequest
import com.chen.memorizewords.network.api.practice.ExamPracticeWordResponseDto
import javax.inject.Inject

class ExamPracticeRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val examPracticeDao: ExamPracticeDao,
    private val remoteExamPracticeDataSource: RemoteExamPracticeDataSource
) : ExamPracticeRepository {

    override suspend fun getWordPractice(wordId: Long): Result<ExamPracticeWord> {
        val remoteResult = remoteExamPracticeDataSource.getWordPractice(wordId)
        remoteResult.onSuccess { response ->
            cacheResponse(response)
            return Result.success(response.toDomain(isReadOnlyCache = false))
        }
        val cached = loadCachedWord(wordId)
        return if (cached != null) {
            Result.success(cached.copy(isReadOnlyCache = true))
        } else {
            Result.failure(remoteResult.exceptionOrNull() ?: IllegalStateException("Load exam practice failed"))
        }
    }

    override suspend fun updateFavorite(itemId: Long, favorite: Boolean): Result<ExamItemState> {
        val result = remoteExamPracticeDataSource.updateFavorite(itemId, favorite)
        return result.map { dto ->
            val entity = dto.toEntity()
            examPracticeDao.upsertStates(listOf(entity))
            entity.toDomain()
        }
    }

    override suspend fun submitSession(submission: ExamPracticeSessionSubmission): Result<Unit> {
        return remoteExamPracticeDataSource.submitSession(
            ExamPracticeSessionSubmitRequest(
                wordId = submission.wordId,
                sessionId = submission.sessionId,
                durationMs = submission.durationMs,
                questionCount = submission.questionCount,
                completedCount = submission.completedCount,
                correctCount = submission.correctCount,
                submitCount = submission.submitCount,
                createdAt = submission.createdAt,
                items = submission.items.map { it.toDto() }
            )
        )
    }

    private suspend fun cacheResponse(response: ExamPracticeWordResponseDto) {
        val cachedAt = System.currentTimeMillis()
        appDatabase.withTransaction {
            examPracticeDao.upsertWordMeta(
                ExamPracticeWordMetaEntity(
                    wordId = response.wordId,
                    word = response.word,
                    totalCount = response.totalCount,
                    favoriteCount = response.favoriteCount,
                    wrongCount = response.wrongCount,
                    objectiveCount = response.objectiveCount,
                    cachedAt = cachedAt
                )
            )
            examPracticeDao.deleteItemsByWordId(response.wordId)
            examPracticeDao.upsertItems(response.examItemDtos.map { it.toEntity(cachedAt) })
            examPracticeDao.upsertStates(
                response.examItemDtos.mapNotNull { it.state?.toEntity() }
            )
        }
    }

    private suspend fun loadCachedWord(wordId: Long): ExamPracticeWord? {
        val meta = examPracticeDao.getWordMeta(wordId) ?: return null
        val items = examPracticeDao.getItemsByWordId(wordId)
        if (items.isEmpty()) return null
        val states = examPracticeDao.getStatesByItemIds(items.map { it.id })
            .associateBy { it.examItemId }
        return ExamPracticeWord(
            wordId = meta.wordId,
            word = meta.word,
            examItems = items.map { entity -> entity.toDomain(states[entity.id]?.toDomain()) },
            totalCount = meta.totalCount,
            favoriteCount = meta.favoriteCount,
            wrongCount = meta.wrongCount,
            objectiveCount = meta.objectiveCount,
            isReadOnlyCache = true
        )
    }
}

private fun ExamPracticeWordResponseDto.toDomain(isReadOnlyCache: Boolean): ExamPracticeWord {
    return ExamPracticeWord(
        wordId = wordId,
        word = word,
        examItems = examItemDtos.map { it.toDomain() },
        totalCount = totalCount,
        favoriteCount = favoriteCount,
        wrongCount = wrongCount,
        objectiveCount = objectiveCount,
        isReadOnlyCache = isReadOnlyCache
    )
}

private fun com.chen.memorizewords.network.api.practice.WordExamItemDto.toEntity(cachedAt: Long): ExamPracticeItemEntity {
    return ExamPracticeItemEntity(
        id = id,
        wordId = wordId,
        questionType = questionType,
        examCategory = examCategory,
        paperName = paperName,
        difficultyLevel = difficultyLevel,
        sortOrder = sortOrder,
        groupKey = groupKey,
        contentText = contentText,
        contextText = contextText,
        options = options,
        answers = answers,
        leftItems = leftItems,
        rightItems = rightItems,
        answerIndexes = answerIndexes,
        analysisText = analysisText,
        cachedAt = cachedAt
    )
}

private fun com.chen.memorizewords.network.api.practice.WordExamItemDto.toDomain(): WordExamItem {
    return WordExamItem(
        id = id,
        wordId = wordId,
        questionType = parseQuestionType(questionType),
        examCategory = parseExamCategory(examCategory),
        paperName = paperName,
        difficultyLevel = difficultyLevel,
        sortOrder = sortOrder,
        groupKey = groupKey,
        contentText = contentText,
        contextText = contextText,
        options = options,
        answers = answers,
        leftItems = leftItems,
        rightItems = rightItems,
        answerIndexes = answerIndexes,
        analysisText = analysisText,
        state = state?.toDomain()
    )
}

private fun ExamPracticeItemEntity.toDomain(state: ExamItemState?): WordExamItem {
    return WordExamItem(
        id = id,
        wordId = wordId,
        questionType = parseQuestionType(questionType),
        examCategory = parseExamCategory(examCategory),
        paperName = paperName,
        difficultyLevel = difficultyLevel,
        sortOrder = sortOrder,
        groupKey = groupKey,
        contentText = contentText,
        contextText = contextText,
        options = options,
        answers = answers,
        leftItems = leftItems,
        rightItems = rightItems,
        answerIndexes = answerIndexes,
        analysisText = analysisText,
        state = state
    )
}

private fun ExamItemStateDto.toEntity(): ExamPracticeItemStateEntity {
    return ExamPracticeItemStateEntity(
        examItemId = examItemId,
        favorite = favorite,
        wrongBook = wrongBook,
        attemptCount = attemptCount,
        correctCount = correctCount,
        lastResult = lastResult,
        lastAnsweredAt = lastAnsweredAt
    )
}

private fun ExamPracticeItemStateEntity.toDomain(): ExamItemState {
    return ExamItemState(
        examItemId = examItemId,
        favorite = favorite,
        wrongBook = wrongBook,
        attemptCount = attemptCount,
        correctCount = correctCount,
        lastResult = lastResult?.let(::parseLastResult),
        lastAnsweredAt = lastAnsweredAt
    )
}

private fun ExamItemStateDto.toDomain(): ExamItemState = toEntity().toDomain()

private fun ExamPracticeAnswerSubmission.toDto(): ExamPracticeSessionItemAnswerDto {
    return ExamPracticeSessionItemAnswerDto(
        itemId = itemId,
        answers = answers,
        answerIndexes = answerIndexes,
        viewedAnswer = viewedAnswer,
        submitCount = submitCount
    )
}

private fun parseQuestionType(value: String): ExamQuestionType {
    return runCatching { ExamQuestionType.valueOf(value) }.getOrDefault(ExamQuestionType.SINGLE_CHOICE)
}

private fun parseExamCategory(value: String): ExamCategory {
    return runCatching { ExamCategory.valueOf(value) }.getOrDefault(ExamCategory.CET4)
}

private fun parseLastResult(value: String): ExamItemLastResult {
    return runCatching { ExamItemLastResult.valueOf(value) }.getOrDefault(ExamItemLastResult.UNGRADED)
}
