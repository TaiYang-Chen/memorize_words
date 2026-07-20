package com.chen.memorizewords.core.navigation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

data class LearningSessionRequest(
    val initialLearnedCount: Int = 0,
    val wordIds: List<Long>,
    val sessionType: Int,
    val sessionWordCount: Int
)

interface HomeEntry {
    fun createHomeIntent(
        context: Context,
        destination: HomeDestination = HomeDestination.DEFAULT
    ): Intent
}

interface AppLaunchEntry {
    fun createLaunchIntent(context: Context): Intent
}

interface AuthEntry {
    fun createAuthIntent(context: Context): Intent
}

interface OnboardingEntry {
    fun createOnboardingIntent(context: Context): Intent
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
        modeName: String,
        randomCount: Int,
        entryTypeName: String,
        entryCount: Int,
        selectedIds: LongArray?
    ): Intent

    fun createWordPickerIntent(context: Context, initialSelectedIds: LongArray? = null): Intent

    fun extractSelectedWordIds(intent: Intent?): LongArray?
}

interface FloatingWordEntry {
    fun createServiceIntent(
        context: Context,
        action: String,
        activationRequestId: String? = null
    ): Intent
    fun createSettingsIntent(
        context: Context,
        destination: FloatingWordDestination = FloatingWordDestination.SETTINGS,
        activationRequestId: String? = null,
        returnDestination: FloatingWordReturnDestination = FloatingWordReturnDestination.DEFAULT
    ): Intent

    fun dispatchServiceAction(
        context: Context,
        action: String,
        activationRequestId: String? = null
    ) {
        tryDispatchServiceAction(context, action, activationRequestId)
    }

    fun tryDispatchServiceAction(
        context: Context,
        action: String,
        activationRequestId: String? = null,
        characterPackId: String? = null,
        downloadRequestId: String? = null
    ): Boolean {
        val intent = createServiceIntent(context, action, activationRequestId).apply {
            if (action == FloatingWordActions.ACTION_APPLY_CHARACTER_PACK) {
                characterPackId?.let {
                    putExtra(FloatingWordEntryExtras.EXTRA_CHARACTER_PACK_ID, it)
                }
                downloadRequestId?.let {
                    putExtra(FloatingWordEntryExtras.EXTRA_DOWNLOAD_REQUEST_ID, it)
                }
            }
        }
        return when (action) {
            FloatingWordActions.ACTION_START -> {
                ContextCompat.startForegroundService(context, intent)
                true
            }

            FloatingWordActions.ACTION_STOP ->
                context.stopService(intent)

            else -> {
                try {
                    context.startService(intent) != null
                } catch (_: IllegalStateException) {
                    // Apply/refresh actions are best effort when the service is no longer running.
                    false
                } catch (_: SecurityException) {
                    // A revoked platform permission must not crash the calling screen.
                    false
                }
            }
        }
    }
}

interface WeChatAuthResultHandler {
    fun onSuccess(oauthCode: String, state: String?)
    fun onCancel()
    fun onError(message: String)
}

object WordBookEntryDestination {
    const val FAVORITES_DEEP_LINK = "myapp://favorites"
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

enum class HomeDestination {
    DEFAULT,
    PRACTICE
}

object HomeEntryExtras {
    const val EXTRA_DESTINATION = "home_destination"
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
    const val ACTION_APPLY_BALL_APPEARANCE = "floating_word_apply_ball_appearance"
    const val ACTION_APPLY_CHARACTER_PACK = "floating_word_apply_character_pack"
}

enum class FloatingWordDestination {
    SETTINGS,
    CHARACTER_SELECTION
}
enum class FloatingWordReturnDestination {
    DEFAULT,
    PRACTICE
}

enum class CharacterSelectionMode {
    MANAGE,
    ACTIVATE
}

object FloatingWordEntryExtras {
    const val EXTRA_DESTINATION = "floating_destination"
    const val EXTRA_CHARACTER_MODE = "floating_character_mode"
    const val EXTRA_ACTIVATION_REQUEST_ID = "floating_activation_request_id"
    const val EXTRA_CHARACTER_PACK_ID = "floating_character_pack_id"
    const val EXTRA_DOWNLOAD_REQUEST_ID = "floating_download_request_id"
    const val EXTRA_RETURN_DESTINATION = "floating_return_destination"
}
