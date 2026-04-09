package com.chen.memorizewords.core.navigation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.chen.memorizewords.domain.model.learning.LearningSessionRequest
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode

interface HomeEntry {
    fun createHomeIntent(context: Context): Intent
}

interface AuthEntry {
    fun createAuthIntent(context: Context): Intent
}

interface WordBookEntry {
    fun createWordBookIntent(context: Context, deepLink: String? = null): Intent
}

interface FeedbackEntry {
    fun createFeedbackIntent(context: Context, deepLink: String? = null): Intent
}

interface LearningEntry {
    fun createLearningIntent(context: Context, request: LearningSessionRequest): Intent
    fun createOpenWordIntent(context: Context, wordId: Long, fromFloating: Boolean): Intent
}

interface PracticeEntry {
    fun createPracticeIntent(
        context: Context,
        mode: PracticeMode,
        randomCount: Int,
        entryType: PracticeEntryType,
        entryCount: Int,
        selectedIds: LongArray?
    ): Intent

    fun createWordPickerIntent(context: Context, initialSelectedIds: LongArray? = null): Intent

    fun extractSelectedWordIds(intent: Intent?): LongArray?
}

interface FloatingWordEntry {
    fun createServiceIntent(context: Context, action: String): Intent
    fun createSettingsIntent(context: Context): Intent

    fun dispatchServiceAction(context: Context, action: String) {
        val intent = createServiceIntent(context, action)
        if (action == FloatingWordActions.ACTION_START) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}

interface WeChatAuthResultHandler {
    fun onSuccess(oauthCode: String, state: String?)
    fun onCancel()
    fun onError(message: String)
}

object WordBookEntryDestination {
    const val SHOP_DEEP_LINK = "myapp://wordbook/shop"
    const val MY_BOOKS_DEEP_LINK = "myapp://wordbook/my-books"

    fun myBooksDeepLink(source: String? = null): String {
        return if (source.isNullOrBlank()) {
            MY_BOOKS_DEEP_LINK
        } else {
            "$MY_BOOKS_DEEP_LINK?source=$source"
        }
    }
}

object AuthEntryDestination {
    const val USER_PROFILE_DEEP_LINK = "myapp://user/profile"
}

object LearningEntryExtras {
    const val EXTRA_INIT_LEARNED_COUNT = "extra_init_learned_count"
    const val EXTRA_WORD_IDS = "extra_word_ids"
    const val EXTRA_LEARNING_TYPE = "extra_learning_type"
    const val EXTRA_LEARNING_COUNT = "extra_learning_count"
    const val EXTRA_OPEN_WORD_ID = "extra_open_word_id"
    const val EXTRA_OPEN_FROM_FLOATING = "extra_open_from_floating"
}

object PracticeEntryExtras {
    const val EXTRA_PRACTICE_MODE = "extra_practice_mode"
    const val EXTRA_SELECTED_WORD_IDS = "extra_selected_word_ids"
    const val EXTRA_RANDOM_COUNT = "extra_random_count"
    const val EXTRA_ENTRY_TYPE = "extra_entry_type"
    const val EXTRA_ENTRY_COUNT = "extra_entry_count"
    const val EXTRA_INITIAL_SELECTED_WORD_IDS = "extra_initial_selected_word_ids"
    const val ARG_SELECTED_WORD_IDS = "arg_selected_word_ids"
    const val ARG_RANDOM_COUNT = "arg_random_count"
}

object FloatingWordActions {
    const val ACTION_START = "floating_word_start"
    const val ACTION_STOP = "floating_word_stop"
    const val ACTION_REFRESH = "floating_word_refresh"
    const val ACTION_PREVIEW_CARD = "floating_word_preview_card"
}
