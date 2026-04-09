package com.chen.memorizewords.domain.usecase.wordbook


import com.chen.memorizewords.domain.model.wordbook.WordBookInfo
import com.chen.memorizewords.domain.repository.LearningProgressRepository
import com.chen.memorizewords.domain.repository.WordBookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.let
import kotlin.math.roundToInt

class GetCurrentWordBookInfoFlowUseCase @Inject constructor(
    private val wordBookRepo: WordBookRepository,
    private val progressRepo: LearningProgressRepository
) {
    /**
     * 杩斿洖涓€涓?Flow锛屽綋浠ヤ笅浠绘剰鏁版嵁鍙樺寲鏃惰嚜鍔ㄥ埛鏂帮細
     * - 鎴戠殑璇嶄功鍒楄〃鍙戠敓鍙樺寲
     * - 浠绘剰璇嶄功鐨勫涔犺繘搴﹀彂鐢熷彉鍖?
     */
    operator fun invoke(): Flow<WordBookInfo?> {
        val currentBookFlow: Flow<WordBookInfo?> =
            wordBookRepo.getCurrentWordBookMinimalFlow()
                .distinctUntilChanged()

        val progressFlow =
            currentBookFlow
                .map { it?.bookId }
                .distinctUntilChanged()
                .flatMapLatest { bookId ->
                    if (bookId == null || bookId < 0) {
                        flowOf(null)
                    } else {
                        progressRepo.getProgressByWordBookId(bookId)
                    }
                }

        return combine(currentBookFlow, progressFlow) { book, stats ->
            book?.let {
                it.copy(
                    learningWords = stats?.learningCount ?: 0,
                    masteredWords = stats?.masteredCount ?: 0,
                    studyDayCount = stats?.studyDayCount ?: 0,
                    accuracyRate = calculateAccuracyRate(
                        stats?.correctCount ?: 0,
                        stats?.wrongCount ?: 0
                    )
                )
            }
        }
    }

    private fun calculateAccuracyRate(correctCount: Int, wrongCount: Int): Float {
        val total = correctCount + wrongCount
        if (total <= 0) return 0f
        val rate = correctCount * 100f / total.toFloat()
        return (rate * 10f).roundToInt() / 10f
    }
}
