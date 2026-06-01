package com.chen.memorizewords.data.wordbook.repository

import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.toDomain
import com.chen.memorizewords.data.wordbook.local.room.model.words.definition.WordDefinitionDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.definition.WordDefinitionEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.example.WordExampleDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.example.WordExampleEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.form.WordFormDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.form.WordFormEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.meta.WordUserMetaDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.meta.WordUserMetaEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordAntonymEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordAssociationEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordRelationDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordSynonymEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.relation.WordTagEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.RootTagDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.RootTagEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.WordRootDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.WordRootEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootword.RootWordDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootword.RootWordEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordWithRelations
import com.chen.memorizewords.data.wordbook.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.domain.study.model.favorites.WordFavorites
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DailyStudyRecords
import com.chen.memorizewords.domain.study.repository.StudyDailyDurationSnapshot
import com.chen.memorizewords.domain.study.repository.StudySnapshotLocalStatePort
import com.chen.memorizewords.domain.study.repository.WordLearningStateStore
import com.chen.memorizewords.domain.sync.OutboxCommand
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.sync.WordStateUpsertSyncPayload
import com.chen.memorizewords.domain.word.model.word.Word
import com.google.gson.Gson
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordRepositoryImplTest {

    private val gson = Gson()

    @Test
    fun getWordsByIds_returnsWordsFromWordBookAndPreservesRequestedOrder() = runBlocking {
        val studyGateway = InMemoryStudyGateway()
        val wordBookStateDao = InMemoryWordLearningStateDao()
        val mirror = createMirror(studyGateway, wordBookStateDao)
        val repository = createRepository(
            wordDao = FakeWordDao(
                words = listOf(
                    wordWithRelations(id = 2L, word = "banana"),
                    wordWithRelations(id = 1L, word = "apple")
                )
            ),
            mirror = mirror
        )

        val words = repository.getWordsByIds(listOf(1L, 2L))

        assertEquals(listOf(1L, 2L), words.map(Word::id))
        assertEquals(listOf("apple", "banana"), words.map(Word::word))
    }

    @Test
    fun updateWordStatus_dualWritesState_andEnqueuesSingleOutboxRecord() = runBlocking {
        val studyGateway = InMemoryStudyGateway()
        val wordBookStateDao = InMemoryWordLearningStateDao()
        val mirror = createMirror(studyGateway, wordBookStateDao)
        val syncWriter = RecordingSyncOutboxWriter()
        val repository = createRepository(
            mirror = mirror,
            syncOutboxWriter = syncWriter
        )
        val word = sampleWord(id = 10L, word = "anchor")

        val isNewWord = repository.updateWordStatus(bookId = 100L, word = word, quality = 4)

        assertTrue(isNewWord)
        val studyState = studyGateway.getState(wordId = 10L, bookId = 100L)
        val wordBookState = wordBookStateDao.getState(wordId = 10L, bookId = 100L)
        assertNotNull(studyState)
        assertNotNull(wordBookState)
        assertEquals(studyState, wordBookState?.toDomain())
        assertEquals(1, syncWriter.commands.size)
        val command = syncWriter.commands.single()
        assertEquals(OutboxTopic.WORD_STATE_UPSERT, command.topic)
        assertEquals(SyncOperation.UPSERT, command.operation)
        val payload = gson.fromJson(command.payload, WordStateUpsertSyncPayload::class.java)
        assertEquals(100L, payload.bookId)
        assertEquals(10L, payload.wordId)
        assertEquals(0, payload.userStatus)
    }

    @Test
    fun setWordAsMastered_usesWordBookFallbackState_andUpdatesBothStores() = runBlocking {
        val studyGateway = InMemoryStudyGateway()
        val wordBookStateDao = InMemoryWordLearningStateDao().apply {
            upsert(
                WordLearningStateEntity(
                    wordId = 10L,
                    bookId = 100L,
                    totalLearnCount = 2,
                    lastLearnTime = 10L,
                    nextReviewTime = 20L,
                    masteryLevel = 1,
                    userStatus = 0,
                    repetition = 1,
                    interval = 1L,
                    efactor = 2.5
                )
            )
        }
        val mirror = createMirror(studyGateway, wordBookStateDao)
        val syncWriter = RecordingSyncOutboxWriter()
        val repository = createRepository(
            mirror = mirror,
            syncOutboxWriter = syncWriter
        )

        repository.setWordAsMastered(bookId = 100L, word = sampleWord(id = 10L, word = "anchor"))

        val studyState = studyGateway.getState(wordId = 10L, bookId = 100L)
        val wordBookState = wordBookStateDao.getState(wordId = 10L, bookId = 100L)?.toDomain()
        assertNotNull(studyState)
        assertEquals(studyState, wordBookState)
        assertEquals(1, studyState?.userStatus)
        assertEquals(3, studyState?.totalLearnCount)
        assertEquals(1, syncWriter.commands.size)
    }

    @Test
    fun overwriteLearningStatesForBookFromRemote_replacesStudyAndWordBookState() = runBlocking {
        val studyGateway = InMemoryStudyGateway().apply {
            upsertState(
                WordLearningState(
                    wordId = 1L,
                    bookId = 100L,
                    totalLearnCount = 1
                )
            )
        }
        val wordBookStateDao = InMemoryWordLearningStateDao().apply {
            upsert(
                WordLearningStateEntity(
                    wordId = 1L,
                    bookId = 100L,
                    totalLearnCount = 1,
                    lastLearnTime = 0L,
                    nextReviewTime = 0L,
                    masteryLevel = 0,
                    userStatus = 0,
                    repetition = 0,
                    interval = 0L,
                    efactor = 2.5
                )
            )
        }
        val mirror = createMirror(studyGateway, wordBookStateDao)
        val remoteStates = listOf(
            WordLearningState(
                wordId = 2L,
                bookId = 100L,
                totalLearnCount = 4,
                lastLearnTime = 40L,
                nextReviewTime = 50L,
                masteryLevel = 3,
                userStatus = 0,
                repetition = 3,
                interval = 6L,
                efactor = 2.6
            )
        )

        mirror.overwriteLearningStatesForBookFromRemote(bookId = 100L, states = remoteStates)

        assertNull(studyGateway.getState(wordId = 1L, bookId = 100L))
        assertNull(wordBookStateDao.getState(wordId = 1L, bookId = 100L))
        assertEquals(remoteStates.single(), studyGateway.getState(wordId = 2L, bookId = 100L))
        assertEquals(remoteStates.single(), wordBookStateDao.getState(wordId = 2L, bookId = 100L)?.toDomain())
    }

    private fun createMirror(
        studyGateway: InMemoryStudyGateway,
        wordBookStateDao: InMemoryWordLearningStateDao
    ): WordLearningStateMirror {
        return WordLearningStateMirror(
            wordBookStateDao = wordBookStateDao,
            studyStateStore = studyGateway,
            studySnapshotLocalStatePort = studyGateway,
            transactionRunner = ImmediateTransactionRunner()
        )
    }

    private fun createRepository(
        wordDao: WordDao = FakeWordDao(),
        mirror: WordLearningStateMirror,
        syncOutboxWriter: RecordingSyncOutboxWriter = RecordingSyncOutboxWriter()
    ): WordRepositoryImpl {
        return WordRepositoryImpl(
            remoteWordBookDataSource = FakeRemoteWordBookDataSource(),
            wordFormDao = EmptyWordFormDao(),
            wordRootDao = EmptyWordRootDao(),
            rootTagDao = EmptyRootTagDao(),
            rootWordDao = EmptyRootWordDao(),
            wordDefinitionDao = EmptyWordDefinitionDao(),
            wordExampleDao = EmptyWordExampleDao(),
            wordRelationDao = EmptyWordRelationDao(),
            wordUserMetaDao = EmptyWordUserMetaDao(),
            wordLearningStateMirror = mirror,
            wordDao = wordDao,
            transactionRunner = ImmediateTransactionRunner(),
            syncOutboxWriter = syncOutboxWriter,
            gson = gson
        )
    }

    private fun sampleWord(id: Long, word: String) = Word(
        id = id,
        word = word,
        normalizedWord = word.lowercase(),
        phoneticUS = null,
        phoneticUK = null,
        hasIrregularForms = false,
        memoryTip = null,
        mnemonicImageUrl = null,
        memoryAssociations = emptyList(),
        wordFamily = null,
        synonyms = emptyList(),
        antonyms = emptyList(),
        tags = emptyList(),
        notes = null,
        rootMemoryTip = null
    )

    private fun wordWithRelations(id: Long, word: String): WordWithRelations {
        return WordWithRelations(
            word = WordEntity(
                id = id,
                word = word,
                normalizedWord = word.lowercase()
            ),
            synonyms = emptyList(),
            antonyms = emptyList(),
            tags = emptyList(),
            associations = emptyList(),
            userMeta = null
        )
    }
}

