package com.chen.memorizewords.feature.learning.ui.practice

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.domain.practice.AudioLoopPlaybackMode
import com.chen.memorizewords.domain.practice.AudioLoopPlayOrder
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.DialogPracticeAudioLoopSettingsBinding
import com.chen.memorizewords.feature.learning.ui.practice.audioLoop.AudioLoopPlaybackStore
import com.chen.memorizewords.feature.learning.ui.practice.audioLoop.AudioLoopServicePlayerState
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AudioLoopSettingsDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "audio_loop_settings_dialog"
        private const val MIN_REPEAT = 1
        private const val MAX_REPEAT = 10
        private const val MIN_SECONDS = 0
        private const val MAX_SECONDS = 30
        private const val TIMED_STOP_STEP_MINUTES = 5
        private const val MAX_TIMED_STOP_MINUTES = 120
        private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
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

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        bottomSheet.setBackgroundResource(android.R.color.transparent)
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * 0.92f).toInt()
        }
        BottomSheetBehavior.from(bottomSheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
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
        bindModeRows()
        bindParameterRows()
        bindSwitchRows()
        binding.btnSave.setOnClickListener {
            val playingState = AudioLoopPlaybackStore.state.value.playerState
            val isActivelyPlaying = playingState == AudioLoopServicePlayerState.PLAYING ||
                playingState == AudioLoopServicePlayerState.PREPARING
            viewModel.saveSettings(draftSettings)
            if (isActivelyPlaying) {
                Toast.makeText(
                    requireContext(),
                    R.string.feature_learning_audio_loop_settings_pending_toast,
                    Toast.LENGTH_SHORT
                ).show()
            }
            dismiss()
        }
    }

    private fun bindModeRows() {
        binding.layoutModeWordOnly.setOnClickListener { updateMode(AudioLoopPlaybackMode.WORD_ONLY) }
        binding.layoutModeWordWithMeaning.setOnClickListener { updateMode(AudioLoopPlaybackMode.WORD_WITH_MEANING) }
        binding.layoutModeWordWithExample.setOnClickListener { updateMode(AudioLoopPlaybackMode.WORD_WITH_EXAMPLE) }
        binding.layoutModeDictation.setOnClickListener { updateMode(AudioLoopPlaybackMode.DICTATION) }
        binding.layoutModeFullDetail.setOnClickListener { updateMode(AudioLoopPlaybackMode.FULL_DETAIL) }
    }

    private fun bindParameterRows() {
        binding.layoutParamPlayTimes.setOnClickListener {
            showIntChoice(
                titleRes = R.string.feature_learning_audio_loop_settings_play_times,
                values = (MIN_REPEAT..MAX_REPEAT).toList(),
                currentValue = draftSettings.playTimes,
                valueText = { getString(R.string.feature_learning_audio_loop_settings_play_times_value, it) },
                onSelected = { draftSettings = draftSettings.copy(playTimes = it) }
            )
        }
        binding.layoutParamWordRepeat.setOnClickListener {
            showIntChoice(
                titleRes = R.string.feature_learning_audio_loop_settings_word_repeat,
                values = (MIN_REPEAT..MAX_REPEAT).toList(),
                currentValue = draftSettings.wordRepeatTimes,
                valueText = { getString(R.string.feature_learning_audio_loop_settings_play_times_value, it) },
                onSelected = { draftSettings = draftSettings.copy(wordRepeatTimes = it) }
            )
        }
        binding.layoutParamExampleRepeat.setOnClickListener {
            showIntChoice(
                titleRes = R.string.feature_learning_audio_loop_settings_example_repeat,
                values = (MIN_REPEAT..MAX_REPEAT).toList(),
                currentValue = draftSettings.exampleRepeatTimes,
                valueText = { getString(R.string.feature_learning_audio_loop_settings_play_times_value, it) },
                onSelected = { draftSettings = draftSettings.copy(exampleRepeatTimes = it) }
            )
        }
        binding.layoutParamInterval.setOnClickListener {
            showIntChoice(
                titleRes = R.string.feature_learning_audio_loop_settings_interval,
                values = (MIN_SECONDS..MAX_SECONDS).toList(),
                currentValue = draftSettings.intervalSeconds,
                valueText = { getString(R.string.feature_learning_audio_loop_settings_interval_value, it) },
                onSelected = { draftSettings = draftSettings.copy(intervalSeconds = it) }
            )
        }
        binding.layoutParamDictationPause.setOnClickListener {
            showIntChoice(
                titleRes = R.string.feature_learning_audio_loop_settings_dictation_pause,
                values = (MIN_SECONDS..MAX_SECONDS).toList(),
                currentValue = draftSettings.dictationPauseSeconds,
                valueText = { getString(R.string.feature_learning_audio_loop_settings_interval_value, it) },
                onSelected = { draftSettings = draftSettings.copy(dictationPauseSeconds = it) }
            )
        }
        binding.layoutParamRevealDelay.setOnClickListener {
            showIntChoice(
                titleRes = R.string.feature_learning_audio_loop_settings_reveal_delay,
                values = (MIN_SECONDS..MAX_SECONDS).toList(),
                currentValue = draftSettings.revealDelaySeconds,
                valueText = { getString(R.string.feature_learning_audio_loop_settings_interval_value, it) },
                onSelected = { draftSettings = draftSettings.copy(revealDelaySeconds = it) }
            )
        }
        binding.layoutParamTimedStop.setOnClickListener {
            showIntChoice(
                titleRes = R.string.feature_learning_audio_loop_settings_timed_stop,
                values = (0..MAX_TIMED_STOP_MINUTES step TIMED_STOP_STEP_MINUTES).toList(),
                currentValue = draftSettings.timedStopMinutes,
                valueText = { getString(R.string.feature_learning_audio_loop_settings_timed_stop_value, it) },
                onSelected = { draftSettings = draftSettings.copy(timedStopMinutes = it) }
            )
        }
        binding.layoutParamSpeed.setOnClickListener {
            showSpeedChoice()
        }
    }

    private fun bindSwitchRows() {
        binding.switchLoop.setOnCheckedChangeListener { _, isChecked ->
            draftSettings = draftSettings.copy(loopEnabled = isChecked)
        }
        binding.switchRandomOrder.setOnCheckedChangeListener { _, isChecked ->
            draftSettings = draftSettings.copy(
                playOrder = if (isChecked) AudioLoopPlayOrder.RANDOM else AudioLoopPlayOrder.SEQUENTIAL
            )
        }
        binding.switchKeepScreen.setOnCheckedChangeListener { _, isChecked ->
            draftSettings = draftSettings.copy(keepScreenOn = isChecked)
        }
        binding.switchWordSpelling.setOnCheckedChangeListener { _, isChecked ->
            draftSettings = draftSettings.copy(showPhonetic = isChecked)
        }
        binding.switchChineseMeaning.setOnCheckedChangeListener { _, isChecked ->
            draftSettings = draftSettings.copy(showMeaning = isChecked)
        }

        binding.layoutSwitchLoop.setOnClickListener { binding.switchLoop.performClick() }
        binding.layoutSwitchRandomOrder.setOnClickListener { binding.switchRandomOrder.performClick() }
        binding.layoutSwitchKeepScreen.setOnClickListener { binding.switchKeepScreen.performClick() }
        binding.layoutSwitchWordSpelling.setOnClickListener { binding.switchWordSpelling.performClick() }
        binding.layoutSwitchChineseMeaning.setOnClickListener { binding.switchChineseMeaning.performClick() }
    }

    private fun updateMode(mode: AudioLoopPlaybackMode) {
        draftSettings = draftSettings.copy(playbackMode = mode)
        renderDraft()
    }

    private fun renderDraft() {
        binding.radioWordOnly.isChecked = draftSettings.playbackMode == AudioLoopPlaybackMode.WORD_ONLY
        binding.radioWordWithMeaning.isChecked = draftSettings.playbackMode == AudioLoopPlaybackMode.WORD_WITH_MEANING
        binding.radioWordWithExample.isChecked = draftSettings.playbackMode == AudioLoopPlaybackMode.WORD_WITH_EXAMPLE
        binding.radioDictation.isChecked = draftSettings.playbackMode == AudioLoopPlaybackMode.DICTATION
        binding.radioFullDetail.isChecked = draftSettings.playbackMode == AudioLoopPlaybackMode.FULL_DETAIL

        binding.tvPlayTimesValue.text =
            getString(R.string.feature_learning_audio_loop_settings_play_times_value, draftSettings.playTimes)
        binding.tvWordRepeatValue.text =
            getString(R.string.feature_learning_audio_loop_settings_play_times_value, draftSettings.wordRepeatTimes)
        binding.tvExampleRepeatValue.text =
            getString(R.string.feature_learning_audio_loop_settings_play_times_value, draftSettings.exampleRepeatTimes)
        binding.tvIntervalValue.text =
            getString(R.string.feature_learning_audio_loop_settings_interval_value, draftSettings.intervalSeconds)
        binding.tvDictationPauseValue.text =
            getString(R.string.feature_learning_audio_loop_settings_interval_value, draftSettings.dictationPauseSeconds)
        binding.tvRevealDelayValue.text =
            getString(R.string.feature_learning_audio_loop_settings_interval_value, draftSettings.revealDelaySeconds)
        binding.tvTimedStopValue.text =
            getString(R.string.feature_learning_audio_loop_settings_timed_stop_value, draftSettings.timedStopMinutes)
        binding.tvSpeedValue.text =
            getString(R.string.feature_learning_audio_loop_settings_speed_value, draftSettings.playbackSpeed)

        binding.switchLoop.isChecked = draftSettings.loopEnabled
        binding.switchRandomOrder.isChecked = draftSettings.playOrder == AudioLoopPlayOrder.RANDOM
        binding.switchKeepScreen.isChecked = draftSettings.keepScreenOn
        binding.switchWordSpelling.isChecked = draftSettings.showPhonetic
        binding.switchChineseMeaning.isChecked = draftSettings.showMeaning
    }

    private fun showIntChoice(
        titleRes: Int,
        values: List<Int>,
        currentValue: Int,
        valueText: (Int) -> String,
        onSelected: (Int) -> Unit
    ) {
        val checkedIndex = values.indexOf(currentValue).coerceAtLeast(0)
        val items = values.map(valueText).toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setSingleChoiceItems(items, checkedIndex) { dialog, which ->
                onSelected(values[which])
                renderDraft()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSpeedChoice() {
        val checkedIndex = SPEEDS.indexOfFirst { it == draftSettings.playbackSpeed }.takeIf { it >= 0 }
            ?: SPEEDS.indexOf(1.0f)
        val items = SPEEDS.map {
            getString(R.string.feature_learning_audio_loop_settings_speed_value, it)
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.feature_learning_audio_loop_settings_speed)
            .setSingleChoiceItems(items, checkedIndex) { dialog, which ->
                draftSettings = draftSettings.copy(playbackSpeed = SPEEDS[which])
                renderDraft()
                dialog.dismiss()
            }
            .show()
    }
}
