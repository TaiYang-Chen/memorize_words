package com.chen.memorizewords.domain.floating.model

sealed interface FloatingWordSourceKey {
    data class CurrentBook(val bookId: Long?) : FloatingWordSourceKey {
        init {
            require(bookId == null || bookId > 0L) { "bookId must be positive when present" }
        }
    }

    data class SelfSelect(val requestedWordIds: List<Long>) : FloatingWordSourceKey {
        init {
            require(requestedWordIds.all { it > 0L }) { "selected word ids must be positive" }
            require(requestedWordIds.distinct().size == requestedWordIds.size) {
                "selected word ids must be unique"
            }
        }
    }
}

data class FloatingWordSourceSnapshot(
    val sourceKey: FloatingWordSourceKey,
    val orderType: FloatingWordOrderType,
    val wordIds: List<Long>
) {
    init {
        require(wordIds.all { it > 0L }) { "word ids must be positive" }
        require(wordIds.distinct().size == wordIds.size) { "word ids must be unique" }
    }
}
