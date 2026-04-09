package com.chen.memorizewords.data.repository.wordbook.update

import com.chen.memorizewords.data.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.data.repository.sync.NetworkMonitor
import com.chen.memorizewords.domain.model.sync.PostLoginBootstrapState
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateAction
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateCandidate
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateExecutionMode
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateJobState
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateNetworkPolicy
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdatePrompt
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateTrigger
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateUiState
import com.chen.memorizewords.domain.repository.WordBookRepository
import com.chen.memorizewords.domain.repository.WordBookUpdateRepository
import com.chen.memorizewords.domain.service.sync.SyncFacade
import com.chen.memorizewords.domain.service.wordbook.WordBookUpdateCoordinator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Singleton
class WordBookUpdateCoordinatorImpl @Inject constructor(
    private val repository: WordBookUpdateRepository,
    private val wordBookRepository: WordBookRepository,
    private val syncFacade: SyncFacade,
    private val syncStateStore: WordBookSyncStateStore,
    private val networkMonitor: NetworkMonitor
) : WordBookUpdateCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uiState = MutableStateFlow(WordBookUpdateUiState())
    private val foregroundPrompt = MutableStateFlow<WordBookUpdatePrompt?>(null)
    private val localNotificationPrompts = MutableSharedFlow<WordBookUpdatePrompt>(extraBufferCapacity = 1)
    private var updateObserveJob: Job? = null
    private var observedBookId: Long? = null
    private var handledTerminalStateKey: String? = null

    init {
        scope.launch {
            repository.observeSettings().collect { settings ->
                updateState { it.copy(settings = settings) }
            }
        }
    }

    override fun observeUiState(): Flow<WordBookUpdateUiState> = uiState.asStateFlow()

    override fun observeForegroundPrompt(): Flow<WordBookUpdatePrompt?> = foregroundPrompt.asStateFlow()

    override fun observeLocalNotificationPrompts(): Flow<WordBookUpdatePrompt> =
        localNotificationPrompts.asSharedFlow()

    override suspend fun onAppForeground(deliverAsNotification: Boolean) {
        if (syncFacade.getCurrentPostLoginBootstrapState() == PostLoginBootstrapState.Running) return
        val candidate = refreshCandidate(WordBookUpdateTrigger.FOREGROUND) ?: return
        if (shouldStartSilentUpdate(candidate)) {
            startUpdate(candidate, WordBookUpdateExecutionMode.SILENT, WordBookUpdateTrigger.FOREGROUND)
            return
        }
        if (!uiState.value.settings.foregroundAlertsEnabled) return
        if (!shouldShowForegroundPrompt(candidate)) return
        repository.reportAction(
            action = WordBookUpdateAction.PROMPT_SHOWN,
            candidate = candidate,
            trigger = WordBookUpdateTrigger.FOREGROUND
        )
        syncStateStore.setLastPrompt(
            candidate.bookId,
            candidate.targetVersion,
            System.currentTimeMillis(),
            WordBookUpdateTrigger.FOREGROUND
        )
        val prompt = WordBookUpdatePrompt(candidate, WordBookUpdateTrigger.FOREGROUND)
        if (deliverAsNotification) {
            localNotificationPrompts.tryEmit(prompt)
        } else {
            foregroundPrompt.value = prompt
        }
    }

    override suspend fun onWordBookPageEntered() {
        val candidate = refreshCandidate(WordBookUpdateTrigger.WORDBOOK_PAGE)
        if (candidate != null) {
            repository.reportAction(
                action = WordBookUpdateAction.OPEN_PAGE,
                candidate = candidate,
                trigger = WordBookUpdateTrigger.WORDBOOK_PAGE
            )
        }
    }

    override suspend fun openUpdatePageFromPrompt() {
        val prompt = foregroundPrompt.value ?: return
        repository.reportAction(
            action = WordBookUpdateAction.OPEN_PAGE,
            candidate = prompt.candidate,
            trigger = prompt.trigger
        )
        foregroundPrompt.value = null
    }

    override suspend fun remindLater() {
        val candidate = foregroundPrompt.value?.candidate ?: uiState.value.candidate ?: return
        val deferredUntil = System.currentTimeMillis() + uiState.value.settings.remindLaterDurationMs
        repository.saveDeferredUntil(candidate.bookId, deferredUntil)
        repository.reportAction(
            action = WordBookUpdateAction.REMIND_LATER,
            candidate = candidate,
            trigger = uiState.value.lastTrigger,
            deferredUntil = deferredUntil
        )
        foregroundPrompt.value = null
        updateState { it.copy(deferredUntil = deferredUntil) }
    }

    override suspend fun ignoreVersion() {
        val candidate = foregroundPrompt.value?.candidate ?: uiState.value.candidate ?: return
        repository.ignoreVersion(candidate.bookId, candidate.targetVersion)
        foregroundPrompt.value = null
        updateState { current ->
            if (current.candidate?.targetVersion == candidate.targetVersion) {
                current.copy(candidate = null, deferredUntil = 0L, detailsVisible = false)
            } else {
                current
            }
        }
    }

    override suspend fun confirmUpdate() {
        val candidate = uiState.value.candidate ?: foregroundPrompt.value?.candidate ?: return
        startUpdate(candidate, WordBookUpdateExecutionMode.MANUAL, uiState.value.lastTrigger)
    }

    override suspend fun updateForegroundAlertsEnabled(enabled: Boolean) {
        repository.saveSettings(uiState.value.settings.copy(foregroundAlertsEnabled = enabled))
    }

    override suspend fun updateSilentUpdateEnabled(enabled: Boolean) {
        repository.saveSettings(uiState.value.settings.copy(silentUpdateEnabled = enabled))
    }

    override suspend fun dismissDetails() {
        updateState { it.copy(detailsVisible = false) }
    }

    override suspend fun showDetails() {
        updateState { it.copy(detailsVisible = true) }
    }

    override suspend fun dismissSettings() {
        updateState { it.copy(settingsVisible = false) }
    }

    override suspend fun showSettings() {
        updateState { it.copy(settingsVisible = true) }
    }

    private suspend fun refreshCandidate(trigger: WordBookUpdateTrigger): WordBookUpdateCandidate? {
        val currentBook = wordBookRepository.getCurrentWordBook() ?: run {
            foregroundPrompt.value = null
            updateState { it.copy(candidate = null, deferredUntil = 0L, lastTrigger = trigger) }
            return null
        }
        val candidate = repository.fetchCandidate(trigger).getOrNull()
        observeUpdateState(currentBook.id)
        if (candidate == null) {
            foregroundPrompt.value = null
            updateState {
                it.copy(
                    candidate = null,
                    deferredUntil = syncStateStore.getDeferredUntil(currentBook.id),
                    lastTrigger = trigger
                )
            }
            return null
        }
        observeUpdateState(candidate.bookId)
        updateState {
            it.copy(
                candidate = candidate,
                deferredUntil = syncStateStore.getDeferredUntil(candidate.bookId),
                lastTrigger = trigger
            )
        }
        return candidate
    }

    private suspend fun startUpdate(
        candidate: WordBookUpdateCandidate,
        executionMode: WordBookUpdateExecutionMode,
        trigger: WordBookUpdateTrigger?
    ) {
        foregroundPrompt.value = null
        syncStateStore.clearFailure(candidate.bookId)
        repository.reportAction(
            action = WordBookUpdateAction.START_UPDATE,
            candidate = candidate,
            trigger = trigger,
            executionMode = executionMode
        )
        observeUpdateState(candidate.bookId)
        repository.enqueueUpdate(candidate.bookId, candidate.targetVersion, executionMode)
    }

    private fun observeUpdateState(bookId: Long) {
        if (observedBookId == bookId && updateObserveJob?.isActive == true) return
        updateObserveJob?.cancel()
        observedBookId = bookId
        updateObserveJob = scope.launch {
            repository.observeUpdateJobState(bookId).collect { state ->
                updateState { it.copy(jobState = state) }
                when (state) {
                    WordBookUpdateJobState.Idle -> Unit
                    is WordBookUpdateJobState.Running -> Unit
                    is WordBookUpdateJobState.Succeeded -> {
                        handleTerminalStateOnce("success:${state.bookId}:${state.targetVersion}") {
                            foregroundPrompt.value = null
                            updateState { current ->
                                if (current.candidate?.targetVersion == state.targetVersion) {
                                    current.copy(candidate = null, deferredUntil = 0L)
                                } else {
                                    current
                                }
                            }
                        }
                    }

                    is WordBookUpdateJobState.Failed -> {
                        handleTerminalStateOnce("failed:${state.bookId}:${state.targetVersion}:${state.message}") {
                            scope.launch {
                                val candidate = uiState.value.candidate
                                repository.reportAction(
                                    action = WordBookUpdateAction.UPDATE_FAILED,
                                    candidate = candidate?.takeIf { it.bookId == state.bookId },
                                    trigger = uiState.value.lastTrigger,
                                    message = state.message
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shouldShowForegroundPrompt(candidate: WordBookUpdateCandidate): Boolean {
        if (candidate.forcePrompt) return true
        val state = syncStateStore.getState(candidate.bookId) ?: return true
        if (state.localVersion >= candidate.targetVersion) return false
        if (state.ignoredVersion >= candidate.targetVersion) return false
        if (state.deferredUntil > System.currentTimeMillis()) return false
        val running = uiState.value.jobState as? WordBookUpdateJobState.Running
        return running?.targetVersion != candidate.targetVersion
    }

    private fun shouldStartSilentUpdate(candidate: WordBookUpdateCandidate): Boolean {
        val settings = uiState.value.settings
        if (!settings.silentUpdateEnabled || !candidate.silentAllowed) return false
        return when (settings.silentUpdateNetworkPolicy) {
            WordBookUpdateNetworkPolicy.WIFI_ONLY -> networkMonitor.isCurrentlyOnWifi()
            WordBookUpdateNetworkPolicy.ANY -> networkMonitor.isCurrentlyOnline()
        }
    }

    private inline fun handleTerminalStateOnce(key: String, action: () -> Unit) {
        if (handledTerminalStateKey == key) return
        handledTerminalStateKey = key
        action()
    }

    private inline fun updateState(transform: (WordBookUpdateUiState) -> WordBookUpdateUiState) {
        uiState.value = transform(uiState.value)
    }
}
