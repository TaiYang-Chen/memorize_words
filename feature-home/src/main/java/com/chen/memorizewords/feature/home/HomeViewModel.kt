package com.chen.memorizewords.feature.home

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateCandidate
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdatePrompt
import com.chen.memorizewords.domain.wordbook.service.WordBookUpdateCoordinator
import com.chen.memorizewords.domain.account.usecase.user.IsLoggedInUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetCurrentWordBookSelectionIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val isLoggedInUseCase: IsLoggedInUseCase,
    private val getCurrentWordBookSelectionIdUseCase: GetCurrentWordBookSelectionIdUseCase,
    private val updateCoordinator: WordBookUpdateCoordinator,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    companion object {
        const val ACTION_WORD_BOOK_UPDATE = "word_book_update"
    }

    sealed interface Route {
    }

    private val _loginState = MutableStateFlow<Boolean?>(null)
    val loginState: StateFlow<Boolean?> = _loginState

    private val _wordbookState = MutableStateFlow<Boolean?>(null)
    val wordbookState: StateFlow<Boolean?> = _wordbookState

    private var pendingPrompt: WordBookUpdatePrompt? = null

    init {
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
                _wordbookState.value = getCurrentWordBookSelectionIdUseCase() != null
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
            navigate(AppRoute.WordBook())
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
