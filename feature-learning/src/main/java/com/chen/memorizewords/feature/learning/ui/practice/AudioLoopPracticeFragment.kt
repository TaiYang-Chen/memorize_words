package com.chen.memorizewords.feature.learning.ui.practice

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeAudioLoopBinding
import com.chen.memorizewords.feature.learning.ui.practice.service.AudioLoopService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AudioLoopPracticeFragment : Fragment() {

    private var _binding: FragmentPracticeAudioLoopBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AudioLoopPracticeViewModel by viewModels()

    private var selectedIds: LongArray? = null
    private var randomCount: Int = 20

    @Inject
    lateinit var wordProvider: PracticeWordProvider

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPracticeAudioLoopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedIds = arguments?.getLongArray(PracticeActivity.ARG_SELECTED_WORD_IDS)
        randomCount = arguments?.getInt(PracticeActivity.ARG_RANDOM_COUNT, 20) ?: 20
        bindActions()
        observeUi()
    }

    private fun bindActions() {
        binding.btnStart.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val words = wordProvider.loadWords(
                    selectedIds = selectedIds,
                    randomCount = randomCount,
                    defaultLimit = 50
                )
                if (words.isEmpty()) return@launch
                val playbackIds = words.map { it.id }
                dispatchServiceAction(
                    action = AudioLoopService.ACTION_START,
                    playbackIds = playbackIds.toLongArray()
                )
                binding.tvStatus.text = "状态：播放中"
            }
        }
        binding.btnStop.setOnClickListener {
            dispatchServiceAction(AudioLoopService.ACTION_STOP)
            binding.tvStatus.text = "状态：已停止"
        }
        binding.btnNext.setOnClickListener {
            dispatchServiceAction(AudioLoopService.ACTION_NEXT)
        }
        binding.btnSettings.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(AudioLoopSettingsDialogFragment.TAG) == null) {
                AudioLoopSettingsDialogFragment().show(
                    childFragmentManager,
                    AudioLoopSettingsDialogFragment.TAG
                )
            }
        }
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val settings = state.settings
                    binding.tvBook.text = "词书：${state.currentBookName}"
                    binding.tvSettings.text =
                        "间隔${settings.intervalSeconds}秒 | 拼读${onOff(settings.playWordSpelling)} | 释义发音${onOff(settings.playChineseMeaning)} | 循环${onOff(settings.loopEnabled)}"
                }
            }
        }
    }

    private fun onOff(enabled: Boolean): String = if (enabled) "开" else "关"

    private fun dispatchServiceAction(action: String, playbackIds: LongArray? = null) {
        val intent = Intent(requireContext(), AudioLoopService::class.java).apply {
            this.action = action
            putExtra(PracticeActivity.EXTRA_RANDOM_COUNT, randomCount)
            putExtra(
                PracticeActivity.EXTRA_ENTRY_TYPE,
                if (selectedIds == null || selectedIds!!.isEmpty()) {
                    PracticeEntryType.RANDOM.name
                } else {
                    PracticeEntryType.SELF.name
                }
            )
            playbackIds?.let { putExtra(PracticeActivity.EXTRA_SELECTED_WORD_IDS, it) }
        }
        if (action == AudioLoopService.ACTION_START) {
            ContextCompat.startForegroundService(requireContext(), intent)
        } else {
            requireContext().startService(intent)
        }
    }
}
