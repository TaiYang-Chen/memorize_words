package com.chen.memorizewords.domain.word.usecase
import com.chen.memorizewords.domain.word.model.word.WordRoot
import com.chen.memorizewords.domain.word.repository.WordRepository
import javax.inject.Inject

class GetRootWordByIdUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(wordId: Long): List<WordRoot> {
        return wordRepository.getRootWordByWordId(wordId)
    }
}
