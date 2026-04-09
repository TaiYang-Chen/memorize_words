package com.chen.memorizewords.feature.home.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.query.word.WordReadFacade
import com.chen.memorizewords.domain.service.practice.PracticeFacade
import com.chen.memorizewords.feature.home.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PracticeRecordDetailViewModel @Inject constructor(
    private val practiceFacade: PracticeFacade,
    private val wordReadFacade: WordReadFacade,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    data class UiState(
        val recordTitle: String = "",
        val recordSubtitle: String = "",
        val summaryText: String = "",
        val words: List<PracticeRecordWordItemUi> = emptyList(),
        val isEmpty: Boolean = true
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadedId: Long? = null

    fun load(recordId: Long) {
        if (recordId <= 0L || loadedId == recordId) return
        loadedId = recordId
        viewModelScope.launch {
            val record = practiceFacade.getSessionRecord(recordId)
            if (record == null) {
                _uiState.value = UiState(isEmpty = true)
                return@launch
            }

            val items = withContext(Dispatchers.IO) {
                buildWordItems(record.wordIds)
            }

            _uiState.value = UiState(
                recordTitle = buildRecordTitle(record),
                recordSubtitle = buildRecordSubtitle(record),
                summaryText = buildSummaryText(record),
                words = items,
                isEmpty = items.isEmpty()
            )
        }
    }

    private suspend fun buildWordItems(wordIds: List<Long>): List<PracticeRecordWordItemUi> {
        if (wordIds.isEmpty()) return emptyList()
        val words = wordReadFacade.getWordsByIds(wordIds)
        if (words.isEmpty()) return emptyList()
        val wordMap = words.associateBy { it.id }
        return wordIds.mapNotNull { id ->
            val word = wordMap[id] ?: return@mapNotNull null
            val definitionText = buildDefinition(word.id)
            PracticeRecordWordItemUi(
                id = word.id,
                word = word.word,
                phonetic = buildPhonetic(word),
                definition = definitionText
            )
        }
    }

    private suspend fun buildDefinition(wordId: Long): String {
        val definitions = wordReadFacade.getWordDefinitions(wordId)
        if (definitions.isEmpty()) {
            return resourceProvider.getString(R.string.practice_record_word_definition_empty)
        }
        return definitions
            .take(3)
            .joinToString("；") { "${it.partOfSpeech.abbr} ${it.meaningChinese}" }
    }

    private fun buildPhonetic(word: Word): String {
        val us = word.phoneticUS?.trim().orEmpty()
        val uk = word.phoneticUK?.trim().orEmpty()
        val usText = us.takeIf { it.isNotBlank() }?.let { formatPhonetic("US", it) }
        val ukText = uk.takeIf { it.isNotBlank() }?.let { formatPhonetic("UK", it) }
        val merged = listOfNotNull(usText, ukText).joinToString(PRACTICE_RECORD_SEPARATOR)
        return if (merged.isNotBlank()) {
            merged
        } else {
            resourceProvider.getString(R.string.practice_record_word_phonetic_empty)
        }
    }

    private fun formatPhonetic(label: String, value: String): String {
        val cleaned = value.trim().trim('/')
        return "$label /$cleaned/"
    }

    private fun buildRecordTitle(record: PracticeSessionRecord): String {
        val modeLabel = when (record.mode) {
            PracticeMode.LISTENING -> resourceProvider.getString(R.string.home_practice_mode_listening)
            PracticeMode.SHADOWING -> resourceProvider.getString(R.string.home_practice_mode_shadowing)
            PracticeMode.SPELLING -> resourceProvider.getString(R.string.home_practice_mode_spelling)
            PracticeMode.AUDIO_LOOP -> resourceProvider.getString(R.string.home_practice_mode_audio_loop)
            PracticeMode.EXAM -> resourceProvider.getString(R.string.home_practice_mode_exam)
        }
        val entryLabel = when (record.entryType) {
            PracticeEntryType.SELF -> resourceProvider.getString(
                R.string.home_practice_entry_self,
                record.entryCount
            )

            PracticeEntryType.RANDOM -> resourceProvider.getString(
                R.string.home_practice_entry_random,
                record.entryCount
            )
        }
        return resourceProvider.getString(R.string.home_practice_record_title, modeLabel, entryLabel)
    }

    private fun buildRecordSubtitle(record: PracticeSessionRecord): String {
        val week = resolvePracticeRecordWeekLabel(
            recordDate = record.date,
            createdAt = record.createdAt
        )
        val time = formatPracticeRecordClockText(record.createdAt, Locale.getDefault())
        val duration = formatDuration(record.durationMs)
        val modeLabel = when (record.mode) {
            PracticeMode.LISTENING -> resourceProvider.getString(R.string.practice_record_mode_listening)
            PracticeMode.SHADOWING -> resourceProvider.getString(R.string.practice_record_mode_shadowing)
            PracticeMode.SPELLING -> resourceProvider.getString(R.string.practice_record_mode_spelling)
            PracticeMode.AUDIO_LOOP -> resourceProvider.getString(R.string.practice_record_mode_audio_loop)
            PracticeMode.EXAM -> resourceProvider.getString(R.string.practice_record_mode_exam)
        }
        return listOf(week, time, duration, modeLabel).joinToString(PRACTICE_RECORD_SEPARATOR)
    }

    private fun buildSummaryText(record: PracticeSessionRecord): String {
        return record.buildSpellingSummary(resourceProvider)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) {
            if (minutes > 0L) {
                resourceProvider.getString(R.string.home_duration_hours_minutes, hours, minutes)
            } else {
                resourceProvider.getString(R.string.home_duration_hours, hours)
            }
        } else {
            resourceProvider.getString(R.string.home_duration_minutes, minutes)
        }
    }
}