private class ImmediateTransactionRunner : WordBookTransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}

private class RecordingSyncOutboxWriter : SyncOutboxWriter {
    val commands = mutableListOf<OutboxCommand>()

    override suspend fun enqueueLatest(command: OutboxCommand) {
        commands += command
    }
}

private class InMemoryStudyGateway : WordLearningStateStore, StudySnapshotLocalStatePort {
    private val states = linkedMapOf<Pair<Long, Long>, WordLearningState>()

    override suspend fun getState(wordId: Long, bookId: Long): WordLearningState? {
        return states[wordId to bookId]
    }

    override suspend fun upsertState(state: WordLearningState) {
        states[state.wordId to state.bookId] = state
    }

    override suspend fun overwriteFavoritesFromRemote(favorites: List<WordFavorites>) = Unit

    override suspend fun overwriteLearningStatesForBookFromRemote(
        bookId: Long,
        states: List<WordLearningState>
    ) {
        this.states.keys
            .filter { (_, candidateBookId) -> candidateBookId == bookId }
            .toList()
            .forEach(this.states::remove)
        states.forEach { state ->
            this.states[state.wordId to state.bookId] = state
        }
    }

    override suspend fun overwriteStudyRecordsFromRemote(records: List<DailyStudyRecords>) = Unit

