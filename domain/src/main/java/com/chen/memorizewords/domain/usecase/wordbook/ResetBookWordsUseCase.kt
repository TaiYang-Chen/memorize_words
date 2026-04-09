package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.repository.WordBookRepository
import com.chen.memorizewords.domain.repository.WordLearningRepository
import javax.inject.Inject

/**
 * 用例：将一个词书中的所有单词“学习状态复位”
 *
 * 这是一个【聚合型 UseCase】：
 * - 读：WordBookRepository（拿 wordIds）
 * - 写：WordLearningRepository（重置学习状态）
 *
 * ⚠️ 本类不关心数据库、不关心 Room、不关心 UI
 */
class ResetBookWordsUseCase @Inject constructor(
    private val wordLearningRepository: WordLearningRepository
) {
    /**
     * 执行复位操作（挂起函数）
     *
     * @param bookId 要复位的词书 id
     */
    suspend operator fun invoke(bookId: Long) {
        wordLearningRepository.deleteLearningWordByBookId(bookId)
    }
}
