package com.chen.memorizewords.feature.home

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.sync.PostLoginBootstrapState
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateCandidate
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdatePrompt
import com.chen.memorizewords.domain.service.sync.SyncFacade
import com.chen.memorizewords.domain.service.wordbook.WordBookUpdateCoordinator
import com.chen.memorizewords.domain.usecase.user.IsLoggedInUseCase
import com.chen.memorizewords.domain.usecase.wordbook.GetCurrentWordBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val isLoggedInUseCase: IsLoggedInUseCase,
    private val getCurrentWordBookUseCase: GetCurrentWordBookUseCase,
    private val syncFacade: SyncFacade,
    private val updateCoordinator: WordBookUpdateCoordinator,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    companion object {
        const val ACTION_WORD_BOOK_UPDATE = "word_book_update"
    }

    sealed interface Route {
        data object ToWordBookUpdates : Route
    }

    private val _loginState = MutableStateFlow<Boolean?>(null)
    val loginState: StateFlow<Boolean?> = _loginState

    private val _wordbookState = MutableStateFlow<Boolean?>(null)
    val wordbookState: StateFlow<Boolean?> = _wordbookState

    private val postLoginBootstrapState: StateFlow<PostLoginBootstrapState> =
        syncFacade.observePostLoginBootstrapState()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PostLoginBootstrapState.Idle
            )

    private var pendingPrompt: WordBookUpdatePrompt? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            postLoginBootstrapState.collect { bootstrapState ->
                if (loginState.value != true) return@collect
                val hasWordBook = getCurrentWordBookUseCase() != null
                _wordbookState.value = resolveWordBookSelectionState(
                    hasWordBook = hasWordBook,
                    bootstrapState = bootstrapState
                )
            }
        }
        viewModelScope.launch {
            updateCoordinator.observeForegroundPrompt().collect { prompt ->
                pendingPrompt = prompt
                prompt ?: return@collect
                showConfirmDialog(
                    action = ACTION_WORD_BOOK_UPDATE,
                    title = resourceProvider.getString(R.string.feature_home_wordbook_update_dialog_title),
                    message = buildUpdateMessage(prompt.candidate),
                    confirmText = resourceProvider.getString(R.string.feature_home_wordbook_update_action),
                    cancelText = resourceProvider.getString(R.string.feature_home_wordbook_remind_later_action)
                )
            }
        }
    }

    fun checkAutoLogin() {
        viewModelScope.launch(Dispatchers.IO) {
            val isLoggedIn = isLoggedInUseCase()
            _loginState.value = isLoggedIn
            if (isLoggedIn) {
                val bootstrapPlan = resolveAutoLoginBootstrapPlan(
                    currentState = syncFacade.getCurrentPostLoginBootstrapState()
                )
                if (bootstrapPlan.shouldStartBootstrap) {
                    syncFacade.startPostLoginBootstrap()
                }
                val hasWordBook = getCurrentWordBookUseCase() != null
                _wordbookState.value = resolveWordBookSelectionState(
                    hasWordBook = hasWordBook,
                    bootstrapState = bootstrapPlan.effectiveState
                )
            } else {
                _wordbookState.value = null
            }
        }
    }

    fun onWordBookUpdateDialogConfirmed() {
        val prompt = pendingPrompt ?: return
        viewModelScope.launch {
            updateCoordinator.openUpdatePageFromPrompt()
            pendingPrompt = null
            navigateRoute(Route.ToWordBookUpdates)
        }
    }

    fun onWordBookUpdateDialogIgnored() {
        val prompt = pendingPrompt ?: return
        viewModelScope.launch {
            updateCoordinator.remindLater()
            pendingPrompt = null
            showToast(
                resourceProvider.getString(R.string.feature_home_wordbook_update_remind_later)
            )
        }
    }

    private fun buildUpdateMessage(update: WordBookUpdateCandidate): String {
        val sampleWords = update.summary.sampleWords.take(3).joinToString("、")
        val sampleLine = if (sampleWords.isBlank()) {
            ""
        } else {
            resourceProvider.getString(R.string.feature_home_wordbook_update_samples, sampleWords)
        }
        return resourceProvider.getString(
            R.string.feature_home_wordbook_update_dialog_message,
            update.bookName,
            update.summary.addedCount,
            update.summary.modifiedCount,
            update.summary.removedCount
        ) + if (sampleLine.isBlank()) "" else "\n$sampleLine"
    }
}

internal fun resolveWordBookSelectionState(
    hasWordBook: Boolean,
    bootstrapState: PostLoginBootstrapState
): Boolean? {
    if (hasWordBook) {
        return true
    }
    return when (bootstrapState) {
        PostLoginBootstrapState.Running -> null
        PostLoginBootstrapState.Idle,
        PostLoginBootstrapState.Failed,
        PostLoginBootstrapState.Succeeded -> false
    }
}

internal data class AutoLoginBootstrapPlan(
    val shouldStartBootstrap: Boolean,
    val effectiveState: PostLoginBootstrapState
)

internal fun resolveAutoLoginBootstrapPlan(
    currentState: PostLoginBootstrapState
): AutoLoginBootstrapPlan {
    return if (
        currentState == PostLoginBootstrapState.Idle ||
        currentState == PostLoginBootstrapState.Failed
    ) {
        AutoLoginBootstrapPlan(
            shouldStartBootstrap = true,
            effectiveState = PostLoginBootstrapState.Running
        )
    } else {
        AutoLoginBootstrapPlan(
            shouldStartBootstrap = false,
            effectiveState = currentState
        )
    }
}