    override suspend fun overwriteDailyDurationsFromRemote(durations: List<StudyDailyDurationSnapshot>) = Unit

    override suspend fun overwriteCheckInRecordsFromRemote(records: List<CheckInRecord>) = Unit
}

private class InMemoryWordLearningStateDao : WordLearningStateDao {
    private val states = linkedMapOf<Pair<Long, Long>, WordLearningStateEntity>()

    override suspend fun insertInitialState(state: WordLearningStateEntity) {
        states.putIfAbsent(state.wordId to state.bookId, state)
    }

    override suspend fun upsert(state: WordLearningStateEntity) {
        states[state.wordId to state.bookId] = state
    }

    override suspend fun upsertAll(states: List<WordLearningStateEntity>) {
        states.forEach { state -> upsert(state) }
    }

    override suspend fun getState(wordId: Long, bookId: Long): WordLearningStateEntity? {
        return states[wordId to bookId]
    }

    override suspend fun getWordsByWordBookId(bookId: Long): List<WordLearningStateEntity> {
        return states.values.filter { it.bookId == bookId }
    }

    override suspend fun getWordCountByBookId(bookId: Long): Int {
        return getWordsByWordBookId(bookId).size
    }

    override suspend fun getLearnedCountByBookId(bookId: Long): Int {
        return getWordsByWordBookId(bookId).count { it.userStatus == 0 }
    }

    override suspend fun getMasteredCountByBookId(bookId: Long): Int {
        return getWordsByWordBookId(bookId).count { it.userStatus == 1 }
    }

    override fun getStudyTotalWordCount(): Flow<Int> = flowOf(states.size)

    override suspend fun deleteLearningWordByBookId(bookId: Long) {
        states.keys
            .filter { (_, candidateBookId) -> candidateBookId == bookId }
            .toList()
            .forEach(states::remove)
    }

    override suspend fun deleteByBookIdAndWordIds(bookId: Long, wordIds: List<Long>) {
        wordIds.forEach { wordId -> states.remove(wordId to bookId) }
    }

    override suspend fun getLearningStatesByIds(
        wordBookId: Long,
        ids: List<Long>
    ): List<WordLearningStateEntity> {
        return ids.mapNotNull { id -> states[id to wordBookId] }
    }

