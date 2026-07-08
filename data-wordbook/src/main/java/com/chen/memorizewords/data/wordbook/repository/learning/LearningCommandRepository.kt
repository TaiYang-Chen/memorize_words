package com.chen.memorizewords.data.wordbook.repository.learning

import com.chen.memorizewords.data.wordbook.local.room.model.learning.event.LearningEventDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.event.LearningEventEntity
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxEntity
import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.record.WordStudyRecordEntity
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.BookWordItemDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.definition.WordDefinitionDao
import com.chen.memorizewords.data.wordbook.repository.WordBookTransactionRunner
import com.chen.memorizewords.domain.study.model.learning.LearningEventAction
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventCommand
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventResult
import com.chen.memorizewords.domain.study.model.progress.word.calculateSm2Review
import com.chen.memorizewords.domain.study.repository.learning.LearningCommandPort
import com.chen.memorizewords.domain.sync.LearningEventSyncPayload
import com.google.gson.Gson
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class LearningCommandRepository @Inject constructor(
    private val transactionRunner: WordBookTransactionRunner,
    private val learningEventDao: LearningEventDao,
    private val learningOutboxDao: LearningOutboxDao,
    private val wordStudyRecordDao: WordStudyRecordDao,
    private val wordLearningStateDao: WordLearningStateDao,
    private val wordBookProgressDao: WordBookProgressDao,
    private val currentWordBookSelectionDao: CurrentWordBookSelectionDao,
    private val wordBookDao: WordBookDao,
    private val bookWordItemDao: BookWordItemDao,
    private val wordDefinitionDao: WordDefinitionDao,
    private val gson: Gson
) : LearningCommandPort {

    override suspend fun record(command: RecordLearningEventCommand): RecordLearningEventResult {
        require(command.bookId > 0L) { "bookId must be positive" }
        require(command.word.id > 0L) { "wordId must be positive" }
        return transactionRunner.runInTransaction {
            recordInTransaction(command)
        }
    }

    private suspend fun recordInTransaction(command: RecordLearningEventCommand): RecordLearningEventResult {
        val currentBookId = currentWordBookSelectionDao.getById()?.bookId
            ?: throw IllegalStateException("current word book is not selected")
        check(currentBookId == command.bookId) {
            "learning event book ${command.bookId} is not current word book $currentBookId"
        }
        wordBookDao.getWordBookById(command.bookId)
            ?: throw NoSuchElementException("word book not found: ${command.bookId}")
        check(bookWordItemDao.existsWordInBook(command.bookId, command.word.id)) {
            "word ${command.word.id} is not in word book ${command.bookId}"
        }
        val previous = wordLearningStateDao.getState(command.word.id, command.bookId)
        val baseRevision = previous?.stateRevision ?: 0L
        val clientSequence = learningEventDao.getMaxClientSequence() + 1L
        val clientEventId = UUID.randomUUID().toString()
        val next = buildNextState(command, previous, clientEventId)

        val beforeStateJson = previous?.let(gson::toJson)
        val afterStateJson = next?.let(gson::toJson) ?: beforeStateJson
        learningEventDao.insert(
            LearningEventEntity(
                clientEventId = clientEventId,
                clientSequence = clientSequence,
                bookId = command.bookId,
                wordId = command.word.id,
                action = command.action.name,
                quality = command.quality,
                correct = command.correct,
                businessDate = command.businessDate,
                occurredAt = command.occurredAt,
                baseStateRevision = baseRevision,
                beforeStateJson = beforeStateJson,
                afterStateJson = afterStateJson,
                payloadJson = command.payloadJson
            )
        )
        if (next != null && command.action.changesWordState()) {
            wordLearningStateDao.upsert(next)
        }
        if (command.action.createsStudyRecord()) {
            wordStudyRecordDao.upsert(
                WordStudyRecordEntity(
                    date = command.businessDate,
                    word = command.word.word,
                    wordId = command.word.id,
                    definition = resolveDefinition(command.word.id),
                    isNewWord = command.isNewWordOverride ?: (previous == null)
                )
            )
        }
        val progressRevision = if (command.action.updatesProgress()) {
            updateProgress(command)
        } else {
            wordBookProgressDao.getProgress(command.bookId)?.revision ?: 0L
        }

        val payload = LearningEventSyncPayload(
            clientEventId = clientEventId,
            deviceId = null,
            clientSequence = clientSequence,
            bookId = command.bookId,
            wordId = command.word.id,
            action = command.action.name,
            quality = command.quality,
            correct = command.correct,
            businessDate = command.businessDate,
            occurredAt = command.occurredAt,
            baseStateRevision = baseRevision,
            payloadJson = command.payloadJson
        )
        learningOutboxDao.upsert(
            LearningOutboxEntity(
                clientEventId = clientEventId,
                bookId = command.bookId,
                wordId = command.word.id,
                payload = gson.toJson(payload)
            )
        )

        return RecordLearningEventResult(
            clientEventId = clientEventId,
            wordId = command.word.id,
            bookId = command.bookId,
            stateRevision = next?.stateRevision ?: baseRevision,
            progressRevision = progressRevision
        )
    }

    private fun buildNextState(
        command: RecordLearningEventCommand,
        previous: WordLearningStateEntity?,
        clientEventId: String
    ): WordLearningStateEntity? {
        if (!command.action.changesWordState()) return previous
        if (command.action == LearningEventAction.UNMASTERED && previous == null) {
            throw IllegalStateException("cannot unmaster unknown word")
        }
        val quality = resolveQuality(command.action, command.quality)
        val sm2 = calculateSm2Review(
            prevInterval = previous?.interval ?: 1L,
            prevEF = previous?.efactor ?: 2.5,
            prevRepetition = previous?.repetition ?: 0,
            quality = quality
        )
        val mastery = when (command.action) {
            LearningEventAction.MASTERED -> MASTERED_LEVEL
            LearningEventAction.UNMASTERED -> (previous?.masteryLevel ?: 0).coerceAtMost(MASTERED_LEVEL - 1)
            else -> sm2.mastery
        }
        val userStatus = if (command.action == LearningEventAction.MASTERED) {
            USER_STATUS_MASTERED
        } else {
            USER_STATUS_LEARNING
        }
        return WordLearningStateEntity(
            wordId = command.word.id,
            bookId = command.bookId,
            totalLearnCount = (previous?.totalLearnCount ?: 0) +
                if (command.action.incrementsLearnCount()) 1 else 0,
            lastLearnTime = command.occurredAt,
            nextReviewTime = command.occurredAt + TimeUnit.DAYS.toMillis(sm2.interval),
            masteryLevel = mastery,
            userStatus = userStatus,
            interval = sm2.interval,
            repetition = sm2.repetition,
            efactor = sm2.ef,
            stateRevision = (previous?.stateRevision ?: 0L) + 1L,
            lastEventId = clientEventId
        )
    }

    private suspend fun updateProgress(
        command: RecordLearningEventCommand
    ): Long {
        val existing = wordBookProgressDao.getProgress(command.bookId)
        val next = if (existing == null) {
            WordBookProgressEntity(
                wordBookId = command.bookId,
                correctCount = if (command.correct == true) 1 else 0,
                wrongCount = if (command.correct == false) 1 else 0,
                studyDayCount = 1,
                lastStudyDate = command.businessDate,
                revision = 1L
            )
        } else {
            existing.copy(
                correctCount = existing.correctCount + if (command.correct == true) 1 else 0,
                wrongCount = existing.wrongCount + if (command.correct == false) 1 else 0,
                studyDayCount = if (existing.lastStudyDate == command.businessDate) {
                    existing.studyDayCount
                } else {
                    existing.studyDayCount + 1
                },
                lastStudyDate = command.businessDate,
                revision = existing.revision + 1L
            )
        }
        wordBookProgressDao.upsert(next)
        return next.revision
    }

    private suspend fun resolveDefinition(wordId: Long): String {
        return wordDefinitionDao.getWordDefinitions(wordId)
            .joinToString("; ") { definition ->
                "${definition.partOfSpeech} ${definition.meaningChinese}".trim()
            }
    }

    private fun resolveQuality(action: LearningEventAction, quality: Int?): Int {
        return quality?.coerceIn(0, 5) ?: when (action) {
            LearningEventAction.MASTERED -> 5
            LearningEventAction.UNMASTERED,
            LearningEventAction.SKIPPED -> 0
            LearningEventAction.ANSWER_RECORDED,
            LearningEventAction.LEARNED,
            LearningEventAction.REVIEWED,
            LearningEventAction.PRACTICE_RESULT_APPLIED -> 3
        }
    }

    private fun LearningEventAction.changesWordState(): Boolean {
        return this != LearningEventAction.SKIPPED &&
            this != LearningEventAction.ANSWER_RECORDED
    }

    private fun LearningEventAction.incrementsLearnCount(): Boolean {
        return this == LearningEventAction.LEARNED ||
            this == LearningEventAction.REVIEWED ||
            this == LearningEventAction.MASTERED ||
            this == LearningEventAction.PRACTICE_RESULT_APPLIED
    }

    private fun LearningEventAction.createsStudyRecord(): Boolean {
        return incrementsLearnCount()
    }

    private fun LearningEventAction.updatesProgress(): Boolean {
        return this != LearningEventAction.SKIPPED
    }

    private companion object {
        const val USER_STATUS_LEARNING = 0
        const val USER_STATUS_MASTERED = 1
        const val MASTERED_LEVEL = 5
    }
}
