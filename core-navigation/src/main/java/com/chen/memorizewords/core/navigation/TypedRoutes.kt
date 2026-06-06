package com.chen.memorizewords.core.navigation

sealed interface AppRoute {
    data object Launch : AppRoute
    data object Home : AppRoute
    data class Auth(val deepLink: String? = null) : AppRoute
    data object Onboarding : AppRoute
    data class WordBook(val deepLink: String? = null) : AppRoute
    data class Feedback(val deepLink: String? = null) : AppRoute
    data class Learning(
        val initialLearnedCount: Int = 0,
        val wordIds: List<Long>,
        val sessionType: Int,
        val sessionWordCount: Int
    ) : AppRoute
    data class OpenWord(val wordId: Long, val fromFloating: Boolean) : AppRoute
    data class Practice(
        val modeName: String,
        val randomCount: Int,
        val entryTypeName: String,
        val entryCount: Int,
        val selectedIds: LongArray? = null
    ) : AppRoute {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Practice) return false
            return modeName == other.modeName &&
                randomCount == other.randomCount &&
                entryTypeName == other.entryTypeName &&
                entryCount == other.entryCount &&
                selectedIds.contentEquals(other.selectedIds)
        }

        override fun hashCode(): Int {
            var result = modeName.hashCode()
            result = 31 * result + randomCount
            result = 31 * result + entryTypeName.hashCode()
            result = 31 * result + entryCount
            result = 31 * result + selectedIds.contentHashCode()
            return result
        }
    }
}

interface RouteNavigator {
    fun navigate(route: AppRoute)
}
