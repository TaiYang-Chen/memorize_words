package com.chen.memorizewords.feature.learning.ui.practice

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeAudioLoopBinding
import com.chen.memorizewords.feature.learning.ui.speech.audioOutputOrNull
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import com.chen.memorizewords.domain.practice.AudioLoopPlaybackMode
import com.chen.memorizewords.domain.practice.usecase.SynthesizeSpeechUseCase
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import com.chen.memorizewords.domain.practice.speech.SpeechTask
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AudioLoopPracticeFragment :
    BaseVmDbFragment<AudioLoopPracticeViewModel, FragmentPracticeAudioLoopBinding>() {

    private enum class DelayAction {
        NONE,
        START_NEXT_REPEAT,
        COMPLETE_ENTRY
    }

    private enum class PlaybackStepState {
        IDLE,
        PLAYING_AUDIO,
        WAITING_DELAY,
        PAUSED_AUDIO,
        PAUSED_DELAY
    }

    override val viewModel: AudioLoopPracticeViewModel by viewModels()

    @Inject
    lateinit var synthesizeSpeech: SynthesizeSpeechUseCase

    private var mediaPlayer: MediaPlayer? = null
    private var playbackActionJob: Job? = null
    private var delayJob: Job? = null
    private var currentPlan: AudioLoopPlaybackPlan? = null
    private var currentCueIndex: Int = 0
    private var currentRepeat: Int = 1
    private var pausedPositionMs: Int = 0
    private var delayRemainingMs: Long = 0L
    private var delayStartedAtMs: Long = 0L
    private var delayAction: DelayAction = DelayAction.NONE
    private var playbackStepState: PlaybackStepState = PlaybackStepState.IDLE
    private var currentEntryHadSuccessfulCue: Boolean = false
    private val audioOutputCache = mutableMapOf<String, SpeechAudioOutput>()
    private var playlistDialog: AlertDialog? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (viewModel.uiState.value.playerState == AudioLoopPlayerState.PLAYING) {
                    pausePlayback(notifyViewModel = false)
                    viewModel.onPlaybackInterrupted()
                }
            }
        }
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    override fun setLayout(): Int = R.layout.fragment_practice_audio_loop

    override fun initView(savedInstanceState: Bundle?) {
        val selectedIds = arguments?.getLongArray(PracticeActivity.ARG_SELECTED_WORD_IDS)
        val randomCount = arguments?.getInt(PracticeActivity.ARG_RANDOM_COUNT, 20) ?: 20
        bindActions()
        viewModel.loadWithSelection(selectedIds, randomCount)
    }

    override fun createObserver() {
        observeState()
        observeCommands()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onPageVisible()
    }

    override fun onPause() {
        if (viewModel.uiState.value.playerState == AudioLoopPlayerState.PLAYING) {
            pausePlayback(notifyViewModel = false)
            viewModel.onPlaybackInterrupted()
        }
        viewModel.onPageHidden()
        super.onPause()
    }

    override fun onStop() {
        if (requireActivity().isFinishing) {
            viewModel.finishSession()
        }
        super.onStop()
    }

    override fun onDestroyView() {
        playlistDialog?.dismiss()
        playlistDialog = null
        stopPlayback()
        playbackActionJob?.cancel()
        playbackActionJob = null
        audioOutputCache.clear()
        super.onDestroyView()
    }

    private fun bindActions() {
        databind.btnBack.setOnClickListener { viewModel.onBackClick() }
        databind.cardPlayPause.setOnClickListener { viewModel.onPlayPauseClick() }
        databind.btnPrevious.setOnClickListener { viewModel.onPreviousClick() }
        databind.btnNext.setOnClickListener { viewModel.onNextClick() }
        databind.btnSettings.setOnClickListener { openSettingsDialog() }
        databind.btnPlaylist.setOnClickListener { openPlaylistDialog() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun observeCommands() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.commands.collect { command ->
                    when (command) {
                        is AudioLoopPlaybackCommand.Start -> {
                            launchPlaybackAction {
                                handleStartCommand(command)
                            }
                        }

                        AudioLoopPlaybackCommand.Pause -> {
                            playbackActionJob?.cancel()
                            playbackActionJob = null
                            pausePlayback(notifyViewModel = false)
                        }

                        AudioLoopPlaybackCommand.Stop -> {
                            playbackActionJob?.cancel()
                            playbackActionJob = null
                            stopPlayback()
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: AudioLoopUiState) {
        val entry = state.currentEntry
        val hasContent = entry != null
        val displaySettings = when (state.playerState) {
            AudioLoopPlayerState.PLAYING,
            AudioLoopPlayerState.PAUSED -> state.playbackSettings

            else -> state.persistedSettings
        }

        databind.tvProgress.text = getString(
            R.string.feature_learning_audio_loop_progress,
            if (hasContent) state.currentIndex + 1 else 0,
            state.entries.size
        )
        databind.tvWord.text = entry?.word.orEmpty()
        databind.tvWord.isVisible = hasContent

        databind.tvPhoneticChip.isVisible =
            hasContent && displaySettings.showPhonetic && !entry?.phonetic.isNullOrBlank()
        databind.tvPhoneticChip.text = entry?.phonetic.orEmpty()

        val showExample = hasContent &&
            displaySettings.playbackMode == AudioLoopPlaybackMode.WORD_WITH_EXAMPLE &&
            !entry?.exampleSentence.isNullOrBlank()
        databind.tvSupporting.text = when {
            !hasContent -> ""
            showExample -> getString(
                R.string.feature_learning_audio_loop_supporting_example,
                entry?.exampleSentence.orEmpty()
            )

            else -> getString(
                R.string.feature_learning_audio_loop_supporting_word,
                entry?.word?.lowercase(Locale.US).orEmpty()
            )
        }
        databind.tvSupporting.isVisible = hasContent

        databind.tvSupportingTranslation.isVisible =
            showExample && !entry?.exampleTranslation.isNullOrBlank()
        databind.tvSupportingTranslation.text = if (showExample) {
            getString(
                R.string.feature_learning_audio_loop_supporting_example_translation,
                entry?.exampleTranslation.orEmpty()
            )
        } else {
            ""
        }

        databind.tvMeaning.isVisible =
            hasContent && displaySettings.showMeaning && !entry?.meaning.isNullOrBlank()
        databind.tvMeaning.text = entry?.meaning.orEmpty()

        databind.tvEmptyTitle.isVisible = !hasContent
        databind.tvEmptyHint.isVisible = !hasContent

        databind.tvStatus.text = when (state.playerState) {
            AudioLoopPlayerState.EMPTY -> getString(R.string.feature_learning_audio_loop_status_empty)
            AudioLoopPlayerState.WAITING -> getString(R.string.feature_learning_audio_loop_status_waiting)
            AudioLoopPlayerState.PLAYING -> getString(R.string.feature_learning_audio_loop_status_playing)
            AudioLoopPlayerState.PAUSED -> getString(R.string.feature_learning_audio_loop_status_paused)
        }
        databind.tvStatusDetail.text = buildStatusDetail(state, displaySettings)
        databind.ivPlayPause.setImageResource(
            if (state.playerState == AudioLoopPlayerState.PLAYING) {
                R.drawable.feature_learning_ic_pause
            } else {
                R.drawable.feature_learning_ic_play
            }
        )

        databind.btnPlaylist.isEnabled = hasContent
        databind.btnPrevious.isEnabled = hasContent && state.currentIndex > 0
        databind.btnNext.isEnabled = hasContent && state.currentIndex < state.entries.lastIndex
        databind.cardPlayPause.isEnabled = hasContent && !state.loading
        databind.btnSettings.isEnabled = true

        updateEnabledAlpha(databind.btnPlaylist, databind.btnPlaylist.isEnabled)
        updateEnabledAlpha(databind.btnPrevious, databind.btnPrevious.isEnabled)
        updateEnabledAlpha(databind.btnNext, databind.btnNext.isEnabled)
        databind.cardPlayPause.alpha = if (databind.cardPlayPause.isEnabled) 1f else 0.42f
    }

    private fun buildStatusDetail(
        state: AudioLoopUiState,
        displaySettings: com.chen.memorizewords.domain.practice.PracticeSettings
    ): String {
        val modeText = if (displaySettings.playbackMode == AudioLoopPlaybackMode.WORD_WITH_EXAMPLE) {
            getString(R.string.feature_learning_audio_loop_mode_word_with_example)
        } else {
            getString(R.string.feature_learning_audio_loop_mode_word_only)
        }
        val repeatText = if (state.currentRepeat > 0) {
            getString(
                R.string.feature_learning_audio_loop_repeat_progress,
                state.currentRepeat,
                state.totalRepeats.coerceAtLeast(1)
            )
        } else {
            getString(
                R.string.feature_learning_audio_loop_repeat_idle,
                state.totalRepeats.coerceAtLeast(1)
            )
        }
        val intervalText = getString(
            R.string.feature_learning_audio_loop_settings_interval_value,
            displaySettings.intervalSeconds
        )
        return listOf(modeText, repeatText, intervalText).joinToString(separator = " | ")
    }

    private suspend fun handleStartCommand(command: AudioLoopPlaybackCommand.Start) {
        if (command.resumeIfPossible && canResumePlan(command.plan)) {
            resumePlayback(command.plan)
            return
        }
        startNewPlan(command.plan)
    }

    private fun canResumePlan(plan: AudioLoopPlaybackPlan): Boolean {
        return currentPlan == plan &&
            (playbackStepState == PlaybackStepState.PAUSED_AUDIO ||
                playbackStepState == PlaybackStepState.PAUSED_DELAY)
    }

    private suspend fun startNewPlan(plan: AudioLoopPlaybackPlan) {
        stopPlayback()
        currentPlan = plan
        currentCueIndex = 0
        currentRepeat = 1
        pausedPositionMs = 0
        delayRemainingMs = 0L
        delayAction = DelayAction.NONE
        currentEntryHadSuccessfulCue = false
        viewModel.onPlaybackRepeatStarted(
            entryId = plan.entryId,
            repeatNumber = currentRepeat,
            settingsSnapshot = plan.settingsSnapshot
        )
        playCurrentCue(startPositionMs = 0)
    }

    private suspend fun resumePlayback(plan: AudioLoopPlaybackPlan) {
        if (!requestAudioFocus()) {
            viewModel.onPlaybackPaused()
            return
        }
        when (playbackStepState) {
            PlaybackStepState.PAUSED_AUDIO -> {
                playCurrentCue(startPositionMs = pausedPositionMs)
            }

            PlaybackStepState.PAUSED_DELAY -> {
                startDelay(delayRemainingMs)
            }

            else -> startNewPlan(plan)
        }
    }

    private suspend fun playCurrentCue(startPositionMs: Int) {
        val plan = currentPlan ?: return
        val cue = plan.cues.getOrNull(currentCueIndex) ?: run {
            finishCurrentEntry()
            return
        }
        if (!requestAudioFocus()) {
            viewModel.onPlaybackPaused()
            return
        }
        val output = resolveAudioOutput(cue)
        if (output == null) {
            onCueFinished()
            return
        }
        startPlayer(output, startPositionMs)
    }

    private suspend fun resolveAudioOutput(cue: AudioLoopPlaybackCue): SpeechAudioOutput? {
        audioOutputCache[cue.key]?.let { return it }
        if (cue.text.isBlank()) return null
        val task = if (cue.text.contains(' ')) {
            SpeechTask.SynthesizeSentence(
                text = cue.text,
                locale = cue.locale
            )
        } else {
            SpeechTask.SynthesizeWord(text = cue.text)
        }
        val output = synthesizeSpeech(task).audioOutputOrNull()
        if (output is SpeechAudioOutput.StreamOutput) return null
        if (output != null) {
            audioOutputCache[cue.key] = output
        }
        return output
    }

    private fun startPlayer(
        output: SpeechAudioOutput,
        startPositionMs: Int
    ) {
        releasePlayer()
        val player = MediaPlayer()
        val prepared = player.prepareSpeechOutputAsync(
            output = output,
            onPrepared = { preparedPlayer ->
                mediaPlayer = preparedPlayer
                playbackStepState = PlaybackStepState.PLAYING_AUDIO
                currentEntryHadSuccessfulCue = true
                preparedPlayer.setOnCompletionListener {
                    releasePlayer()
                    launchPlaybackAction {
                        onCueFinished()
                    }
                }
                if (startPositionMs > 0) {
                    preparedPlayer.seekTo(startPositionMs)
                }
                pausedPositionMs = 0
                preparedPlayer.start()
            },
            onError = {
                releasePlayer()
                launchPlaybackAction {
                    onCueFinished()
                }
            }
        )
        if (!prepared) {
            releasePlayer()
            launchPlaybackAction {
                onCueFinished()
            }
        } else {
            mediaPlayer = player
        }
    }

    private suspend fun onCueFinished() {
        val plan = currentPlan ?: return
        val hasNextCue = currentCueIndex < plan.cues.lastIndex
        if (hasNextCue) {
            currentCueIndex += 1
            playCurrentCue(startPositionMs = 0)
            return
        }

        val hasNextRepeat = currentRepeat < plan.settingsSnapshot.playTimes.coerceAtLeast(1)
        if (hasNextRepeat) {
            currentRepeat += 1
            currentCueIndex = 0
            delayAction = DelayAction.START_NEXT_REPEAT
            startDelay(plan.settingsSnapshot.intervalSeconds * 1000L)
            return
        }

        delayAction = DelayAction.COMPLETE_ENTRY
        startDelay(plan.settingsSnapshot.intervalSeconds * 1000L)
    }

    private fun startDelay(durationMs: Long) {
        val plan = currentPlan ?: return
        if (durationMs <= 0L) {
            when (delayAction) {
                DelayAction.START_NEXT_REPEAT -> {
                    viewModel.onPlaybackRepeatStarted(
                        entryId = plan.entryId,
                        repeatNumber = currentRepeat,
                        settingsSnapshot = plan.settingsSnapshot
                    )
                    launchPlaybackAction {
                        playCurrentCue(startPositionMs = 0)
                    }
                }

                DelayAction.COMPLETE_ENTRY -> finishCurrentEntry()
                DelayAction.NONE -> Unit
            }
            return
        }
        delayJob?.cancel()
        delayRemainingMs = durationMs
        delayStartedAtMs = SystemClock.elapsedRealtime()
        playbackStepState = PlaybackStepState.WAITING_DELAY
        delayJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(durationMs)
            delayRemainingMs = 0L
            delayStartedAtMs = 0L
            when (delayAction) {
                DelayAction.START_NEXT_REPEAT -> {
                    viewModel.onPlaybackRepeatStarted(
                        entryId = plan.entryId,
                        repeatNumber = currentRepeat,
                        settingsSnapshot = plan.settingsSnapshot
                    )
                    launchPlaybackAction {
                        playCurrentCue(startPositionMs = 0)
                    }
                }

                DelayAction.COMPLETE_ENTRY -> finishCurrentEntry()
                DelayAction.NONE -> Unit
            }
        }
    }

    private fun finishCurrentEntry() {
        val plan = currentPlan ?: return
        val entryId = plan.entryId
        val entryHadSuccessfulCue = currentEntryHadSuccessfulCue
        clearPlaybackRuntime()
        if (entryHadSuccessfulCue) {
            viewModel.onPlaybackCompleted(entryId)
        } else {
            viewModel.onPlaybackEntryFailed(entryId)
        }
    }

    private fun pausePlayback(notifyViewModel: Boolean) {
        playbackActionJob?.cancel()
        playbackActionJob = null
        when (playbackStepState) {
            PlaybackStepState.PLAYING_AUDIO -> {
                pausedPositionMs = mediaPlayer?.currentPosition ?: 0
                releasePlayer()
                playbackStepState = PlaybackStepState.PAUSED_AUDIO
            }

            PlaybackStepState.WAITING_DELAY -> {
                val elapsed = (SystemClock.elapsedRealtime() - delayStartedAtMs).coerceAtLeast(0L)
                delayRemainingMs = (delayRemainingMs - elapsed).coerceAtLeast(0L)
                delayJob?.cancel()
                delayJob = null
                playbackStepState = PlaybackStepState.PAUSED_DELAY
            }

            else -> return
        }
        abandonAudioFocus()
        if (notifyViewModel) {
            viewModel.onPlaybackPaused()
        }
    }

    private fun stopPlayback() {
        playbackActionJob?.cancel()
        playbackActionJob = null
        delayJob?.cancel()
        delayJob = null
        releasePlayer()
        clearPlaybackRuntime()
    }

    private fun clearPlaybackRuntime() {
        currentPlan = null
        currentCueIndex = 0
        currentRepeat = 1
        pausedPositionMs = 0
        delayRemainingMs = 0L
        delayStartedAtMs = 0L
        delayAction = DelayAction.NONE
        playbackStepState = PlaybackStepState.IDLE
        currentEntryHadSuccessfulCue = false
        abandonAudioFocus()
    }

    private fun releasePlayer() {
        val player = mediaPlayer
        mediaPlayer = null
        runCatching {
            player?.stop()
        }
        runCatching {
            player?.release()
        }
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = ContextCompat.getSystemService(
            requireContext(),
            AudioManager::class.java
        ) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .build()
                .also { audioFocusRequest = it }
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = ContextCompat.getSystemService(
            requireContext(),
            AudioManager::class.java
        ) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun openSettingsDialog() {
        if (childFragmentManager.findFragmentByTag(AudioLoopSettingsDialogFragment.TAG) != null) return
        AudioLoopSettingsDialogFragment().show(
            childFragmentManager,
            AudioLoopSettingsDialogFragment.TAG
        )
    }

    private fun openPlaylistDialog() {
        val state = viewModel.uiState.value
        if (state.entries.isEmpty()) return
        playlistDialog?.dismiss()
        val items = state.entries.map { entry ->
            if (entry.meaning.isNotBlank()) {
                "${entry.word}\n${entry.meaning}"
            } else {
                entry.word
            }
        }.toTypedArray()
        playlistDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feature_learning_audio_loop_playlist_title)
            .setSingleChoiceItems(items, state.currentIndex) { dialog, which ->
                viewModel.onPlaylistItemSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchPlaybackAction(block: suspend () -> Unit) {
        playbackActionJob?.cancel()
        playbackActionJob = viewLifecycleOwner.lifecycleScope.launch {
            block()
        }
    }

    private fun updateEnabledAlpha(view: android.view.View, enabled: Boolean) {
        view.alpha = if (enabled) 1f else 0.34f
    }
}
