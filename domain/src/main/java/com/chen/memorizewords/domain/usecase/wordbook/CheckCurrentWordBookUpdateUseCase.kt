package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.model.wordbook.WordBookPendingUpdate
import com.chen.memorizewords.domain.repository.WordBookRepository
import com.chen.memorizewords.domain.repository.WordBookUpdateRepository
import javax.inject.Inject

class CheckCurrentWordBookUpdateUseCase @Inject constructor(
    private val wordBookRepository: WordBookRepository,
    private val updateRepository: WordBookUpdateRepository
) {
    suspend operator fun invoke(): WordBookPendingUpdate? {
        val currentBook = wordBookRepository.getCurrentWordBook() ?: return null
        val pendingUpdate = updateRepository.getPendingUpdate(currentBook.id).getOrNull() ?: return null
        if (pendingUpdate.targetVersion <= 0L) return null
        updateRepository.markPrompted(currentBook.id, pendingUpdate.targetVersion)
        return pendingUpdate.copy(
            bookName = if (pendingUpdate.bookName.isBlank()) currentBook.title else pendingUpdate.bookName
        )
    }
}
