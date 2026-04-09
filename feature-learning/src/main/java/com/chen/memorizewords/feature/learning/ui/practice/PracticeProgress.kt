package com.chen.memorizewords.feature.learning.ui.practice

internal fun resolveNextPracticeIndex(currentIndex: Int, totalCount: Int): Int? {
    if (totalCount <= 0) return null
    val lastIndex = totalCount - 1
    return if (currentIndex >= lastIndex) null else currentIndex + 1
}
