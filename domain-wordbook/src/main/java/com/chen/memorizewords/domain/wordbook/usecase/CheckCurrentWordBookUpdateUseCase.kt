package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.wordbook.model.WordBookPendingUpdate
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import com.chen.memorizewords.domain.wordbook.repository.WordBookUpdateRepository
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
