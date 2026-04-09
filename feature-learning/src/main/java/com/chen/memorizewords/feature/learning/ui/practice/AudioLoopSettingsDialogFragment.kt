package com.chen.memorizewords.feature.learning.ui.practice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.chen.memorizewords.domain.model.practice.PracticeSettings
import com.chen.memorizewords.feature.learning.databinding.DialogPracticeAudioLoopSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AudioLoopSettingsDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "audio_loop_settings_dialog"
    }

    private var _binding: DialogPracticeAudioLoopSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AudioLoopPracticeViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(requireActivity())[AudioLoopPracticeViewModel::class.java]
    }

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
        val state = viewModel.uiState.value
        val settings = state.settings
        val bookItems = mutableListOf(0L to "当前词书")
        bookItems.addAll(state.books.map { it.bookId to it.title })

        binding.switchWordSpelling.isChecked = settings.playWordSpelling
        binding.switchChineseMeaning.isChecked = settings.playChineseMeaning
        binding.switchLoop.isChecked = settings.loopEnabled
        binding.seekInterval.progress = (settings.intervalSeconds - 1).coerceIn(0, 9)
        binding.tvInterval.text = "播放间隔：${settings.intervalSeconds}秒"

        binding.seekInterval.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvInterval.text = "播放间隔：${progress + 1}秒"
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            bookItems.map { it.second }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerBook.adapter = adapter
        val selectedIndex = bookItems.indexOfFirst { it.first == settings.selectedBookId }.takeIf { it >= 0 } ?: 0
        binding.spinnerBook.setSelection(selectedIndex, false)

        binding.btnSave.setOnClickListener {
            val selectedBookId = bookItems.getOrNull(binding.spinnerBook.selectedItemPosition)?.first ?: 0L
            val updated = PracticeSettings(
                selectedBookId = selectedBookId,
                intervalSeconds = binding.seekInterval.progress + 1,
                loopEnabled = binding.switchLoop.isChecked,
                playWordSpelling = binding.switchWordSpelling.isChecked,
                playChineseMeaning = binding.switchChineseMeaning.isChecked
            )
            viewModel.saveSettings(updated)
            dismiss()
        }
    }
}
