package com.chen.memorizewords.feature.learning.ui.practice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.domain.model.practice.AudioLoopPlaybackMode
import com.chen.memorizewords.domain.model.practice.PracticeSettings
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.DialogPracticeAudioLoopSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AudioLoopSettingsDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "audio_loop_settings_dialog"
        private const val MIN_PLAY_TIMES = 1
        private const val MAX_PLAY_TIMES = 10
        private const val MIN_INTERVAL_SECONDS = 0
        private const val MAX_INTERVAL_SECONDS = 10
    }

    private var _binding: DialogPracticeAudioLoopSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AudioLoopPracticeViewModel by lazy {
        ViewModelProvider(requireParentFragment())[AudioLoopPracticeViewModel::class.java]
    }

    private var draftSettings: PracticeSettings = PracticeSettings()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPracticeAudioLoopSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        draftSettings = viewModel.uiState.value.persistedSettings
        bindActions()
        renderDraft()
    }

    private fun bindActions() {
        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            draftSettings = draftSettings.copy(
                playbackMode = if (checkedId == R.id.radio_word_with_example) {
                    AudioLoopPlaybackMode.WORD_WITH_EXAMPLE
                } else {
                    AudioLoopPlaybackMode.WORD_ONLY
                }
            )
        }
        binding.btnPlayTimesMinus.setOnClickListener {
            draftSettings = draftSettings.copy(
                playTimes = (draftSettings.playTimes - 1).coerceAtLeast(MIN_PLAY_TIMES)
            )
            renderDraft()
        }
        binding.btnPlayTimesPlus.setOnClickListener {
            draftSettings = draftSettings.copy(
                playTimes = (draftSettings.playTimes + 1).coerceAtMost(MAX_PLAY_TIMES)
            )
            renderDraft()
        }
        binding.btnIntervalMinus.setOnClickListener {
            draftSettings = draftSettings.copy(
                intervalSeconds = (draftSettings.intervalSeconds - 1)
                    .coerceAtLeast(MIN_INTERVAL_SECONDS)
            )
            renderDraft()
        }
        binding.btnIntervalPlus.setOnClickListener {
            draftSettings = draftSettings.copy(
                intervalSeconds = (draftSettings.intervalSeconds + 1)
                    .coerceAtMost(MAX_INTERVAL_SECONDS)
            )
            renderDraft()
        }
        binding.switchLoop.setOnCheckedChangeListener { _, isChecked ->
            draftSettings = draftSettings.copy(loopEnabled = isChecked)
        }
        binding.switchWordSpelling.setOnCheckedChangeListener { _, isChecked ->
            draftSettings = draftSettings.copy(showPhonetic = isChecked)
        }
        binding.switchChineseMeaning.setOnCheckedChangeListener { _, isChecked ->
            draftSettings = draftSettings.copy(showMeaning = isChecked)
        }
        binding.btnSave.setOnClickListener {
            viewModel.saveSettings(draftSettings)
            dismiss()
        }
    }

    private fun renderDraft() {
        binding.radioWordOnly.isChecked =
            draftSettings.playbackMode == AudioLoopPlaybackMode.WORD_ONLY
        binding.radioWordWithExample.isChecked =
            draftSettings.playbackMode == AudioLoopPlaybackMode.WORD_WITH_EXAMPLE

        binding.tvPlayTimesValue.text = getString(
            R.string.feature_learning_audio_loop_settings_play_times_value,
            draftSettings.playTimes
        )
        binding.tvIntervalValue.text = getString(
            R.string.feature_learning_audio_loop_settings_interval_value,
            draftSettings.intervalSeconds
        )

        binding.switchLoop.isChecked = draftSettings.loopEnabled
        binding.switchWordSpelling.isChecked = draftSettings.showPhonetic
        binding.switchChineseMeaning.isChecked = draftSettings.showMeaning

        binding.btnPlayTimesMinus.isEnabled = draftSettings.playTimes > MIN_PLAY_TIMES
        binding.btnPlayTimesPlus.isEnabled = draftSettings.playTimes < MAX_PLAY_TIMES
        binding.btnIntervalMinus.isEnabled = draftSettings.intervalSeconds > MIN_INTERVAL_SECONDS
        binding.btnIntervalPlus.isEnabled = draftSettings.intervalSeconds < MAX_INTERVAL_SECONDS
    }
}
