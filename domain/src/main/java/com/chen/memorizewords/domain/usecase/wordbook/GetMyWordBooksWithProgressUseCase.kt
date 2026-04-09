package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.model.study.progress.wordbook.WordBookProgress
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
import kotlin.math.roundToInt

// 用例：从 wordbook repo 拿词书列表，再从 learning repo 拿统计并合并为 UI 模型
class GetMyWordBooksWithProgressUseCase @Inject constructor(
    private val wordBookRepo: WordBookRepository,          // 词书仓库
    private val progressRepo: LearningProgressRepository   // 学习进度仓库
) {
    /**
     * 返回一个 Flow，当以下任意数据变化时自动刷新：
     * - 我的词书列表发生变化
     * - 任意词书的学习进度发生变化
     */
    operator fun invoke(): Flow<List<WordBookInfo>> {

        // 1️⃣ 词书列表 Flow（只包含最小元数据）
        val booksFlow: Flow<List<WordBookInfo>> =
            wordBookRepo.getMyWordBooksMinimalFlow()

        // 2️⃣ 将 booksFlow 转换为 progressFlow（依赖 bookIds）
        val progressFlow: Flow<Map<Long, WordBookProgress>> =
            booksFlow
                .map { books -> books.map { it.bookId } } // 提取 bookIds
                .distinctUntilChanged()                   // bookId 集合不变就不重新查
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(emptyMap())                // 没有词书，直接空 map
                    } else {
                        progressRepo.getProgressForBooksFlow(ids)
                    }
                }

        // 3️⃣ 合并两个 Flow
        return combine(
            booksFlow,
            progressFlow
        ) { books, progressMap ->

            books.map { book ->
                val stats = progressMap[book.bookId]

                book.copy(
                    learningWords = stats?.learningCount ?: 0,
                    masteredWords = stats?.masteredCount ?: 0,
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
