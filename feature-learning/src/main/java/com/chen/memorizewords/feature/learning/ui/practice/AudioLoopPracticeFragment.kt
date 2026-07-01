package com.chen.memorizewords.feature.learning.ui.practice

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.domain.practice.AudioLoopPlaybackMode
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.domain.word.model.word.PronunciationType
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeAudioLoopBinding
import com.chen.memorizewords.feature.learning.ui.practice.audioLoop.AudioLoopPlaybackService
import com.chen.memorizewords.feature.learning.ui.practice.audioLoop.AudioLoopPlaybackStore
import com.chen.memorizewords.feature.learning.ui.practice.audioLoop.AudioLoopServiceEntry
import com.chen.memorizewords.feature.learning.ui.practice.audioLoop.AudioLoopServicePlayerState
import com.chen.memorizewords.feature.learning.ui.practice.audioLoop.AudioLoopServiceQueue
import com.chen.memorizewords.feature.learning.ui.practice.audioLoop.AudioLoopServiceState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AudioLoopPracticeFragment :
    BaseVmDbFragment<AudioLoopPracticeViewModel, FragmentPracticeAudioLoopBinding>() {

    override val viewModel: AudioLoopPracticeViewModel by viewModels()

    private var pendingSettingsSync: Boolean = false
    private var pronunciationType: PronunciationType = PronunciationType.US

    override fun setLayout(): Int = R.layout.fragment_practice_audio_loop

    override fun initView(savedInstanceState: Bundle?) {
        val selectedIds = arguments?.getLongArray(PracticeActivity.ARG_SELECTED_WORD_IDS)
        val randomCount = arguments?.getInt(PracticeActivity.ARG_RANDOM_COUNT, 20) ?: 20
        bindActions()
        viewModel.loadWithSelection(selectedIds, randomCount)
    }

    override fun createObserver() {
        observeViewModelState()
        observePlaybackState()
        observeMessages()
    }

    override fun onDestroyView() {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroyView()
    }

    private fun bindActions() {
        databind.btnBack.setOnClickListener { viewModel.onBackClick() }
        databind.cardPlayPause.setOnClickListener { togglePlayback() }
        databind.btnPrevious.setOnClickListener {
            AudioLoopPlaybackService.start(requireContext(), AudioLoopPlaybackService.ACTION_PREVIOUS)
        }
        databind.btnNext.setOnClickListener {
            AudioLoopPlaybackService.start(requireContext(), AudioLoopPlaybackService.ACTION_NEXT)
        }
        databind.btnSettings.setOnClickListener { openSettingsDialog() }
        databind.btnPlaylist.setOnClickListener { openPlaylistDialog() }
        databind.audioLoopLanguageRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val nextType = when (checkedId) {
                R.id.btn_audio_loop_uk -> PronunciationType.UK
                else -> PronunciationType.US
            }
            if (nextType == pronunciationType) return@setOnCheckedChangeListener
            pronunciationType = nextType
            ensureQueueSynced(viewModel.uiState.value)
            renderCurrentContent()
        }
    }

    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    ensureQueueSynced(state)
                    applyKeepScreenOn(state.persistedSettings.keepScreenOn)
                    renderCurrentContent()
                }
            }
        }
    }

    private fun observePlaybackState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AudioLoopPlaybackStore.state.collect { serviceState ->
                    if (!serviceState.isActivelyPlaying() && pendingSettingsSync) {
                        ensureQueueSynced(viewModel.uiState.value, forcePending = true)
                    }
                    renderCurrentContent()
                }
            }
        }
    }

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AudioLoopPlaybackStore.messages.collect { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun ensureQueueSynced(state: AudioLoopUiState, forcePending: Boolean = false) {
        if (state.loading) return
        val entries = state.entries.map { it.toServiceEntry() }
        val entryKey = buildEntryKey(entries, state.entryType)
        val storeState = AudioLoopPlaybackStore.state.value
        val currentQueue = AudioLoopPlaybackStore.queue.value
        val queueEntryKey = currentQueue?.let { buildEntryKey(it.entries, it.entryType) }
        val entriesChanged = currentQueue == null || entryKey != queueEntryKey
        val settingsChanged = currentQueue == null || currentQueue.settings != state.persistedSettings
        if (!entriesChanged && !settingsChanged) {
            pendingSettingsSync = false
            return
        }
        if (!entriesChanged && settingsChanged && storeState.isActivelyPlaying() && !forcePending) {
            pendingSettingsSync = true
            return
        }
        if (forcePending && !pendingSettingsSync && !entriesChanged) return
        val resetPosition = entriesChanged
        pendingSettingsSync = false
        AudioLoopPlaybackStore.setQueue(
            AudioLoopServiceQueue(
                entries = entries,
                settings = state.persistedSettings,
                entryType = state.entryType
            ),
            resetPosition = resetPosition
        )
        AudioLoopPlaybackService.start(requireContext(), AudioLoopPlaybackService.ACTION_SET_QUEUE)
    }

    private fun buildEntryKey(
        entries: List<AudioLoopServiceEntry>,
        entryType: com.chen.memorizewords.domain.practice.PracticeEntryType
    ): String {
        return buildString {
            append(entries.joinToString(separator = ",") { "${it.id}:${it.speechLocale}" })
            append('|')
            append(entryType)
        }
    }

    private fun renderCurrentContent() {
        val state = viewModel.uiState.value
        val serviceState = AudioLoopPlaybackStore.state.value
        val serviceHasEntries = serviceState.entries.isNotEmpty()
        renderContent(
            entry = if (serviceHasEntries) {
                serviceState.currentEntry
            } else {
                state.currentEntry?.toServiceEntry()
            },
            settings = state.persistedSettings,
            serviceState = serviceState,
            fallbackEntriesSize = state.entries.size,
            fallbackIndex = state.currentIndex,
            isLoading = state.loading
        )
    }

    private fun renderContent(
        entry: AudioLoopServiceEntry?,
        settings: PracticeSettings,
        serviceState: AudioLoopServiceState,
        fallbackEntriesSize: Int,
        fallbackIndex: Int,
        isLoading: Boolean
    ) {
        val total = serviceState.entries.size.takeIf { it > 0 } ?: fallbackEntriesSize
        val index = if (serviceState.entries.isNotEmpty()) {
            serviceState.currentIndex.coerceIn(0, serviceState.entries.lastIndex)
        } else {
            fallbackIndex.coerceIn(0, (fallbackEntriesSize - 1).coerceAtLeast(0))
        }
        val displayEntry = if (serviceState.entries.isNotEmpty()) {
            serviceState.entries.getOrNull(index)
        } else {
            entry
        }
        val hasContent = displayEntry != null
        val isCurrentFailed = hasContent && serviceState.failedIds.contains(displayEntry?.id)
        val isCurrentCompleted = hasContent && serviceState.completedIds.contains(displayEntry?.id)
        databind.tvProgress.text = getString(
            R.string.feature_learning_audio_loop_progress,
            if (hasContent) index + 1 else 0,
            total
        )
        databind.tvWord.text = displayEntry?.word.orEmpty()
        databind.tvWord.isVisible = hasContent
        val selectedPhonetic = displayEntry?.selectedPhonetic().orEmpty()
        databind.layoutAudioLoopPhonetic.isVisible = hasContent && settings.showPhonetic && selectedPhonetic.isNotBlank()
        val checkedId = when (pronunciationType) {
            PronunciationType.US -> R.id.btn_audio_loop_us
            PronunciationType.UK -> R.id.btn_audio_loop_uk
        }
        if (databind.audioLoopLanguageRadioGroup.checkedRadioButtonId != checkedId) {
            databind.audioLoopLanguageRadioGroup.check(checkedId)
        }
        databind.tvPhoneticChip.text = selectedPhonetic

        val showExample = hasContent &&
            (settings.playbackMode == AudioLoopPlaybackMode.WORD_WITH_EXAMPLE ||
                settings.playbackMode == AudioLoopPlaybackMode.FULL_DETAIL) &&
            !displayEntry?.exampleSentence.isNullOrBlank()
        databind.tvSupporting.text = when {
            !hasContent -> ""
            showExample -> displayEntry?.exampleSentence.orEmpty()
            settings.playbackMode == AudioLoopPlaybackMode.DICTATION -> ""
            else -> displayEntry?.word.orEmpty()
        }
        databind.tvSupporting.isVisible = hasContent && databind.tvSupporting.text.isNotBlank()
        databind.tvSupportingTranslation.isVisible =
            showExample && !displayEntry?.exampleTranslation.isNullOrBlank()
        databind.tvSupportingTranslation.text = if (showExample) {
            displayEntry?.exampleTranslation.orEmpty()
        } else {
            ""
        }
        databind.tvMeaning.isVisible = hasContent &&
            (settings.showMeaning ||
                settings.playbackMode == AudioLoopPlaybackMode.WORD_WITH_MEANING ||
                settings.playbackMode == AudioLoopPlaybackMode.FULL_DETAIL) &&
            !displayEntry?.meaning.isNullOrBlank()
        databind.tvMeaning.text = displayEntry?.meaning.orEmpty()

        databind.tvEntryStatusChip.isVisible = hasContent && (isCurrentFailed || isCurrentCompleted || pendingSettingsSync)
        databind.tvEntryStatusChip.text = when {
            isCurrentFailed -> getString(R.string.feature_learning_audio_loop_entry_failed)
            pendingSettingsSync -> getString(R.string.feature_learning_audio_loop_settings_pending)
            isCurrentCompleted -> getString(R.string.feature_learning_audio_loop_entry_completed)
            else -> ""
        }

        databind.tvEmptyTitle.isVisible = !hasContent
        databind.tvEmptyHint.isVisible = !hasContent
        databind.tvEmptyTitle.text = getString(
            if (isLoading) {
                R.string.feature_learning_audio_loop_loading_title
            } else {
                R.string.feature_learning_audio_loop_empty_title
            }
        )
        databind.tvEmptyHint.text = getString(
            if (isLoading) {
                R.string.feature_learning_audio_loop_loading_hint
            } else {
                R.string.feature_learning_audio_loop_empty_hint
            }
        )
        databind.tvStatus.text = when {
            isLoading -> getString(R.string.feature_learning_audio_loop_status_loading)
            else -> when (serviceState.playerState) {
                AudioLoopServicePlayerState.EMPTY -> getString(R.string.feature_learning_audio_loop_status_empty)
                AudioLoopServicePlayerState.WAITING,
                AudioLoopServicePlayerState.STOPPED -> getString(R.string.feature_learning_audio_loop_status_waiting)
                AudioLoopServicePlayerState.PREPARING -> getString(R.string.feature_learning_audio_loop_status_preparing)
                AudioLoopServicePlayerState.PLAYING -> getString(R.string.feature_learning_audio_loop_status_playing)
                AudioLoopServicePlayerState.PAUSED -> getString(R.string.feature_learning_audio_loop_status_paused)
            }
        }
        databind.tvStatusDetail.text = buildStatusDetail(serviceState, settings)
        val isPlaying = serviceState.playerState == AudioLoopServicePlayerState.PLAYING ||
            serviceState.playerState == AudioLoopServicePlayerState.PREPARING
        databind.ivPlayPause.setImageResource(
            if (isPlaying) R.drawable.feature_learning_ic_pause else R.drawable.feature_learning_ic_play
        )
        databind.btnPlaylist.isEnabled = hasContent
        databind.btnPrevious.isEnabled = hasContent && index > 0
        databind.btnNext.isEnabled = hasContent && index < total - 1
        databind.cardPlayPause.isEnabled = hasContent && !viewModel.uiState.value.loading
        databind.btnSettings.isEnabled = true
        updateEnabledAlpha(databind.btnPlaylist, databind.btnPlaylist.isEnabled)
        updateEnabledAlpha(databind.btnPrevious, databind.btnPrevious.isEnabled)
        updateEnabledAlpha(databind.btnNext, databind.btnNext.isEnabled)
        databind.cardPlayPause.alpha = if (databind.cardPlayPause.isEnabled) 1f else 0.42f
    }

    private fun buildStatusDetail(
        state: AudioLoopServiceState,
        settings: PracticeSettings
    ): String {
        val modeText = when (settings.playbackMode) {
            AudioLoopPlaybackMode.WORD_ONLY -> getString(R.string.feature_learning_audio_loop_mode_word_only)
            AudioLoopPlaybackMode.WORD_WITH_EXAMPLE -> getString(R.string.feature_learning_audio_loop_mode_word_with_example)
            AudioLoopPlaybackMode.WORD_WITH_MEANING -> getString(R.string.feature_learning_audio_loop_mode_word_with_meaning)
            AudioLoopPlaybackMode.DICTATION -> getString(R.string.feature_learning_audio_loop_mode_dictation)
            AudioLoopPlaybackMode.FULL_DETAIL -> getString(R.string.feature_learning_audio_loop_mode_full_detail)
        }
        val repeat = if (state.currentRepeat > 0) state.currentRepeat else 0
        val repeatText = getString(
            R.string.feature_learning_audio_loop_repeat_progress,
            repeat,
            settings.playTimes.coerceAtLeast(1)
        )
        val speedText = getString(R.string.feature_learning_audio_loop_settings_speed_value, settings.playbackSpeed)
        return buildList {
            add(modeText)
            add(repeatText)
            add(speedText)
            if (pendingSettingsSync) {
                add(getString(R.string.feature_learning_audio_loop_settings_pending_detail))
            }
            state.lastMessage?.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString(separator = " | ")
    }

    private fun togglePlayback() {
        if (AudioLoopPlaybackStore.queue.value == null) {
            ensureQueueSynced(viewModel.uiState.value)
        }
        val action = if (
            AudioLoopPlaybackStore.state.value.playerState == AudioLoopServicePlayerState.PLAYING ||
            AudioLoopPlaybackStore.state.value.playerState == AudioLoopServicePlayerState.PREPARING
        ) {
            AudioLoopPlaybackService.ACTION_PAUSE
        } else {
            AudioLoopPlaybackService.ACTION_PLAY
        }
        AudioLoopPlaybackService.start(requireContext(), action)
    }

    private fun openSettingsDialog() {
        if (childFragmentManager.findFragmentByTag(AudioLoopSettingsDialogFragment.TAG) != null) return
        AudioLoopSettingsDialogFragment().show(childFragmentManager, AudioLoopSettingsDialogFragment.TAG)
    }

    private fun openPlaylistDialog() {
        val entries = AudioLoopPlaybackStore.state.value.entries
            .ifEmpty { viewModel.uiState.value.entries.map { it.toServiceEntry() } }
        if (entries.isEmpty()) return
        if (childFragmentManager.findFragmentByTag(AudioLoopPlaylistDialogFragment.TAG) != null) return
        AudioLoopPlaylistDialogFragment().show(childFragmentManager, AudioLoopPlaylistDialogFragment.TAG)
    }

    private fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun AudioLoopEntryUi.toServiceEntry(): AudioLoopServiceEntry {
        return AudioLoopServiceEntry(
            id = id,
            word = word,
            phonetic = selectedPhonetic(),
            phoneticUs = phoneticUs,
            phoneticUk = phoneticUk,
            speechLocale = pronunciationType.speechLocale(),
            meaning = meaning,
            exampleSentence = exampleSentence,
            exampleTranslation = exampleTranslation
        )
    }

    private fun AudioLoopEntryUi.selectedPhonetic(): String {
        return when (pronunciationType) {
            PronunciationType.US -> phoneticUs.ifBlank { phoneticUk }
            PronunciationType.UK -> phoneticUk.ifBlank { phoneticUs }
        }
    }

    private fun AudioLoopServiceEntry.selectedPhonetic(): String {
        return when (pronunciationType) {
            PronunciationType.US -> phoneticUs.ifBlank { phoneticUk }.ifBlank { phonetic }
            PronunciationType.UK -> phoneticUk.ifBlank { phoneticUs }.ifBlank { phonetic }
        }
    }

    private fun PronunciationType.speechLocale(): String {
        return when (this) {
            PronunciationType.US -> AUDIO_LOOP_SPEECH_LOCALE_US
            PronunciationType.UK -> AUDIO_LOOP_SPEECH_LOCALE_UK
        }
    }

    private fun updateEnabledAlpha(view: android.view.View, enabled: Boolean) {
        view.alpha = if (enabled) 1f else 0.34f
    }

    private fun AudioLoopServiceState.isActivelyPlaying(): Boolean {
        return playerState == AudioLoopServicePlayerState.PLAYING ||
            playerState == AudioLoopServicePlayerState.PREPARING
    }

    private companion object {
        const val AUDIO_LOOP_SPEECH_LOCALE_US = "en-US"
        const val AUDIO_LOOP_SPEECH_LOCALE_UK = "en-GB"
    }
}
