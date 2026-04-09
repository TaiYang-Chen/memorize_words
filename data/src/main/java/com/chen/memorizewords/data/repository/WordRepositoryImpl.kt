package com.chen.memorizewords.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.local.room.model.words.word.WordDao
import com.chen.memorizewords.data.local.room.model.words.word.toDomain
import com.chen.memorizewords.data.local.room.model.words.word.toAntonymEntities as wordToAntonymEntities
import com.chen.memorizewords.data.local.room.model.words.word.toAssociationEntities as wordToAssociationEntities
import com.chen.memorizewords.data.local.room.model.words.word.toEntity as wordToEntity
import com.chen.memorizewords.data.local.room.model.words.word.toSynonymEntities as wordToSynonymEntities
import com.chen.memorizewords.data.local.room.model.words.word.toTagEntities as wordToTagEntities
import com.chen.memorizewords.data.local.room.model.words.word.toUserMetaEntity as wordToUserMetaEntity
import com.chen.memorizewords.data.local.room.model.words.definition.WordDefinitionDao
import com.chen.memorizewords.data.local.room.model.words.definition.toDomain
import com.chen.memorizewords.data.local.room.model.words.definition.toEntity as definitionToEntity
import com.chen.memorizewords.data.local.room.model.words.example.WordExampleDao
import com.chen.memorizewords.data.local.room.model.words.example.toDomain
import com.chen.memorizewords.data.local.room.model.words.example.toEntity as exampleToEntity
import com.chen.memorizewords.data.local.room.model.words.form.WordFormDao
import com.chen.memorizewords.data.local.room.model.words.form.toDomain
import com.chen.memorizewords.data.local.room.model.words.form.toEntity as formToEntity
import com.chen.memorizewords.data.local.room.model.words.meta.WordUserMetaDao
import com.chen.memorizewords.data.local.room.model.words.relation.WordRelationDao
import com.chen.memorizewords.data.local.room.model.words.root.root.WordRootDao
import com.chen.memorizewords.data.local.room.model.words.root.root.toDomain as wordRootToDomain
import com.chen.memorizewords.data.local.room.model.words.root.root.toEntity as wordRootToEntity
import com.chen.memorizewords.data.local.room.model.words.root.rootword.RootWordDao
import com.chen.memorizewords.data.local.room.model.words.root.rootword.toRelationEntity
import com.chen.memorizewords.data.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.WordStateUpsertSyncPayload
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.domain.model.words.word.WordForm
import com.chen.memorizewords.domain.model.words.word.WordQuickLookupResult
import com.chen.memorizewords.domain.model.words.word.WordRoot
import com.chen.memorizewords.domain.repository.word.WordRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.round
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

class WordRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val appDatabase: AppDatabase,
    private val remoteWordBookDataSource: RemoteWordBookDataSource,
    private val wordFormDao: WordFormDao,
    private val wordRootDao: WordRootDao,
    private val rootWordDao: RootWordDao,
    private val wordDefinitionDao: WordDefinitionDao,
    private val wordExampleDao: WordExampleDao,
    private val wordRelationDao: WordRelationDao,
    private val wordUserMetaDao: WordUserMetaDao,
    private val stateDao: WordLearningStateDao,
    private val dao: WordDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : WordRepository {

    override suspend fun getWordsByIds(ids: List<Long>): List<Word> {
        return dao.getWithRelationsByIds(ids).map { it.toDomain() }
    }

    override suspend fun getWordById(wordId: Long): Word? {
        return dao.getWordWithRelationsById(wordId)?.toDomain()
    }

    override suspend fun getWordForms(wordId: Long): List<WordForm> {
        return wordFormDao.getFormsByWordId(wordId).map { it.toDomain() }
    }

    override suspend fun getRootWordByWordId(wordId: Long): List<WordRoot> {
        return resolveWordRoots(wordId)
    }

    override suspend fun getWordExamples(wordId: Long): List<WordExample> {
        return wordExampleDao.getExamplesByWordId(wordId).map { it.toDomain() }
    }

    override suspend fun getWordDefinitions(wordId: Long): List<WordDefinitions> {
        return wordDefinitionDao.getWordDefinitions(wordId).map { it.toDomain() }
    }

    override suspend fun getRandomDefinition(wordId: Long): WordDefinitions {
        return wordDefinitionDao.getRandomDefinition(wordId).toDomain()
    }

    override suspend fun getRandomDefinitionsByPos(
        wordId: Long,
        limit: Int
    ): List<WordDefinitions> {
        return wordDefinitionDao.getRandomDistractorsByPos(wordId, limit).map { it.toDomain() }
    }

    override suspend fun updateWordStatus(
        bookId: Long,
        word: Word,
        quality: Int
    ): Boolean {
        val state = stateDao.getState(word.id, bookId)
        val isNewWord = state == null
        val prevInterval = state?.interval ?: 1L
        val prevEFactor = state?.efactor ?: 2.5
        val prevRepetition = state?.repetition ?: 0
        val result = sm2Calculate(prevInterval, prevEFactor, prevRepetition, quality)
        val now = System.currentTimeMillis()
        val nextReviewTime = now + TimeUnit.DAYS.toMillis(result.interval)
        val newTotalLearnCount = (state?.totalLearnCount ?: 0) + 1

        withContext(Dispatchers.IO) {
            appDatabase.withTransaction {
                if (state == null) {
                    stateDao.insertInitialState(
                        WordLearningStateEntity(
                            wordId = word.id,
                            bookId = bookId,
                            totalLearnCount = newTotalLearnCount,
                            lastLearnTime = now,
                            nextReviewTime = nextReviewTime,
                            masteryLevel = result.mastery,
                            userStatus = 0,
                            repetition = result.repetition,
                            interval = result.interval,
                            efactor = result.ef
                        )
                    )
                } else {
                    stateDao.updateAfterLearn(
                        bookId = bookId,
                        wordId = word.id,
                        totalLearnCount = newTotalLearnCount,
                        learnTime = now,
                        nextReviewTime = nextReviewTime,
                        masteryLevel = result.mastery,
                        userStatus = 0,
                        repetition = result.repetition,
                        interval = result.interval,
                        efactor = result.ef
                    )
                }
            }
        }

        enqueueWordStateUpsertSync(
            bookId = bookId,
            wordId = word.id,
            totalLearnCount = newTotalLearnCount,
            lastLearnTime = now,
            nextReviewTime = nextReviewTime,
            masteryLevel = result.mastery,
            userStatus = 0,
            repetition = result.repetition,
            interval = result.interval,
            efactor = result.ef
        )

        return isNewWord
    }

    override suspend fun setWordAsMastered(
        bookId: Long,
        word: Word
    ) {
        val state = stateDao.getState(word.id, bookId)
        val prevInterval = state?.interval ?: 1L
        val prevEFactor = state?.efactor ?: 2.5
        val prevRepetition = state?.repetition ?: 0
        val result = sm2Calculate(prevInterval, prevEFactor, prevRepetition, 5)
        val now = System.currentTimeMillis()
        val nextReviewTime = now + TimeUnit.DAYS.toMillis(result.interval)
        val newTotalLearnCount = (state?.totalLearnCount ?: 0) + 1

        withContext(Dispatchers.IO) {
            appDatabase.withTransaction {
                if (state == null) {
                    stateDao.insertInitialState(
                        WordLearningStateEntity(
                            wordId = word.id,
                            bookId = bookId,
                            totalLearnCount = newTotalLearnCount,
                            lastLearnTime = now,
                            nextReviewTime = nextReviewTime,
                            masteryLevel = result.mastery,
                            userStatus = 1,
                            repetition = result.repetition,
                            interval = result.interval,
                            efactor = result.ef
                        )
                    )
                } else {
                    stateDao.updateAfterLearn(
                        bookId = bookId,
                        wordId = word.id,
                        totalLearnCount = newTotalLearnCount,
                        learnTime = now,
                        nextReviewTime = nextReviewTime,
                        masteryLevel = result.mastery,
                        userStatus = 1,
                        repetition = result.repetition,
                        interval = result.interval,
                        efactor = result.ef
                    )
                }
            }
        }

        enqueueWordStateUpsertSync(
            bookId = bookId,
            wordId = word.id,
            totalLearnCount = newTotalLearnCount,
            lastLearnTime = now,
            nextReviewTime = nextReviewTime,
            masteryLevel = result.mastery,
            userStatus = 1,
            repetition = result.repetition,
            interval = result.interval,
            efactor = result.ef
        )
    }

    override suspend fun getWordByWordString(word: String): Word? {
        return dao.getWordWithRelationsByWordString(word)?.toDomain()
    }

    override suspend fun lookupWordQuick(
        normalizedWord: String,
        rawWord: String
    ): WordQuickLookupResult {
        return withContext(Dispatchers.IO) {
            if (normalizedWord.isBlank() && rawWord.isBlank()) {
                return@withContext WordQuickLookupResult(
                    status = WordQuickLookupResult.Status.MISSING,
                    queryRawWord = rawWord,
                    normalizedWord = normalizedWord
                )
            }

            val localWord = resolveLocalWord(rawWord, normalizedWord)
            if (localWord != null) {
                return@withContext buildFoundResult(localWord, rawWord, normalizedWord, false)
            }

            val remoteWordDto = remoteWordBookDataSource.lookupWord(rawWord, normalizedWord).getOrNull()
            if (remoteWordDto != null) {
                appDatabase.withTransaction {
                    dao.insert(remoteWordDto.wordToEntity())
                    wordDefinitionDao.insert(remoteWordDto.definitionDtos.map { it.definitionToEntity() })
                    wordExampleDao.insert(remoteWordDto.exampleDtos.map { it.exampleToEntity() })
                    wordFormDao.insert(remoteWordDto.wordFormDtos.map { it.formToEntity() })
                    wordRootDao.insertRoots(remoteWordDto.rootWords.map { it.wordRootToEntity() })
                    rootWordDao.deleteByWordId(remoteWordDto.id)
                    rootWordDao.insert(
                        remoteWordDto.rootWords.mapIndexed { index, root ->
                            root.toRelationEntity(wordId = remoteWordDto.id, sequence = index + 1)
                        }
                    )

                    val synonyms = remoteWordDto.wordToSynonymEntities()
                    val antonyms = remoteWordDto.wordToAntonymEntities()
                    val tags = remoteWordDto.wordToTagEntities()
                    val associations = remoteWordDto.wordToAssociationEntities()

                    wordRelationDao.deleteSynonymsByWordId(remoteWordDto.id)
                    wordRelationDao.deleteAntonymsByWordId(remoteWordDto.id)
                    wordRelationDao.deleteTagsByWordId(remoteWordDto.id)
                    wordRelationDao.deleteAssociationsByWordId(remoteWordDto.id)
                    if (synonyms.isNotEmpty()) wordRelationDao.insertSynonyms(synonyms)
                    if (antonyms.isNotEmpty()) wordRelationDao.insertAntonyms(antonyms)
                    if (tags.isNotEmpty()) wordRelationDao.insertTags(tags)
                    if (associations.isNotEmpty()) wordRelationDao.insertAssociations(associations)

                    wordUserMetaDao.upsert(remoteWordDto.wordToUserMetaEntity())
                }
                val inserted = dao.getWordWithRelationsById(remoteWordDto.id)?.toDomain()
                    ?: remoteWordDto.wordToEntity().let { _ ->
                        Word(
                            id = remoteWordDto.id,
                            word = remoteWordDto.word,
                            normalizedWord = remoteWordDto.normalizedWord,
                            phoneticUS = remoteWordDto.phoneticUS,
                            phoneticUK = remoteWordDto.phoneticUK,
                            hasIrregularForms = remoteWordDto.hasIrregularForms,
                            memoryTip = remoteWordDto.memoryTip,
                            mnemonicImageUrl = remoteWordDto.mnemonicImageUrl,
                            memoryAssociations = remoteWordDto.memoryAssociations,
                            wordFamily = remoteWordDto.wordFamily,
                            synonyms = remoteWordDto.synonyms,
                            antonyms = remoteWordDto.antonyms,
                            tags = remoteWordDto.tags,
                            notes = remoteWordDto.notes,
                            rootMemoryTip = remoteWordDto.rootMemoryTip
                        )
                    }
                return@withContext buildFoundResult(inserted, rawWord, normalizedWord, true)
            }

            WordQuickLookupResult(
                status = WordQuickLookupResult.Status.MISSING,
                queryRawWord = rawWord,
                normalizedWord = normalizedWord
            )
        }
    }

    private suspend fun resolveLocalWord(rawWord: String, normalizedWord: String): Word? {
        dao.getWordWithRelationsByWordString(rawWord)?.toDomain()?.let { return it }
        dao.getWordWithRelationsByNormalizedWord(normalizedWord)?.toDomain()?.let { return it }
        val fromForm = wordFormDao.getByFormText(normalizedWord).firstOrNull() ?: return null
        return dao.getWordWithRelationsById(fromForm.wordId)?.toDomain()
    }

    private suspend fun buildFoundResult(
        word: Word,
        rawWord: String,
        normalizedWord: String,
        fromNetwork: Boolean
    ): WordQuickLookupResult {
        return WordQuickLookupResult(
            status = WordQuickLookupResult.Status.FOUND,
            queryRawWord = rawWord,
            normalizedWord = normalizedWord,
            word = word,
            definitions = wordDefinitionDao.getWordDefinitions(word.id).map { it.toDomain() },
            examples = wordExampleDao.getExamplesByWordId(word.id).map { it.toDomain() },
            forms = wordFormDao.getFormsByWordId(word.id).map { it.toDomain() },
            roots = resolveWordRoots(word.id),
            fromNetwork = fromNetwork
        )
    }

    private suspend fun resolveWordRoots(wordId: Long): List<WordRoot> {
        val relations = rootWordDao.getRootsForWordId(wordId)
        if (relations.isEmpty()) return emptyList()

        val rootsById = wordRootDao.getWordRootsByIds(relations.map { it.rootId }.distinct())
            .associateBy { it.id }
        return relations.mapNotNull { relation ->
            rootsById[relation.rootId]?.wordRootToDomain()
        }
    }

    private suspend fun enqueueWordStateUpsertSync(
        bookId: Long,
        wordId: Long,
        totalLearnCount: Int,
        lastLearnTime: Long,
        nextReviewTime: Long,
        masteryLevel: Int,
        userStatus: Int,
        repetition: Int,
        interval: Long,
        efactor: Double
    ) {
        val payload = WordStateUpsertSyncPayload(
            bookId = bookId,
            wordId = wordId,
            totalLearnCount = totalLearnCount,
            lastLearnTime = lastLearnTime,
            nextReviewTime = nextReviewTime,
            masteryLevel = masteryLevel,
            userStatus = userStatus,
            repetition = repetition,
            interval = interval,
            efactor = efactor
        )
        syncOutboxDao.upsert(
            syncOutboxEntity(
                bizType = SyncOutboxBizType.WORD_STATE_UPSERT,
                bizKey = "word_state:$bookId:$wordId",
                operation = SyncOutboxOperation.UPSERT,
                payload = gson.toJson(payload)
            )
        )
        syncOutboxWorkScheduler.scheduleDrain()
    }
}

data class Sm2Result(
    val interval: Long,
    val ef: Double,
    val repetition: Int,
    val mastery: Int
)

fun sm2Calculate(
    prevInterval: Long,
    prevEF: Double,
    prevRepetition: Int,
    q: Int
): Sm2Result {
    val quality = q.coerceIn(0, 5)
    var ef = prevEF
    ef += 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)
    if (ef < 1.3) ef = 1.3

    var repetition = prevRepetition
    val interval: Long = if (quality < 3) {
        repetition = 0
        1L
    } else {
        repetition++
        when (repetition) {
            1 -> 1L
            2 -> 6L
            else -> {
                val raw = round(prevInterval * ef)
                max(1L, raw.toLong())
            }
        }
    }

    val mastery = when {
        repetition >= 6 && ef >= 2.6 -> 5
        repetition >= 5 -> 4
        repetition >= 3 -> 3
        repetition >= 2 -> 2
        repetition >= 1 -> 1
        else -> 0
    }

    return Sm2Result(interval, ef, repetition, mastery)
}
