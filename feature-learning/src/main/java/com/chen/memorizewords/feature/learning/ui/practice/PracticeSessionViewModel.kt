package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.session.SessionTimer
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.service.practice.PracticeFacade
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel
class PracticeSessionViewModel @Inject constructor(
    private val practiceFacade: PracticeFacade
) : BaseViewModel() {

    private var sessionKey: String? = null
    private var sessionMode: PracticeMode? = null
    private var sessionEntryType: PracticeEntryType = PracticeEntryType.RANDOM
    private var sessionEntryCount: Int = 0
    private val sessionTimer = SessionTimer()
    private var saved: Boolean = false
    private var sessionWordIds: List<Long> = emptyList()
    private var sessionSummary: PracticeSessionSummary = PracticeSessionSummary()

    fun startSession(
        mode: PracticeMode,
        entryType: PracticeEntryType,
        entryCount: Int,
        selectedIds: LongArray?,
        randomCount: Int
    ) {
        val newSessionKey = buildPracticeSessionKey(
            mode = mode,
            entryType = entryType,
            entryCount = entryCount,
            selectedIds = selectedIds,
            randomCount = randomCount
        )
        if (sessionKey == newSessionKey) return
        sessionKey = newSessionKey
        sessionMode = mode
        sessionEntryType = entryType
        sessionEntryCount = entryCount.coerceAtLeast(0)
        sessionTimer.reset()
        saved = false
        sessionWordIds = emptyList()
        sessionSummary = PracticeSessionSummary()
    }

    fun setSessionWordIds(ids: List<Long>) {
        sessionWordIds = ids.distinct()
    }

    fun updateSessionSummary(summary: PracticeSessionSummary) {
        sessionSummary = summary
    }

    fun onPageVisible() {
        sessionTimer.start()
    }

    fun onPageHidden() {
        val durationMs = sessionTimer.pause()
        if (durationMs <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            practiceFacade.addPracticeDuration(durationMs)
        }
    }

    fun finishSession() {
        if (saved) return
        onPageHidden()
        val mode = sessionMode ?: return
        val totalDurationMs = sessionTimer.finish()
        if (!shouldSavePracticeSession(totalDurationMs, sessionSummary, sessionWordIds)) return
        saved = true
        val record = PracticeSessionRecord(
            id = 0L,
            date = "",
            mode = mode,
            entryType = sessionEntryType,
            entryCount = sessionEntryCount,
            durationMs = totalDurationMs,
            createdAt = System.currentTimeMillis(),
            wordIds = sessionWordIds,
            questionCount = sessionSummary.questionCount,
            completedCount = sessionSummary.completedCount,
            correctCount = sessionSummary.correctCount,
            submitCount = sessionSummary.submitCount
        )
        viewModelScope.launch(Dispatchers.IO) {
            practiceFacade.saveSessionRecord(record)
        }
    }
}

internal fun shouldSavePracticeSession(
    totalDurationMs: Long,
    summary: PracticeSessionSummary,
    wordIds: List<Long>
): Boolean {
    return totalDurationMs > 0L &&
        summary.questionCount > 0 &&
        wordIds.isNotEmpty()
}

internal fun buildPracticeSelectionKey(selectedIds: LongArray?, randomCount: Int): String {
    return selectedIds?.joinToString(separator = ",") ?: "random:$randomCount"
}

internal fun buildPracticeSessionKey(
    mode: PracticeMode,
    entryType: PracticeEntryType,
    entryCount: Int,
    selectedIds: LongArray?,
    randomCount: Int
): String {
    return "${mode.name}_${entryType.name}_${entryCount.coerceAtLeast(0)}_${
        buildPracticeSelectionKey(selectedIds, randomCount)
    }"
}
