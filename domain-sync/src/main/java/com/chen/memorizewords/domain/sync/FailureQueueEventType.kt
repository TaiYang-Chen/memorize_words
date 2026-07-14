package com.chen.memorizewords.domain.sync

/** Stable event identifiers shared by Retrofit metadata and the client replay registry. */
object FailureQueueEventType {
    const val LEARNING_EVENT = "LEARNING_EVENT"
    const val PRACTICE_SETTINGS = "PRACTICE_SETTINGS"
    const val PRACTICE_DURATION = "PRACTICE_DURATION"
    const val PRACTICE_SESSION = "PRACTICE_SESSION"
    const val FLOATING_SETTINGS = "FLOATING_SETTINGS"
    const val FLOATING_DISPLAY_RECORD = "FLOATING_DISPLAY_RECORD"
    const val ONBOARDING_STATE = "ONBOARDING_STATE"
    const val STUDY_PLAN = "STUDY_PLAN"
    const val FAVORITE_ADD = "FAVORITE_ADD"
    const val FAVORITE_REMOVE = "FAVORITE_REMOVE"
    const val WORD_BOOK_DELETE = "WORD_BOOK_DELETE"
    const val WORD_BOOK_SELECTION = "WORD_BOOK_SELECTION"
    const val DAILY_STUDY_DURATION = "DAILY_STUDY_DURATION"
    const val CHECKIN_RECORD = "CHECKIN_RECORD"
}
