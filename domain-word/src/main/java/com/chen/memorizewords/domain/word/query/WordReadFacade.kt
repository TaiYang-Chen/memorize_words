package com.chen.memorizewords.domain.word.query
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.domain.word.model.word.WordForm
import com.chen.memorizewords.domain.word.model.word.WordQuickLookupResult
import com.chen.memorizewords.domain.word.model.word.WordRoot
import com.chen.memorizewords.domain.word.repository.WordRepository
import com.chen.memorizewords.domain.word.usecase.GenerateMultipleChoiceOptionsUseCase
import javax.inject.Inject

class WordReadFacade @Inject constructor(
    private val wordRepository: WordRepository,
    private val generateMultipleChoiceOptionsUseCase: GenerateMultipleChoiceOptionsUseCase
) {
    suspend fun getWordById(wordId: Long): Word? = wordRepository.getWordById(wordId)

    suspend fun getWordByWordString(word: String): Word? = wordRepository.getWordByWordString(word)

    suspend fun getWordsByIds(ids: List<Long>): List<Word> = wordRepository.getWordsByIds(ids)

    suspend fun getWordDefinitions(wordId: Long): List<WordDefinitions> =
        wordRepository.getWordDefinitions(wordId)

    suspend fun getWordExamples(wordId: Long): List<WordExample> =
        wordRepository.getWordExamples(wordId)

    suspend fun getWordRoots(wordId: Long): List<WordRoot> =
        wordRepository.getRootWordByWordId(wordId)

    suspend fun getWordForms(wordId: Long): List<WordForm> = wordRepository.getWordForms(wordId)

    suspend fun lookupWordQuick(normalizedWord: String, rawWord: String): WordQuickLookupResult =
        wordRepository.lookupWordQuick(normalizedWord, rawWord)

    suspend fun generateMultipleChoiceOptions(wordId: Long): List<WordDefinitions> =
        generateMultipleChoiceOptionsUseCase(wordId)

    suspend fun getWordDetailById(wordId: Long): WordDetail? {
        val word = getWordById(wordId) ?: return null
        return getWordDetail(word)
    }

    suspend fun getWordDetailByWordString(word: String): WordDetail? {
        val resolvedWord = getWordByWordString(word) ?: return null
        return getWordDetail(resolvedWord)
    }

    suspend fun getWordDetail(word: Word): WordDetail {
        val fullWord = wordRepository.getWordById(word.id) ?: word
        return WordDetail(
            word = fullWord,
            definitions = getWordDefinitions(word.id),
            examples = getWordExamples(word.id),
            roots = getWordRoots(word.id),
            forms = getWordForms(word.id)
        )
    }
}

data class WordDetail(
    val word: Word,
    val definitions: List<WordDefinitions>,
    val examples: List<WordExample>,
    val roots: List<WordRoot>,
    val forms: List<WordForm>
)
