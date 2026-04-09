package com.chen.memorizewords.feature.learning.ui.practice

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeShadowingBinding
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShadowingPracticeFragment : Fragment() {

    private var _binding: FragmentPracticeShadowingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ShadowingPracticeViewModel by viewModels()
    private val sessionViewModel: PracticeSessionViewModel by activityViewModels()

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFile: File? = null
    private var isRecording: Boolean = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            toggleRecording()
        } else {
            Toast.makeText(requireContext(), R.string.practice_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPracticeShadowingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindActions()
        observeUi()
        val selectedIds = arguments?.getLongArray(PracticeActivity.ARG_SELECTED_WORD_IDS)
        val randomCount = arguments?.getInt(PracticeActivity.ARG_RANDOM_COUNT, 20) ?: 20
        viewModel.loadWithSelection(selectedIds, randomCount)
    }

    override fun onDestroyView() {
        stopRecordingIfNeeded()
        releaseMediaPlayer()
        clearRecordingFile()
        _binding = null
        super.onDestroyView()
    }

    private fun bindActions() {
        binding.btnPlayReference.setOnClickListener { playReferenceAudio() }
        binding.btnRecordToggle.setOnClickListener {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        binding.btnPlayMyAudio.setOnClickListener { playMyAudio() }
        binding.btnEvaluate.setOnClickListener {
            if (isRecording) {
                showStopRecordingFirstToast()
                return@setOnClickListener
            }
            val file = recordingFile
            if (file == null || !file.exists()) {
                Toast.makeText(requireContext(), R.string.practice_please_record_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.evaluate(file.absolutePath)
        }
        binding.btnNextWord.setOnClickListener {
            if (isRecording) {
                showStopRecordingFirstToast()
                return@setOnClickListener
            }
            releaseMediaPlayer()
            clearRecordingFile()
            applyActionAvailability()
            viewModel.nextWord()
        }
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.tvWord.text = state.word
                        applyActionAvailability(state)
                        sessionViewModel.updateSessionSummary(state.summary)
                        binding.btnNextWord.text = getString(
                            if (state.isCompleted) {
                                R.string.practice_completed
                            } else {
                                R.string.practice_next_word
                            }
                        )
                        val result = state.lastResult
                        binding.tvScores.text = if (state.evaluating) {
                            ""
                        } else if (result == null) {
                            state.errorMessage
                        } else {
                            getString(
                                R.string.practice_score_format,
                                result.totalScore,
                                result.pronunciationScore,
                                result.fluencyScore
                            )
                        }
                        binding.tvRecognized.text = when {
                            state.evaluating -> ""
                            result != null -> getString(
                                R.string.practice_my_pronunciation,
                                result.recognizedText
                            )
                            state.isCompleted -> getString(R.string.practice_session_completed)
                            else -> ""
                        }
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

    private fun playReferenceAudio() {
        val output = viewModel.uiState.value.speech?.audioOutput
        if (output == null) {
            Toast.makeText(requireContext(), R.string.practice_tts_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        releaseMediaPlayer()
        val player = MediaPlayer()
        val prepared = player.prepareSpeechOutputAsync(
            output = output,
            onPrepared = {
                it.setOnCompletionListener { _ -> releaseMediaPlayer() }
                it.start()
            },
            onError = {
                releaseMediaPlayer()
                context?.let { safeContext ->
                    Toast.makeText(
                        safeContext,
                        R.string.practice_audio_unavailable,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        if (prepared) {
            mediaPlayer = player
        }
    }

    private fun playMyAudio() {
        val file = recordingFile
        if (file == null || !file.exists()) {
            Toast.makeText(requireContext(), R.string.practice_please_record_first, Toast.LENGTH_SHORT).show()
            return
        }
        playFile(file)
    }

    private fun playFile(file: File) {
        releaseMediaPlayer()
        val player = MediaPlayer()
        val prepared = runCatching {
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { _ -> releaseMediaPlayer() }
            player.setOnErrorListener { _, _, _ ->
                releaseMediaPlayer()
                true
            }
            player.prepareAsync()
            true
        }.getOrElse {
            runCatching { player.release() }
            Toast.makeText(
                requireContext(),
                R.string.practice_audio_unavailable,
                Toast.LENGTH_SHORT
            ).show()
            false
        }
        if (prepared) {
            mediaPlayer = player
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            stopRecordingIfNeeded()
            return
        }
        releaseMediaPlayer()
        clearRecordingFile()
        val outFile = File(requireContext().cacheDir, "shadowing_${System.currentTimeMillis()}.m4a")
        recordingFile = outFile
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(96000)
            setOutputFile(outFile.absolutePath)
            prepare()
            start()
        }
        isRecording = true
        binding.btnRecordToggle.text = getString(R.string.practice_stop_recording)
        applyActionAvailability()
    }

    private fun stopRecordingIfNeeded() {
        if (!isRecording) return
        runCatching {
            mediaRecorder?.stop()
        }
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        binding.btnRecordToggle.text = getString(R.string.practice_start_recording)
        applyActionAvailability()
    }

    private fun applyActionAvailability(
        state: ShadowingPracticeViewModel.ShadowingUiState = viewModel.uiState.value
    ) {
        val recordingLocked = isRecording
        val hasRecording = hasUsableRecording()
        binding.btnNextWord.isEnabled = !recordingLocked && !state.isCompleted && !state.loading
        binding.btnEvaluate.isEnabled =
            hasRecording && !recordingLocked && !state.loading && !state.evaluating &&
                !state.evaluationUnsupported && !state.isCompleted
        binding.btnPlayReference.isEnabled =
            !recordingLocked && !state.loading && state.speech != null
        binding.btnRecordToggle.isEnabled = !state.loading && !state.evaluating && !state.isCompleted
        binding.btnPlayMyAudio.isEnabled = hasRecording && !recordingLocked && !state.evaluating
    }

    private fun showStopRecordingFirstToast() {
        Toast.makeText(requireContext(), R.string.practice_stop_recording_first, Toast.LENGTH_SHORT).show()
    }

    private fun hasUsableRecording(): Boolean {
        return recordingFile?.exists() == true
    }

    private fun clearRecordingFile() {
        val file = recordingFile
        recordingFile = null
        if (file != null && file.exists()) {
            runCatching { file.delete() }
        }
    }

    private fun releaseMediaPlayer() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            runCatching { player.stop() }
            player.release()
        }
    }
}
