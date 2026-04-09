package com.chen.memorizewords.domain.service.wordbook

import com.chen.memorizewords.domain.model.common.PageSlice
import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.domain.model.wordbook.WordBookInfo
import com.chen.memorizewords.domain.model.wordbook.WordBookPendingUpdate
import com.chen.memorizewords.domain.model.wordbook.WordListQuery
import com.chen.memorizewords.domain.model.words.WordListRow
import com.chen.memorizewords.domain.usecase.wordbook.CheckCurrentWordBookUpdateUseCase
import com.chen.memorizewords.domain.usecase.wordbook.GetCurrentWordBookInfoFlowUseCase
import com.chen.memorizewords.domain.usecase.wordbook.GetMyWordBooksWithProgressUseCase
import com.chen.memorizewords.domain.usecase.wordbook.GetWordBookWordRowsPageUseCase
import com.chen.memorizewords.domain.usecase.wordbook.SetCurrentWordBookUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class WordBookFacade @Inject constructor(
    private val getCurrentWordBookInfoFlowUseCase: GetCurrentWordBookInfoFlowUseCase,
    private val getMyWordBooksWithProgressUseCase: GetMyWordBooksWithProgressUseCase,
    private val setCurrentWordBookUseCase: SetCurrentWordBookUseCase,
    private val getWordBookWordRowsPageUseCase: GetWordBookWordRowsPageUseCase,
    private val wordBookRepositoryReader: WordBookRepositoryReader,
    private val checkCurrentWordBookUpdateUseCase: CheckCurrentWordBookUpdateUseCase
) {
    fun observeCurrentWordBookInfo(): Flow<WordBookInfo?> = getCurrentWordBookInfoFlowUseCase()

    fun observeMyWordBooksWithProgress(): Flow<List<WordBookInfo>> =
        getMyWordBooksWithProgressUseCase()

    suspend fun setCurrentWordBook(bookId: Long) {
        setCurrentWordBookUseCase(bookId)
    }

    suspend fun getCurrentWordBook(): WordBook? = wordBookRepositoryReader.getCurrentWordBook()

    suspend fun getBookNameById(bookId: Long): String? = wordBookRepositoryReader.getBookNameById(bookId)

    suspend fun getWordRowsPage(query: WordListQuery): PageSlice<WordListRow> =
        getWordBookWordRowsPageUseCase(query)

    suspend fun checkCurrentWordBookUpdate(): WordBookPendingUpdate? =
        checkCurrentWordBookUpdateUseCase()
}
