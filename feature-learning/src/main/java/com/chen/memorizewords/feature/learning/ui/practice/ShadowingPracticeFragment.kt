package com.chen.memorizewords.feature.learning.ui.practice

import android.Manifest
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeShadowingBinding
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import com.chen.memorizewords.feature.learning.ui.speech.speechOutputFileOrNull
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@AndroidEntryPoint
class ShadowingPracticeFragment :
    BaseVmDbFragment<ShadowingPracticeViewModel, FragmentPracticeShadowingBinding>() {

    private enum class PlaybackTarget {
        REFERENCE,
        MINE
    }

    private enum class PlaybackControlOrigin {
        WAVE,
        REFERENCE_BUTTON,
        MINE_BUTTON,
        AUTO
    }

    private sealed class PlaybackSource(val target: PlaybackTarget) {
        data class Reference(val output: SpeechAudioOutput) : PlaybackSource(PlaybackTarget.REFERENCE) {
            override val key: String = "reference_${output.hashCode()}"
        }

        data class Mine(val filePath: String) : PlaybackSource(PlaybackTarget.MINE) {
            override val key: String = "mine_$filePath"
        }

        abstract val key: String
    }

    override val viewModel: ShadowingPracticeViewModel by viewModels()
    private val sessionViewModel: PracticeSessionViewModel by activityViewModels()

    private val wavRecorder = ShadowingWavRecorder()
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlaybackSource: PlaybackSource? = null
    private var currentPlaybackOrigin: PlaybackControlOrigin? = null
    private var isPlaybackPrepared: Boolean = false
    private var autoPlayJob: Job? = null
    private var playbackProgressJob: Job? = null
    private val currentWaveSamples = mutableListOf<Int>()
    private val managedRecordingFiles = mutableListOf<File>()
    private var currentRecordingFile: File? = null
    private var recordingStartedAtMs: Long = 0L
    private var hasRequestedPermission: Boolean = false
    private var permissionBlocked: Boolean = false
    private var lastAutoPlaySequenceId: Int = 0
    private var preservePlaybackOnAutoCancel: Boolean = false
    private val waveformPeakCache = mutableMapOf<String, List<WaveformPeak>>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedPermission = true
        refreshPermissionState()
        if (granted) {
            handleRecordAction()
        } else {
            val message = if (permissionBlocked) {
                getString(R.string.practice_shadowing_open_settings)
            } else {
                getString(R.string.practice_permission_denied)
            }
            showToast(message)
        }
    }

    override fun setLayout(): Int = R.layout.fragment_practice_shadowing

    override fun initView(savedInstanceState: Bundle?) {
        bindActions()
        refreshPermissionState()
        databind.tvTitle.text = getString(R.string.practice_shadowing_title_compare)
        databind.waveformView.clearWaveform()
        val selectedIds = arguments?.getLongArray(PracticeActivity.ARG_SELECTED_WORD_IDS)
        val randomCount = arguments?.getInt(PracticeActivity.ARG_RANDOM_COUNT, 20) ?: 20
        viewModel.loadWithSelection(selectedIds, randomCount)
    }

    override fun createObserver() {
        observeUi()
    }

    override fun onUiEffect(effect: UiEffect) {
        when (effect) {
            is ShadowingPracticeDoneEffect -> showDonePage(effect)
            else -> super.onUiEffect(effect)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onDestroyView() {
        abortRecording()
        cancelAutoPlayback()
        releasePlayer(resetProgress = true)
        clearManagedRecordingFiles()
        super.onDestroyView()
    }

    private fun bindActions() {
        databind.btnBack.setOnClickListener { viewModel.onBackClick() }
        databind.cardPlayReference.setOnClickListener { toggleReferencePlayback() }
        databind.cardPlayMine.setOnClickListener { toggleMinePlayback() }
        databind.cardRecord.setOnClickListener { handleRecordAction() }
        databind.btnEvaluatePronunciation.setOnClickListener {
            viewModel.evaluateLatestAttempt()
        }
        databind.btnNextWord.setOnClickListener { handleNextAction() }
        databind.cardWavePlayOverlay.setOnClickListener { handleWaveOverlayPlay() }
        databind.tvQuotaAction.setOnClickListener {
            showToast(getString(R.string.practice_shadowing_unlock_soon))
        }
        databind.waveformView.onSeekRequested = { fraction ->
            seekPlayback(fraction)
        }
    }

    private fun showDonePage(effect: ShadowingPracticeDoneEffect) {
        abortRecording()
        cancelAutoPlayback()
        releasePlayer(resetProgress = true)
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.practice_fragment_container,
                ShadowingPracticeDoneFragment.newInstance(
                    questionCount = effect.questionCount,
                    completedCount = effect.completedCount,
                    correctCount = effect.correctCount,
                    submitCount = effect.submitCount
                ),
                ShadowingPracticeDoneFragment.TAG
            )
            .commit()
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                        sessionViewModel.updateSessionSummary(state.summary)
                        maybeStartAutoPlayback(state)
                    }
                }
                launch {
                    viewModel.sessionWordIds.collect { ids ->
                        if (ids.isNotEmpty()) {
                            sessionViewModel.setSessionWordIds(ids)
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: ShadowingPracticeViewModel.ShadowingUiState) {
        databind.tvProgress.text = state.progressText
        databind.tvWord.text = state.word
        databind.tvPhoneticChip.text = state.phoneticText
        databind.tvMeaning.text = state.meaningText
        databind.tvWaveTitle.text = state.statusTitle
        databind.tvWaveSubtitle.text = state.statusSubtitle
        databind.tvFeedbackTitle.text = state.feedbackTitle
        databind.tvFeedbackMessage.text = state.feedbackMessage
        databind.layoutQuotaBanner.isVisible = false

        val latestAttempt = state.latestAttempt
        databind.tvRecognized.isVisible = latestAttempt?.recognizedText?.isNotBlank() == true
        databind.tvRecognized.text = latestAttempt?.let {
            getString(R.string.practice_shadowing_recognized_format, it.recognizedText)
        }.orEmpty()
        databind.tvScoreDetail.text = latestAttempt?.let { attempt ->
            if (
                attempt.totalScore != null &&
                attempt.pronunciationScore != null &&
                attempt.fluencyScore != null
            ) {
                getString(
                    R.string.practice_shadowing_score_detail_format,
                    attempt.totalScore,
                    attempt.pronunciationScore,
                    attempt.fluencyScore
                )
            } else {
                attempt.scoreText
            }
        }.orEmpty()
        databind.tvScoreDetail.isVisible =
            latestAttempt?.hasEvaluation == true && !state.isEvaluatingLatestAttempt
        databind.btnEvaluatePronunciation.isVisible = state.canEvaluateLatestAttempt
        databind.btnEvaluatePronunciation.isEnabled = state.canEvaluateLatestAttempt
        databind.tvScoreBreakdown.text = state.scoreBreakdownText
        databind.tvScoreBreakdown.isVisible =
            state.scoreBreakdownText.isNotBlank() && !state.isEvaluatingLatestAttempt
        databind.tvWeakPoints.text = latestAttempt?.weakPointText.orEmpty()
        databind.tvWeakPoints.isVisible =
            latestAttempt?.weakPointText?.isNotBlank() == true && !state.isEvaluatingLatestAttempt
        databind.tvAudioIssue.text = state.audioIssueText
        databind.tvAudioIssue.isVisible =
            state.audioIssueText.isNotBlank() && !state.isEvaluatingLatestAttempt
        databind.tvDetailNote.text = state.detailSourceNote
        databind.tvDetailNote.isVisible =
            state.detailSourceNote.isNotBlank() && !state.isEvaluatingLatestAttempt

        val waveformSamples = when {
            state.stage == ShadowingStage.RECORDING -> currentWaveSamples
            latestAttempt != null -> latestAttempt.waveformSamples
            else -> emptyList()
        }
        if (state.stage == ShadowingStage.RECORDING) {
            databind.waveformView.startLiveWave()
        } else {
            databind.waveformView.stopLiveWave()
            val audioFilePath = latestAttempt?.audioFilePath
            if (audioFilePath != null) {
                setWaveformFromFile(File(audioFilePath), fallbackSamples = waveformSamples)
            } else {
                databind.waveformView.clearWaveform()
            }
        }
        databind.cardWavePlayOverlay.isVisible =
            state.stage != ShadowingStage.RECORDING &&
                (state.speech != null || latestAttempt != null)

        renderHistory(state.attemptHistory)
        applyActionAvailability(state)
        updatePlaybackUi()
    }

    private fun renderHistory(history: List<ShadowingAttemptUi>) {
        databind.historyScroll.isVisible = history.size > 1
        databind.layoutHistoryContainer.removeAllViews()
        if (history.size <= 1) return
        history.forEachIndexed { index, item ->
            val chip = TextView(requireContext()).apply {
                text = "${item.title}  ${item.scoreText}"
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (item.isSelected) {
                            android.R.color.white
                        } else {
                            R.color.feature_learning_shadowing_text_primary
                        }
                    )
                )
                textSize = 13f
                background = ContextCompat.getDrawable(
                    requireContext(),
                    if (item.isSelected) {
                        R.drawable.feature_learning_bg_shadowing_history_selected
                    } else {
                        R.drawable.feature_learning_bg_shadowing_history
                    }
                )
                setPadding(14.dpToPx(requireContext()), 10.dpToPx(requireContext()), 14.dpToPx(requireContext()), 10.dpToPx(requireContext()))
                setOnClickListener {
                    databind.waveformView.stopLiveWave()
                    viewModel.selectAttempt(item.attemptId)
                    setWaveformFromFile(File(item.audioFilePath), fallbackSamples = item.waveformSamples)
                    togglePlayback(PlaybackSource.Mine(item.audioFilePath), PlaybackControlOrigin.WAVE)
                }
                alpha = if (item.isEvaluating) 0.72f else 1f
            }
            val params = LinearLayoutLayoutParamsFactory.wrapContent()
            params.marginEnd = if (index == history.lastIndex) 0 else 10.dpToPx(requireContext())
            databind.layoutHistoryContainer.addView(chip, params)
        }
    }

    private fun handleRecordAction() {
        val state = viewModel.uiState.value
        if (state.loading || state.isCompleted) return
        if (!hasRecordPermission()) {
            if (permissionBlocked) {
                openAppSettings()
                showToast(getString(R.string.practice_shadowing_open_settings))
            } else {
                hasRequestedPermission = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }
        if (state.stage == ShadowingStage.RECORDING) {
            stopRecordingAndEvaluate()
        } else {
            startRecording()
        }
    }

    private fun handleNextAction() {
        when (viewModel.uiState.value.stage) {
            ShadowingStage.RECORDING -> {
                showToast(getString(R.string.practice_stop_recording_first))
                return
            }

            else -> Unit
        }
        cancelAutoPlayback()
        releasePlayer(resetProgress = true)
        viewModel.nextWord()
    }

    private fun handleWaveOverlayPlay() {
        if (currentPlaybackOrigin == PlaybackControlOrigin.WAVE) {
            val current = currentPlaybackSource
            if (current != null) {
                togglePlayback(current, PlaybackControlOrigin.WAVE)
                return
            }
        }
        val state = viewModel.uiState.value
        val reference = state.speech?.audioOutput
        val minePath = state.latestAttempt?.audioFilePath
        when {
            reference != null && minePath != null -> {
                startWaveComparisonPlayback(reference, minePath)
            }

            minePath != null -> {
                togglePlayback(PlaybackSource.Mine(minePath), PlaybackControlOrigin.WAVE)
            }

            reference != null -> {
                togglePlayback(PlaybackSource.Reference(reference), PlaybackControlOrigin.WAVE)
            }
        }
    }

    private fun toggleReferencePlayback() {
        if (viewModel.uiState.value.stage == ShadowingStage.RECORDING) {
            return
        }
        val output = viewModel.uiState.value.speech?.audioOutput
        if (output == null) {
            showToast(getString(R.string.practice_tts_not_ready))
            return
        }
        togglePlayback(PlaybackSource.Reference(output), PlaybackControlOrigin.REFERENCE_BUTTON)
    }

    private fun toggleMinePlayback() {
        if (viewModel.uiState.value.stage == ShadowingStage.RECORDING) {
            return
        }
        val filePath = viewModel.uiState.value.latestAttempt?.audioFilePath
        if (filePath.isNullOrBlank()) {
            showToast(getString(R.string.practice_please_record_first))
            return
        }
        togglePlayback(PlaybackSource.Mine(filePath), PlaybackControlOrigin.MINE_BUTTON)
    }

    private fun togglePlayback(source: PlaybackSource, origin: PlaybackControlOrigin) {
        val current = currentPlaybackSource
        val sameSource = current?.key == source.key && mediaPlayer != null && isPlaybackPrepared
        cancelAutoPlayback(preservePlayback = sameSource)
        if (sameSource) {
            val player = mediaPlayer ?: return
            val sameOrigin = currentPlaybackOrigin == origin
            currentPlaybackOrigin = origin
            if (player.isPlaying && sameOrigin) {
                player.pause()
                stopPlaybackProgressUpdates()
                if (current?.target == PlaybackTarget.REFERENCE) {
                    restoreUserWaveformIfAvailable()
                }
            } else {
                applyPlaybackWaveform(source)
                if (player.duration > 0) {
                    databind.waveformView.setPlaybackProgress(
                        player.currentPosition.toFloat() / player.duration
                    )
                }
                player.start()
                startPlaybackProgressUpdates()
            }
            updatePlaybackUi()
            return
        }
        startPlayback(source = source, origin = origin, startFraction = null)
    }

    private fun startWaveComparisonPlayback(reference: SpeechAudioOutput, minePath: String) {
        cancelAutoPlayback()
        releasePlayer(resetProgress = true)
        autoPlayJob = viewLifecycleOwner.lifecycleScope.launch {
            playSourceSuspending(PlaybackSource.Reference(reference), PlaybackControlOrigin.WAVE)
            playSourceSuspending(PlaybackSource.Mine(minePath), PlaybackControlOrigin.WAVE)
            releasePlayer(resetProgress = true)
        }
    }

    private fun seekPlayback(fraction: Float) {
        if (viewModel.uiState.value.stage == ShadowingStage.RECORDING) {
            return
        }
        val preferredSource = when {
            currentPlaybackOrigin == PlaybackControlOrigin.WAVE -> currentPlaybackSource
            else -> {
                viewModel.uiState.value.latestAttempt?.audioFilePath?.let {
                    PlaybackSource.Mine(it)
                } ?: viewModel.uiState.value.speech?.audioOutput?.let {
                    PlaybackSource.Reference(it)
                }
            }
        } ?: return

        val sameSource = currentPlaybackSource?.key == preferredSource.key &&
            mediaPlayer != null &&
            isPlaybackPrepared
        cancelAutoPlayback(preservePlayback = sameSource)
        if (sameSource) {
            val player = mediaPlayer ?: return
            currentPlaybackOrigin = PlaybackControlOrigin.WAVE
            if (player.duration > 0) {
                player.seekTo((player.duration * fraction).toInt())
                if (!player.isPlaying) {
                    player.start()
                    startPlaybackProgressUpdates()
                }
                updatePlaybackUi()
            }
        } else {
            startPlayback(
                source = preferredSource,
                origin = PlaybackControlOrigin.WAVE,
                startFraction = fraction
            )
        }
    }

    private fun startRecording() {
        cancelAutoPlayback()
        releasePlayer(resetProgress = true)
        val outputFile = File(
            requireContext().cacheDir,
            "shadowing_${System.currentTimeMillis()}.wav"
        )
        currentRecordingFile = outputFile
        managedRecordingFiles += outputFile
        currentWaveSamples.clear()
        runCatching {
            wavRecorder.start(
                file = outputFile,
                onAmplitude = { amplitude ->
                    view?.post {
                        val normalized = normalizeWaveSample(amplitude)
                        currentWaveSamples += normalized
                        if (currentWaveSamples.size > MAX_WAVE_SAMPLE_COUNT) {
                            currentWaveSamples.removeAt(0)
                        }
                        databind.waveformView.updateLiveAmplitude(amplitude)
                    }
                },
                onError = {
                    view?.post {
                        currentRecordingFile = null
                        showToast(getString(R.string.practice_shadowing_recorder_failed))
                        viewModel.onRecordingFailed(getString(R.string.practice_shadowing_recorder_failed))
                    }
                }
            )
        }.getOrElse {
            currentRecordingFile = null
            showToast(getString(R.string.practice_shadowing_recorder_failed))
            viewModel.onRecordingFailed(getString(R.string.practice_shadowing_recorder_failed))
            return
        }
        recordingStartedAtMs = System.currentTimeMillis()
        databind.waveformView.startLiveWave()
        viewModel.onRecordingStarted()
    }

    private fun stopRecordingAndEvaluate() {
        databind.waveformView.stopLiveWave()
        val outputFile = currentRecordingFile
        currentRecordingFile = null
        recordingStartedAtMs = 0L
        val result = runCatching { wavRecorder.stop() }.getOrNull()
        if (result == null || outputFile == null || !outputFile.exists()) {
            showToast(getString(R.string.practice_shadowing_recording_missing))
            viewModel.onRecordingFailed(getString(R.string.practice_shadowing_recording_missing))
            return
        }
        val waveform = currentWaveSamples.toList()
        setWaveformFromFile(result.file, fallbackSamples = waveform)
        viewModel.onRecordingCompleted(result.file.absolutePath, waveform, result.durationMs)
    }

    private fun abortRecording() {
        wavRecorder.cancel()
        currentRecordingFile = null
        recordingStartedAtMs = 0L
    }

    private fun maybeStartAutoPlayback(state: ShadowingPracticeViewModel.ShadowingUiState) {
        if (state.autoPlaySequenceId == 0 || state.autoPlaySequenceId == lastAutoPlaySequenceId) {
            return
        }
        val reference = state.speech?.audioOutput ?: return
        val minePath = state.latestAttempt?.audioFilePath ?: return
        lastAutoPlaySequenceId = state.autoPlaySequenceId
        cancelAutoPlayback()
        autoPlayJob = viewLifecycleOwner.lifecycleScope.launch {
            playSourceSuspending(PlaybackSource.Reference(reference), PlaybackControlOrigin.AUTO)
            playSourceSuspending(PlaybackSource.Mine(minePath), PlaybackControlOrigin.AUTO)
            releasePlayer(resetProgress = true)
        }
    }

    private suspend fun playSourceSuspending(
        source: PlaybackSource,
        origin: PlaybackControlOrigin
    ) {
        suspendCancellableCoroutine<Unit> { continuation ->
            startPlayback(
                source = source,
                origin = origin,
                startFraction = null,
                onCompletion = {
                    if (continuation.isActive) continuation.resume(Unit)
                },
                onError = {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            )
            continuation.invokeOnCancellation {
                val preservePlayback = preservePlaybackOnAutoCancel
                preservePlaybackOnAutoCancel = false
                if (!preservePlayback && currentPlaybackSource?.key == source.key) {
                    releasePlayer(resetProgress = true)
                }
            }
        }
    }

    private fun startPlayback(
        source: PlaybackSource,
        origin: PlaybackControlOrigin,
        startFraction: Float?,
        onCompletion: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ) {
        releasePlayer(resetProgress = true)
        val player = MediaPlayer()
        currentPlaybackSource = source
        currentPlaybackOrigin = origin
        isPlaybackPrepared = false
        applyPlaybackWaveform(source)
        val prepared = when (source) {
            is PlaybackSource.Reference -> player.prepareSpeechOutputAsync(
                output = source.output,
                onPrepared = { preparedPlayer ->
                    isPlaybackPrepared = true
                    applyInitialSeek(preparedPlayer, startFraction)
                    preparedPlayer.setOnCompletionListener {
                        onCompletion?.invoke()
                        releasePlayer(resetProgress = true)
                    }
                    preparedPlayer.start()
                    startPlaybackProgressUpdates()
                    updatePlaybackUi()
                },
                onError = {
                    onError?.invoke()
                    releasePlayer(resetProgress = true)
                }
            )

            is PlaybackSource.Mine -> runCatching {
                player.setDataSource(source.filePath)
                player.setOnPreparedListener { preparedPlayer ->
                    isPlaybackPrepared = true
                    applyInitialSeek(preparedPlayer, startFraction)
                    preparedPlayer.start()
                    startPlaybackProgressUpdates()
                    updatePlaybackUi()
                }
                player.setOnCompletionListener {
                    onCompletion?.invoke()
                    releasePlayer(resetProgress = true)
                }
                player.setOnErrorListener { _, _, _ ->
                    onError?.invoke()
                    releasePlayer(resetProgress = true)
                    true
                }
                player.prepareAsync()
                true
            }.getOrElse {
                onError?.invoke()
                releasePlayer(resetProgress = true)
                false
            }
        }
        if (prepared) {
            mediaPlayer = player
        } else {
            runCatching { player.release() }
        }
        updatePlaybackUi()
    }

    private fun applyInitialSeek(player: MediaPlayer, fraction: Float?) {
        if (fraction == null || player.duration <= 0) return
        player.seekTo((player.duration * fraction).toInt())
    }

    private fun startPlaybackProgressUpdates() {
        stopPlaybackProgressUpdates()
        playbackProgressJob = viewLifecycleOwner.lifecycleScope.launch {
            while (mediaPlayer != null) {
                val player = mediaPlayer ?: break
                if (player.isPlaying && player.duration > 0) {
                    if (currentPlaybackOrigin == PlaybackControlOrigin.WAVE) {
                        databind.waveformView.setPlaybackProgress(
                            player.currentPosition.toFloat() / player.duration
                        )
                    }
                }
                updatePlaybackUi()
                delay(50L)
            }
        }
    }

    private fun stopPlaybackProgressUpdates() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
    }

    private fun releasePlayer(
        resetProgress: Boolean,
        restoreUserWaveform: Boolean = true
    ) {
        stopPlaybackProgressUpdates()
        val player = mediaPlayer
        mediaPlayer = null
        isPlaybackPrepared = false
        currentPlaybackSource = null
        currentPlaybackOrigin = null
        if (resetProgress) {
            databind.waveformView.setPlaybackProgress(0f)
        }
        runCatching {
            player?.stop()
        }
        runCatching {
            player?.release()
        }
        if (resetProgress && restoreUserWaveform) {
            restoreUserWaveformIfAvailable()
        }
        updatePlaybackUi()
    }

    private fun cancelAutoPlayback(preservePlayback: Boolean = false) {
        preservePlaybackOnAutoCancel = preservePlayback
        autoPlayJob?.cancel()
        autoPlayJob = null
    }

    private fun updatePlaybackUi() {
        val current = currentPlaybackSource?.target
        val origin = currentPlaybackOrigin
        val player = mediaPlayer
        val isPlaying = player?.isPlaying == true
        val waveIsPlaying = isPlaying && origin == PlaybackControlOrigin.WAVE

        databind.ivWavePlay.setImageResource(
            if (waveIsPlaying) {
                R.drawable.feature_learning_ic_pause
            } else {
                R.drawable.feature_learning_ic_play
            }
        )

        databind.cardPlayReference.alpha = when {
            !databind.cardPlayReference.isEnabled -> 0.42f
            current == PlaybackTarget.REFERENCE &&
                origin == PlaybackControlOrigin.REFERENCE_BUTTON -> 1f
            else -> 0.96f
        }
        databind.cardPlayMine.alpha = when {
            !databind.cardPlayMine.isEnabled -> 0.42f
            current == PlaybackTarget.MINE &&
                origin == PlaybackControlOrigin.MINE_BUTTON -> 1f
            else -> 0.96f
        }
    }

    private fun applyActionAvailability(state: ShadowingPracticeViewModel.ShadowingUiState) {
        val recording = state.stage == ShadowingStage.RECORDING
        val canPlayReference = !recording && state.speech != null
        val canPlayMine = !recording && state.latestAttempt != null
        val permissionBlockedNow = !hasRecordPermission() && permissionBlocked
        val recordLabel = when {
            permissionBlockedNow -> {
                getString(R.string.practice_shadowing_enable_permission)
            }

            recording -> getString(R.string.practice_shadowing_stop_record)
            state.latestAttempt != null -> getString(R.string.practice_shadowing_rerecord)
            else -> getString(R.string.practice_shadowing_record)
        }

        databind.cardPlayReference.isEnabled = canPlayReference
        databind.cardPlayMine.isEnabled = canPlayMine
        databind.cardRecord.isEnabled = !state.loading && !state.isCompleted
        databind.btnNextWord.isEnabled = !recording && !state.loading
        databind.btnNextWord.text = state.nextActionText
        databind.tvRecordLabel.text = recordLabel

        databind.cardPlayReference.alpha = if (canPlayReference) 1f else 0.42f
        databind.cardPlayMine.alpha = if (canPlayMine) 1f else 0.42f

        val recordStyle = when {
            permissionBlockedNow -> RecordButtonStyle(
                iconResId = R.drawable.feature_learning_ic_microphone,
                backgroundColorResId = R.color.feature_learning_shadowing_disabled,
                iconColorResId = R.color.feature_learning_shadowing_text_primary,
                elevationDp = 3
            )

            recording -> RecordButtonStyle(
                iconResId = R.drawable.feature_learning_ic_stop,
                backgroundColorResId = R.color.feature_learning_shadowing_record_recording_bg,
                iconColorResId = R.color.feature_learning_shadowing_record_icon_recording,
                elevationDp = 10
            )

            state.latestAttempt != null -> RecordButtonStyle(
                iconResId = R.drawable.feature_learning_ic_microphone,
                backgroundColorResId = R.color.feature_learning_shadowing_record_rerecord_bg,
                iconColorResId = R.color.feature_learning_shadowing_record_icon,
                elevationDp = 8
            )

            else -> RecordButtonStyle(
                iconResId = R.drawable.feature_learning_ic_microphone,
                backgroundColorResId = R.color.feature_learning_shadowing_record_bg,
                iconColorResId = R.color.feature_learning_shadowing_record_icon,
                elevationDp = 8
            )
        }
        databind.ivRecordAction.setImageResource(recordStyle.iconResId)
        databind.ivRecordAction.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), recordStyle.iconColorResId)
        )
        databind.cardRecord.setCardBackgroundColor(
            ContextCompat.getColor(requireContext(), recordStyle.backgroundColorResId)
        )
        databind.cardRecord.cardElevation = (recordStyle.elevationDp).dpToPx(requireContext()).toFloat()
    }

    private fun refreshPermissionState() {
        val granted = hasRecordPermission()
        permissionBlocked = !granted &&
            hasRequestedPermission &&
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        if (granted) {
            permissionBlocked = false
        }
        if (view != null) {
            applyActionAvailability(viewModel.uiState.value)
        }
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null)
        )
        startActivity(intent)
    }

    private fun clearManagedRecordingFiles() {
        managedRecordingFiles.forEach { file ->
            if (file.exists()) {
                runCatching { file.delete() }
            }
        }
        managedRecordingFiles.clear()
        waveformPeakCache.clear()
    }

    private fun normalizeWaveSample(amplitude: Int): Int {
        val normalized = (amplitude / 32767f * 100f).toInt()
        return normalized.coerceIn(10, 100)
    }

    private fun setWaveformFromFile(file: File, fallbackSamples: List<Int>) {
        val targetCount = waveformTargetPeakCount()
        val cacheKey = "${file.absolutePath}:$targetCount:${file.lastModified()}:${file.length()}"
        val peaks = waveformPeakCache.getOrPut(cacheKey) {
            runCatching {
                ShadowingWaveformExtractor.extractPeaks(file, targetCount)
            }.getOrDefault(emptyList())
        }
        if (peaks.isNotEmpty()) {
            databind.waveformView.setWaveformPeaks(peaks)
        } else if (fallbackSamples.isNotEmpty()) {
            databind.waveformView.setWaveformSamples(fallbackSamples)
        } else {
            databind.waveformView.clearWaveform()
        }
    }

    private fun restoreUserWaveformIfAvailable(): Boolean {
        if (view == null || viewModel.uiState.value.stage == ShadowingStage.RECORDING) {
            return false
        }
        val attempt = viewModel.uiState.value.latestAttempt ?: return false
        val audioFile = File(attempt.audioFilePath)
        if (!audioFile.exists()) {
            if (attempt.waveformSamples.isNotEmpty()) {
                databind.waveformView.setWaveformSamples(attempt.waveformSamples)
                databind.waveformView.setPlaybackProgress(0f)
                return true
            }
            return false
        }
        setWaveformFromFile(audioFile, fallbackSamples = attempt.waveformSamples)
        databind.waveformView.setPlaybackProgress(0f)
        return true
    }

    private fun applyPlaybackWaveform(source: PlaybackSource) {
        when (source) {
            is PlaybackSource.Reference -> {
                val referenceFile = speechOutputFileOrNull(source.output)
                if (referenceFile != null && referenceFile.exists()) {
                    setWaveformFromFile(referenceFile, fallbackSamples = REFERENCE_FALLBACK_WAVEFORM)
                } else {
                    databind.waveformView.setWaveformSamples(REFERENCE_FALLBACK_WAVEFORM)
                }
            }

            is PlaybackSource.Mine -> {
                setWaveformFromFile(
                    file = File(source.filePath),
                    fallbackSamples = waveformSamplesForFile(source.filePath)
                )
            }
        }
        databind.waveformView.setPlaybackProgress(0f)
    }

    private fun waveformSamplesForFile(filePath: String): List<Int> {
        val state = viewModel.uiState.value
        val latestAttempt = state.latestAttempt
        if (latestAttempt?.audioFilePath == filePath) {
            return latestAttempt.waveformSamples
        }
        return state.attemptHistory.firstOrNull { it.audioFilePath == filePath }
            ?.waveformSamples
            .orEmpty()
    }

    private fun waveformTargetPeakCount(): Int {
        val viewWidth = databind.waveformView.width
        val displayWidth = if (viewWidth > 0) {
            viewWidth - databind.waveformView.paddingLeft - databind.waveformView.paddingRight
        } else {
            resources.displayMetrics.widthPixels - 48.dpToPx(requireContext())
        }
        return displayWidth.coerceAtLeast(160)
    }

    private fun showToast(message: String) {
        viewModel.showToast(message)
    }

    private object LinearLayoutLayoutParamsFactory {
        fun wrapContent(): android.widget.LinearLayout.LayoutParams {
            return android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private data class RecordButtonStyle(
        val iconResId: Int,
        val backgroundColorResId: Int,
        val iconColorResId: Int,
        val elevationDp: Int
    )

    companion object {
        private const val MAX_WAVE_SAMPLE_COUNT = 96
        private val REFERENCE_FALLBACK_WAVEFORM = listOf(
            18, 24, 20, 30, 38, 34, 28, 42,
            50, 44, 36, 30, 26, 34, 42, 32,
            24, 20, 28, 36, 46, 40, 30, 22
        )
    }
}
