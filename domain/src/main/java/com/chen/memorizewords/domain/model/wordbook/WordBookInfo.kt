package com.chen.memorizewords.domain.model.wordbook

data class WordBookInfo(
    val bookId: Long = -1,
    val title: String = "未选择词汇书",
    val category: String = "",
    val imgUrl: String = "",
    val description: String = "",
    val totalWords: Int = 0,
    val learningWords: Int = 0,
    val masteredWords: Int = 0,
    val studyDayCount: Int = 0,
    val accuracyRate: Float = 0f,
    val isSelected: Boolean = false,
    val createdByUserId: String? = null
) {
    val remainWords = totalWords - learningWords
    fun remainDays(count: Int) = remainWords / count
}