    override suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long> {
        return getWordsByWordBookId(bookId)
            .sortedWith(compareBy(WordLearningStateEntity::nextReviewTime, WordLearningStateEntity::lastLearnTime, WordLearningStateEntity::wordId))
            .map(WordLearningStateEntity::wordId)
    }

    override suspend fun updateAfterLearn(
        bookId: Long,
        wordId: Long,
        totalLearnCount: Int,
        learnTime: Long,
        nextReviewTime: Long,
        masteryLevel: Int,
        userStatus: Int,
        repetition: Int,
        interval: Long,
        efactor: Double
    ) {
        upsert(
            WordLearningStateEntity(
                wordId = wordId,
                bookId = bookId,
                totalLearnCount = totalLearnCount,
                lastLearnTime = learnTime,
                nextReviewTime = nextReviewTime,
                masteryLevel = masteryLevel,
                userStatus = userStatus,
                repetition = repetition,
                interval = interval,
                efactor = efactor
            )
        )
    }

    override suspend fun deleteAll() {
        states.clear()
    }
}

private class FakeWordDao(
    private val words: List<WordWithRelations> = emptyList()
) : WordDao {
    override suspend fun getByIds(ids: List<Long>): List<WordEntity> {
        return words.map(WordWithRelations::word).filter { it.id in ids }
    }

    override suspend fun getWithRelationsByIds(ids: List<Long>): List<WordWithRelations> {
        return words.filter { it.word.id in ids }
    }

    override suspend fun insert(word: WordEntity): Long = word.id

    override suspend fun insertAll(words: List<WordEntity>) = Unit

    override suspend fun update(word: WordEntity) = Unit

    override suspend fun delete(word: WordEntity) = Unit

    override fun getWordById(id: Long): WordEntity? = words.firstOrNull { it.word.id == id }?.word

    override suspend fun getWordWithRelationsById(id: Long): WordWithRelations? {
        return words.firstOrNull { it.word.id == id }
    }

    override suspend fun getWordWithRelationsByWordString(word: String): WordWithRelations? {
        return words.firstOrNull { it.word.word == word }
    }

    override suspend fun getWordWithRelationsByNormalizedWord(normalizedWord: String): WordWithRelations? {
        return words.firstOrNull { it.word.normalizedWord == normalizedWord }
    }
}

private class EmptyWordDefinitionDao : WordDefinitionDao {
    override suspend fun insert(definition: WordDefinitionEntity) = Unit
    override suspend fun insert(definition: List<WordDefinitionEntity>) = Unit
    override suspend fun delete(definition: WordDefinitionEntity) = Unit
    override fun getDefinitionsForWordId(wordId: Long): Flow<List<WordDefinitionEntity>> = emptyFlow()
    override suspend fun deleteByWordId(wordId: Long) = Unit
    override suspend fun update(definition: WordDefinitionEntity) = Unit
    override suspend fun insertWordDefinitions(wordDefinitions: List<WordDefinitionEntity>) = Unit
    override suspend fun getWordDefinitions(wordId: Long): List<WordDefinitionEntity> = emptyList()
    override suspend fun getOneWordDefinition(wordId: Long): WordDefinitionEntity? = null
    override suspend fun getRandomWordDefinitionsExcept(wordId: Long, limit: Int): List<WordDefinitionEntity> = emptyList()
    override suspend fun getRandomDefinition(wordId: Long): WordDefinitionEntity {
        throw UnsupportedOperationException("Not needed in test")
    }
    override suspend fun getRandomDistractorsByPos(wordId: Long, limit: Int): List<WordDefinitionEntity> = emptyList()
}

private class EmptyWordExampleDao : WordExampleDao {
    override suspend fun insert(example: WordExampleEntity) = Unit
    override suspend fun insert(example: List<WordExampleEntity>) = Unit
    override suspend fun update(example: WordExampleEntity) = Unit
    override suspend fun delete(example: WordExampleEntity) = Unit
    override suspend fun getExamplesByWordId(wordId: Long): List<WordExampleEntity> = emptyList()
    override suspend fun deleteByWordId(wordId: Long) = Unit
}

