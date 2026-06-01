package com.chen.memorizewords.domain.wordbook.service
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.WordBookPendingUpdate
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.wordbook.usecase.CheckCurrentWordBookUpdateUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetCurrentWordBookInfoFlowUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetMyWordBooksWithProgressUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetWordBookWordRowsPageUseCase
import com.chen.memorizewords.domain.wordbook.usecase.SetCurrentWordBookUseCase
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
