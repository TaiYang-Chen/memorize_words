package com.chen.memorizewords.domain.query.word

import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.domain.model.words.word.WordForm
import com.chen.memorizewords.domain.model.words.word.WordQuickLookupResult
import com.chen.memorizewords.domain.model.words.word.WordRoot
import com.chen.memorizewords.domain.repository.word.WordRepository
import com.chen.memorizewords.domain.usecase.word.GenerateMultipleChoiceOptionsUseCase
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
        return WordDetail(
            word = word,
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