private class EmptyWordFormDao : WordFormDao {
    override suspend fun insert(form: WordFormEntity) = Unit
    override suspend fun insert(form: List<WordFormEntity>) = Unit
    override suspend fun update(form: WordFormEntity) = Unit
    override suspend fun delete(form: WordFormEntity) = Unit
    override fun getFormsByWordId(wordId: Long): List<WordFormEntity> = emptyList()
    override fun getByFormText(formText: String): List<WordFormEntity> = emptyList()
    override suspend fun deleteByWordId(wordId: Long) = Unit
}

private class EmptyWordUserMetaDao : WordUserMetaDao {
    override suspend fun upsert(entity: WordUserMetaEntity) = Unit
    override suspend fun deleteByWordId(wordId: Long) = Unit
}

private class EmptyWordRelationDao : WordRelationDao {
    override suspend fun insertSynonyms(items: List<WordSynonymEntity>) = Unit
    override suspend fun insertAntonyms(items: List<WordAntonymEntity>) = Unit
    override suspend fun insertTags(items: List<WordTagEntity>) = Unit
    override suspend fun insertAssociations(items: List<WordAssociationEntity>) = Unit
    override suspend fun deleteSynonymsByWordId(wordId: Long) = Unit
    override suspend fun deleteAntonymsByWordId(wordId: Long) = Unit
    override suspend fun deleteTagsByWordId(wordId: Long) = Unit
    override suspend fun deleteAssociationsByWordId(wordId: Long) = Unit
}

private class EmptyWordRootDao : WordRootDao {
    override suspend fun insert(root: WordRootEntity) = Unit
    override suspend fun insert(root: List<WordRootEntity>) = Unit
    override suspend fun insertRoots(roots: List<WordRootEntity>) = Unit
    override suspend fun insertRootMeaning(meaning: com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootmeaning.RootMeaningEntity) = Unit
    override suspend fun insertRootMeanings(meanings: List<com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootmeaning.RootMeaningEntity>) = Unit
    override suspend fun insertRootVariant(variant: com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootvariant.RootVariantEntity) = Unit
    override suspend fun insertRootVariants(variants: List<com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootvariant.RootVariantEntity>) = Unit
    override suspend fun insertRootExample(example: com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootexample.RootExampleEntity) = Unit
    override suspend fun insertRootExamples(examples: List<com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootexample.RootExampleEntity>) = Unit
    override suspend fun getRootByRootWord(rootWord: String): List<WordRootEntity> = emptyList()
    override suspend fun getWordRootById(id: Long): WordRootEntity? = null
    override suspend fun getWordRootsByIds(ids: List<Long>): List<WordRootEntity> = emptyList()
}

private class EmptyRootTagDao : RootTagDao {
    override suspend fun insertAll(items: List<RootTagEntity>) = Unit
    override suspend fun deleteByRootId(rootId: Long) = Unit
    override suspend fun deleteByRootIds(rootIds: List<Long>) = Unit
    override suspend fun getByRootIds(rootIds: List<Long>): List<RootTagEntity> = emptyList()
}

private class EmptyRootWordDao : RootWordDao {
    override suspend fun insert(rootWord: RootWordEntity) = Unit
    override suspend fun insert(rootWords: List<RootWordEntity>) = Unit
    override suspend fun insertAll(rootWords: List<RootWordEntity>) = Unit
    override fun getRootsForWordId(wordId: Long): List<RootWordEntity> = emptyList()
    override fun getWordsForRoot(rootId: Long): Flow<List<RootWordEntity>> = emptyFlow()
    override suspend fun deleteByWordId(wordId: Long) = Unit
    override suspend fun delete(wordId: Long, sequence: Int) = Unit
    override suspend fun clearAll() = Unit
}

private class FakeRemoteWordBookDataSource : RemoteWordBookDataSource {
    override suspend fun getWordBooks(): Result<List<com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto>> {
        return Result.success(emptyList())
    }

    override suspend fun getBookWords(
        bookId: Long,
        page: Int,
        count: Int
    ): Result<com.chen.memorizewords.core.network.http.PageData<com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordDto>> {
        return Result.success(
            com.chen.memorizewords.core.network.http.PageData(
            page = page,
            size = count,
            total = 0,
            items = emptyList()
        )
        )
    }

    override suspend fun lookupWord(
        word: String,
        normalizedWord: String
    ): Result<com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordDto?> {
        return Result.success(null)
    }
}
