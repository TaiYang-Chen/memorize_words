package com.chen.memorizewords.feature.learning.ui.practice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
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
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeShadowingBinding
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import com.chen.memorizewords.speech.api.SpeechAudioOutput
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

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlaybackSource: PlaybackSource? = null
    private var isPlaybackPrepared: Boolean = false
    private var autoPlayJob: Job? = null
    private var playbackProgressJob: Job? = null
    private var amplitudeJob: Job? = null
    private val currentWaveSamples = mutableListOf<Int>()
    private val managedRecordingFiles = mutableListOf<File>()
    private var currentRecordingFile: File? = null
    private var recordingStartedAtMs: Long = 0L
    private var hasRequestedPermission: Boolean = false
    private var permissionBlocked: Boolean = false
    private var lastAutoPlaySequenceId: Int = 0
    private var preservePlaybackOnAutoCancel: Boolean = false

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
        databind.waveformView.setWaveformSamples(defaultWaveformSamples())
        val selectedIds = arguments?.getLongArray(PracticeActivity.ARG_SELECTED_WORD_IDS)
        val randomCount = arguments?.getInt(PracticeActivity.ARG_RANDOM_COUNT, 20) ?: 20
        viewModel.loadWithSelection(selectedIds, randomCount)
    }

    override fun createObserver() {
        observeUi()
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
        databind.tvGuideChip.setOnClickListener { viewModel.onGuideClick() }
        databind.cardPlayReference.setOnClickListener { toggleReferencePlayback() }
        databind.cardPlayMine.setOnClickListener { toggleMinePlayback() }
        databind.cardRecord.setOnClickListener { handleRecordAction() }
        databind.btnNextWord.setOnClickListener { handleNextAction() }
        databind.cardWavePlayOverlay.setOnClickListener { handleWaveOverlayPlay() }
        databind.tvQuotaAction.setOnClickListener {
            showToast(getString(R.string.practice_shadowing_unlock_soon))
        }
        databind.waveformView.onSeekRequested = { fraction ->
            seekPlayback(fraction)
        }
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
        databind.tvSummary.text = state.summaryText
        databind.tvPendingReview.text = state.pendingReviewText
        databind.tvPendingReview.isVisible = state.pendingReviewText.isNotBlank()
        databind.tvReviewTag.text = state.reviewTagText
        databind.tvReviewTag.isVisible = state.reviewTagText.isNotBlank()
        databind.layoutQuotaBanner.isVisible = false

        val latestAttempt = state.latestAttempt
        databind.tvRecognized.isVisible = latestAttempt != null
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
        databind.tvScoreBreakdown.text = state.scoreBreakdownText
        databind.tvScoreBreakdown.isVisible = state.scoreBreakdownText.isNotBlank()
        databind.tvAudioIssue.text = state.audioIssueText
        databind.tvAudioIssue.isVisible = state.audioIssueText.isNotBlank()
        databind.tvDetailNote.text = state.detailSourceNote
        databind.tvDetailNote.isVisible = state.detailSourceNote.isNotBlank()

        val waveformSamples = when {
            state.stage == ShadowingStage.RECORDING -> currentWaveSamples.ifEmpty { defaultWaveformSamples() }
            latestAttempt != null -> latestAttempt.waveformSamples
            else -> defaultWaveformSamples()
        }
        if (state.stage == ShadowingStage.RECORDING) {
            databind.waveformView.startLiveWave()
        } else {
            databind.waveformView.stopLiveWave()
            databind.waveformView.setWaveformSamples(waveformSamples)
        }
        databind.cardWavePlayOverlay.isVisible =
            state.stage != ShadowingStage.RECORDING &&
                state.stage != ShadowingStage.EVALUATING &&
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
                        R.color.feature_learning_shadowing_text_primary
                    )
                )
                textSize = 13f
                background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.feature_learning_bg_shadowing_history
                )
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener {
                    databind.waveformView.stopLiveWave()
                    databind.waveformView.setWaveformSamples(item.waveformSamples)
                    togglePlayback(PlaybackSource.Mine(item.audioFilePath))
                }
            }
            val params = LinearLayoutLayoutParamsFactory.wrapContent()
            params.marginEnd = if (index == history.lastIndex) 0 else dp(10)
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

            ShadowingStage.EVALUATING -> {
                showToast(getString(R.string.practice_shadowing_wait_for_feedback))
                return
            }

            else -> Unit
        }
        cancelAutoPlayback()
        releasePlayer(resetProgress = true)
        viewModel.nextWord()
    }

    private fun handleWaveOverlayPlay() {
        if (viewModel.uiState.value.stage == ShadowingStage.EVALUATING) return
        val current = currentPlaybackSource
        if (current != null) {
            togglePlayback(current)
            return
        }
        val state = viewModel.uiState.value
        val source = state.latestAttempt?.audioFilePath?.let { PlaybackSource.Mine(it) }
            ?: state.speech?.audioOutput?.let { PlaybackSource.Reference(it) }
        if (source != null) {
            togglePlayback(source)
        }
    }

    private fun toggleReferencePlayback() {
        if (viewModel.uiState.value.stage == ShadowingStage.RECORDING ||
            viewModel.uiState.value.stage == ShadowingStage.EVALUATING
        ) {
            return
        }
        val output = viewModel.uiState.value.speech?.audioOutput
        if (output == null) {
            showToast(getString(R.string.practice_tts_not_ready))
            return
        }
        togglePlayback(PlaybackSource.Reference(output))
    }

    private fun toggleMinePlayback() {
        if (viewModel.uiState.value.stage == ShadowingStage.RECORDING ||
            viewModel.uiState.value.stage == ShadowingStage.EVALUATING
        ) {
            return
        }
        val filePath = viewModel.uiState.value.latestAttempt?.audioFilePath
        if (filePath.isNullOrBlank()) {
            showToast(getString(R.string.practice_please_record_first))
            return
        }
        togglePlayback(PlaybackSource.Mine(filePath))
    }

    private fun togglePlayback(source: PlaybackSource) {
        val current = currentPlaybackSource
        val sameSource = current?.key == source.key && mediaPlayer != null && isPlaybackPrepared
        cancelAutoPlayback(preservePlayback = sameSource)
        if (sameSource) {
            val player = mediaPlayer ?: return
            if (player.isPlaying) {
                player.pause()
                stopPlaybackProgressUpdates()
            } else {
                player.start()
                startPlaybackProgressUpdates()
            }
            updatePlaybackUi()
            return
        }
        startPlayback(source = source, startFraction = null)
    }

    private fun seekPlayback(fraction: Float) {
        if (viewModel.uiState.value.stage == ShadowingStage.RECORDING ||
            viewModel.uiState.value.stage == ShadowingStage.EVALUATING
        ) {
            return
        }
        val preferredSource = when (currentPlaybackSource?.target) {
            PlaybackTarget.REFERENCE -> currentPlaybackSource
            PlaybackTarget.MINE -> currentPlaybackSource
            null -> {
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
            if (player.duration > 0) {
                player.seekTo((player.duration * fraction).toInt())
                if (!player.isPlaying) {
                    player.start()
                    startPlaybackProgressUpdates()
                }
                updatePlaybackUi()
            }
        } else {
            startPlayback(source = preferredSource, startFraction = fraction)
        }
    }

    private fun startRecording() {
        cancelAutoPlayback()
        releasePlayer(resetProgress = true)
        val outputFile = File(
            requireContext().cacheDir,
            "shadowing_${System.currentTimeMillis()}.m4a"
        )
        currentRecordingFile = outputFile
        managedRecordingFiles += outputFile
        currentWaveSamples.clear()
        val recorder = runCatching {
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(96000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
        }.getOrElse {
            currentRecordingFile = null
            showToast(getString(R.string.practice_shadowing_recorder_failed))
            viewModel.onRecordingFailed(getString(R.string.practice_shadowing_recorder_failed))
            return
        }
        mediaRecorder = recorder
        recordingStartedAtMs = System.currentTimeMillis()
        databind.waveformView.startLiveWave()
        startAmplitudePolling()
        viewModel.onRecordingStarted()
    }

    private fun stopRecordingAndEvaluate() {
        stopAmplitudePolling()
        databind.waveformView.stopLiveWave()
        val recorder = mediaRecorder ?: return
        mediaRecorder = null
        val outputFile = currentRecordingFile
        currentRecordingFile = null
        val durationMs = (System.currentTimeMillis() - recordingStartedAtMs).coerceAtLeast(0L)
        recordingStartedAtMs = 0L
        val stopped = runCatching {
            recorder.stop()
            recorder.release()
        }.isSuccess
        if (!stopped || outputFile == null || !outputFile.exists()) {
            runCatching { recorder.release() }
            showToast(getString(R.string.practice_shadowing_recording_missing))
            viewModel.onRecordingFailed(getString(R.string.practice_shadowing_recording_missing))
            return
        }
        val waveform = currentWaveSamples.ifEmpty { defaultWaveformSamples() }
        databind.waveformView.setWaveformSamples(waveform)
        viewModel.onRecordingCompleted(outputFile.absolutePath, waveform, durationMs)
    }

    private fun abortRecording() {
        stopAmplitudePolling()
        val recorder = mediaRecorder ?: return
        mediaRecorder = null
        currentRecordingFile = null
        recordingStartedAtMs = 0L
        runCatching {
            recorder.stop()
            recorder.release()
        }
    }

    private fun startAmplitudePolling() {
        stopAmplitudePolling()
        amplitudeJob = viewLifecycleOwner.lifecycleScope.launch {
            while (mediaRecorder != null) {
                val amplitude = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                val normalized = normalizeWaveSample(amplitude)
                currentWaveSamples += normalized
                if (currentWaveSamples.size > MAX_WAVE_SAMPLE_COUNT) {
                    currentWaveSamples.removeAt(0)
                }
                databind.waveformView.updateLiveAmplitude(amplitude)
                delay(60L)
            }
        }
    }

    private fun stopAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = null
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
            playSourceSuspending(PlaybackSource.Reference(reference))
            playSourceSuspending(PlaybackSource.Mine(minePath))
            releasePlayer(resetProgress = true)
        }
    }

    private suspend fun playSourceSuspending(source: PlaybackSource) {
        suspendCancellableCoroutine<Unit> { continuation ->
            startPlayback(
                source = source,
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
        startFraction: Float?,
        onCompletion: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ) {
        releasePlayer(resetProgress = true)
        val player = MediaPlayer()
        currentPlaybackSource = source
        isPlaybackPrepared = false
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
                    databind.waveformView.setPlaybackProgress(
                        player.currentPosition.toFloat() / player.duration
                    )
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

    private fun releasePlayer(resetProgress: Boolean) {
        stopPlaybackProgressUpdates()
        val player = mediaPlayer
        mediaPlayer = null
        isPlaybackPrepared = false
        currentPlaybackSource = null
        if (resetProgress) {
            databind.waveformView.setPlaybackProgress(0f)
        }
        runCatching {
            player?.stop()
        }
        runCatching {
            player?.release()
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
        val player = mediaPlayer
        val isPlaying = player?.isPlaying == true

        databind.ivWavePlay.setImageResource(
            if (isPlaying) {
                R.drawable.feature_learning_ic_pause
            } else {
                R.drawable.feature_learning_ic_play
            }
        )

        databind.cardPlayReference.alpha = when {
            !databind.cardPlayReference.isEnabled -> 0.42f
            current == PlaybackTarget.REFERENCE -> 1f
            else -> 0.96f
        }
        databind.cardPlayMine.alpha = when {
            !databind.cardPlayMine.isEnabled -> 0.42f
            current == PlaybackTarget.MINE -> 1f
            else -> 0.96f
        }
    }

    private fun applyActionAvailability(state: ShadowingPracticeViewModel.ShadowingUiState) {
        val recording = state.stage == ShadowingStage.RECORDING
        val evaluating = state.stage == ShadowingStage.EVALUATING
        val canPlayReference = !recording && !evaluating && state.speech != null
        val canPlayMine = !recording && !evaluating && state.latestAttempt != null
        val recordLabel = when {
            !hasRecordPermission() && permissionBlocked -> {
                getString(R.string.practice_shadowing_enable_permission)
            }

            recording -> getString(R.string.practice_shadowing_stop_record)
            state.latestAttempt != null -> getString(R.string.practice_shadowing_rerecord)
            else -> getString(R.string.practice_shadowing_record)
        }

        databind.cardPlayReference.isEnabled = canPlayReference
        databind.cardPlayMine.isEnabled = canPlayMine
        databind.cardRecord.isEnabled = !state.loading && !state.isCompleted && !evaluating
        databind.btnNextWord.isEnabled = !recording && !state.loading && !evaluating
        databind.btnNextWord.text = state.nextActionText
        databind.tvRecordLabel.text = recordLabel

        databind.cardPlayReference.alpha = if (canPlayReference) 1f else 0.42f
        databind.cardPlayMine.alpha = if (canPlayMine) 1f else 0.42f

        databind.cardRecord.setCardBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                if (!hasRecordPermission() && permissionBlocked) {
                    R.color.feature_learning_shadowing_disabled
                } else {
                    R.color.feature_learning_shadowing_record_bg
                }
            )
        )
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
    }

    private fun normalizeWaveSample(amplitude: Int): Int {
        val normalized = (amplitude / 32767f * 100f).toInt()
        return normalized.coerceIn(10, 100)
    }

    private fun defaultWaveformSamples(): List<Int> {
        return listOf(
            12, 18, 16, 24, 14, 20, 15, 22,
            18, 26, 16, 20, 14, 24, 16, 18,
            22, 14, 18, 26, 15, 20, 14, 18
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
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

    companion object {
        private const val MAX_WAVE_SAMPLE_COUNT = 96
    }
}
