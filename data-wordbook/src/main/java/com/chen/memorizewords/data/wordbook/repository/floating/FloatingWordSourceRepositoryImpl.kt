package com.chen.memorizewords.data.wordbook.repository.floating

import androidx.room.withTransaction
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordDao
import com.chen.memorizewords.data.wordbook.repository.wordbook.chunkedSql
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceKey
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceSnapshot
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
import com.chen.memorizewords.domain.floating.repository.FloatingWordSourceRepository
import javax.inject.Inject

class FloatingWordSourceRepositoryImpl @Inject constructor(
    private val database: WordBookDatabase,
    private val currentSelectionDao: CurrentWordBookSelectionDao,
    private val learningStateDao: WordLearningStateDao,
    private val wordDao: WordDao
) : FloatingWordSourceRepository {

    override suspend fun loadSnapshot(
        settings: FloatingWordSettings
    ): FloatingWordSourceSnapshot = database.withTransaction {
        when (settings.sourceType) {
            FloatingWordSourceType.CURRENT_BOOK -> loadCurrentBookSnapshot(settings.orderType)
            FloatingWordSourceType.SELF_SELECT -> loadSelfSelectSnapshot(settings)
        }
    }

    private suspend fun loadCurrentBookSnapshot(
        orderType: FloatingWordOrderType
    ): FloatingWordSourceSnapshot {
        val bookId = currentSelectionDao.getById()?.bookId?.takeIf { it > 0L }
        if (bookId == null) {
            return FloatingWordSourceSnapshot(
                sourceKey = FloatingWordSourceKey.CurrentBook(null),
                orderType = orderType,
                wordIds = emptyList()
            )
        }

        val reviewableIds = normalizeFloatingWordSourceIds(
            learningStateDao.getLearnedWordIdsByBook(bookId)
        )
        val orderedIds = when (orderType) {
            FloatingWordOrderType.RANDOM,
            FloatingWordOrderType.MEMORY_CURVE -> reviewableIds

            else -> orderFloatingWordSourceIds(
                wordIds = reviewableIds,
                orderType = orderType,
                wordsById = loadWordSortValues(reviewableIds)
            )
        }
        return FloatingWordSourceSnapshot(
            sourceKey = FloatingWordSourceKey.CurrentBook(bookId),
            orderType = orderType,
            wordIds = orderedIds
        )
    }

    private suspend fun loadSelfSelectSnapshot(
        settings: FloatingWordSettings
    ): FloatingWordSourceSnapshot {
        val requestedIds = normalizeFloatingWordSourceIds(settings.selectedWordIds)
        val wordsById = loadWordSortValues(requestedIds)
        val existingIds = requestedIds.filter(wordsById::containsKey)
        val currentBookId = if (settings.orderType == FloatingWordOrderType.MEMORY_CURVE) {
            currentSelectionDao.getById()?.bookId?.takeIf { it > 0L }
        } else {
            null
        }
        val learningById = if (currentBookId == null) {
            emptyMap()
        } else {
            existingIds.chunkedSql()
                .flatMap { ids -> learningStateDao.getLearningStatesByIds(currentBookId, ids) }
                .associate { state ->
                    state.wordId to FloatingWordLearningSortValue(
                        nextReviewAtMs = state.nextReviewAtMs,
                        lastLearnedAtMs = state.lastLearnedAtMs
                    )
                }
        }
        val orderedIds = if (
            settings.orderType == FloatingWordOrderType.MEMORY_CURVE && currentBookId == null
        ) {
            existingIds
        } else {
            orderFloatingWordSourceIds(
                wordIds = existingIds,
                orderType = settings.orderType,
                wordsById = wordsById,
                learningById = learningById
            )
        }
        return FloatingWordSourceSnapshot(
            sourceKey = FloatingWordSourceKey.SelfSelect(requestedIds),
            orderType = settings.orderType,
            wordIds = orderedIds
        )
    }

    private suspend fun loadWordSortValues(
        wordIds: List<Long>
    ): Map<Long, FloatingWordSortValue> {
        return wordIds.chunkedSql()
            .flatMap { ids -> wordDao.getByIds(ids) }
            .associate { word ->
                word.id to FloatingWordSortValue(
                    word = word.word,
                    normalizedWord = word.normalizedWord
                )
            }
    }
}

internal data class FloatingWordSortValue(
    val word: String,
    val normalizedWord: String
)

internal data class FloatingWordLearningSortValue(
    val nextReviewAtMs: Long,
    val lastLearnedAtMs: Long
)

internal fun normalizeFloatingWordSourceIds(wordIds: List<Long>): List<Long> {
    return wordIds.asSequence().filter { it > 0L }.distinct().toList()
}

internal fun orderFloatingWordSourceIds(
    wordIds: List<Long>,
    orderType: FloatingWordOrderType,
    wordsById: Map<Long, FloatingWordSortValue>,
    learningById: Map<Long, FloatingWordLearningSortValue> = emptyMap()
): List<Long> {
    val existingIds = normalizeFloatingWordSourceIds(wordIds).filter(wordsById::containsKey)
    return when (orderType) {
        FloatingWordOrderType.RANDOM -> existingIds
        FloatingWordOrderType.MEMORY_CURVE -> existingIds.sortedWith(
            compareBy<Long>(
                { learningById[it]?.nextReviewAtMs ?: Long.MAX_VALUE },
                { learningById[it]?.lastLearnedAtMs ?: Long.MAX_VALUE },
                { it }
            )
        )

        FloatingWordOrderType.ALPHABETIC_ASC ->
            existingIds.sortedBy { wordsById.getValue(it).normalizedWord }

        FloatingWordOrderType.ALPHABETIC_DESC ->
            existingIds.sortedByDescending { wordsById.getValue(it).normalizedWord }

        FloatingWordOrderType.LENGTH_ASC ->
            existingIds.sortedBy { wordsById.getValue(it).word.length }

        FloatingWordOrderType.LENGTH_DESC ->
            existingIds.sortedByDescending { wordsById.getValue(it).word.length }
    }
}
