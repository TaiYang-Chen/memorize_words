package com.chen.memorizewords.domain.repository

import com.chen.memorizewords.domain.model.study.progress.wordbook.WordBookProgress
import kotlinx.coroutines.flow.Flow

// 学习进度仓库（用于按 bookId 列表提供统计信息）
interface LearningProgressRepository {
    fun getProgressForBooksFlow(bookIds: List<Long>): Flow<Map<Long, WordBookProgress>>
    fun getProgressByWordBookId(bookId: Long): Flow<WordBookProgress?>
    fun getStudyTotalWordCount(): Flow<Int>
}
