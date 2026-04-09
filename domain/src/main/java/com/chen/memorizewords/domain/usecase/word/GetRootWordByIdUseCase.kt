package com.chen.memorizewords.domain.usecase.word

import com.chen.memorizewords.domain.model.words.word.WordRoot
import com.chen.memorizewords.domain.repository.word.WordRepository
import javax.inject.Inject

class GetRootWordByIdUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(wordId: Long): List<WordRoot> {
        return wordRepository.getRootWordByWordId(wordId)
    }
}
