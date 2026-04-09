package com.chen.memorizewords.feature.learning.ui.practice

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeListeningBinding
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ListeningPracticeFragment : Fragment() {

    private var _binding: FragmentPracticeListeningBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ListeningPracticeViewModel by viewModels()
    private val sessionViewModel: PracticeSessionViewModel by activityViewModels()

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPracticeListeningBinding.inflate(inflater, container, false)
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
        releaseMediaPlayer()
        _binding = null
        super.onDestroyView()
    }

    private fun bindActions() {
        binding.btnOption1.setOnClickListener { viewModel.onSelect(0) }
        binding.btnOption2.setOnClickListener { viewModel.onSelect(1) }
        binding.btnOption3.setOnClickListener { viewModel.onSelect(2) }
        binding.btnOption4.setOnClickListener { viewModel.onSelect(3) }
        binding.btnNext.setOnClickListener {
            releaseMediaPlayer()
            viewModel.onNext()
        }
        binding.btnPlayAudio.setOnClickListener { playCurrentAudio() }
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.tvProgress.text = state.progress
                        binding.tvResult.text = state.feedback
                        binding.btnNext.isEnabled = state.canNext && !state.questionLoading
                        binding.btnPlayAudio.isEnabled = !state.questionLoading && state.speech != null
                        sessionViewModel.updateSessionSummary(state.summary)
                        binding.btnNext.text = getString(
                            if (state.isCompleted) {
                                R.string.practice_completed
                            } else {
                                R.string.practice_next_question
                            }
                        )

                        val options = state.options
                        bindOption(
                            binding.btnOption1,
                            options.getOrNull(0)?.text.orEmpty(),
                            state.selectedIndex == 0,
                            !state.questionLoading
                        )
                        bindOption(
                            binding.btnOption2,
                            options.getOrNull(1)?.text.orEmpty(),
                            state.selectedIndex == 1,
                            !state.questionLoading
                        )
                        bindOption(
                            binding.btnOption3,
                            options.getOrNull(2)?.text.orEmpty(),
                            state.selectedIndex == 2,
                            !state.questionLoading
                        )
                        bindOption(
                            binding.btnOption4,
                            options.getOrNull(3)?.text.orEmpty(),
                            state.selectedIndex == 3,
                            !state.questionLoading
                        )
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

    private fun bindOption(view: MaterialButton, text: String, selected: Boolean, interactive: Boolean) {
        view.text = text.ifBlank { "-" }
        view.isEnabled = interactive && text.isNotBlank()
        view.alpha = if (selected) 1f else 0.9f
    }

    private fun playCurrentAudio() {
        val output = viewModel.uiState.value.speech?.audioOutput
        if (output == null) {
            Toast.makeText(
                requireContext(),
                R.string.practice_audio_unavailable,
                Toast.LENGTH_SHORT
            ).show()
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

    private fun releaseMediaPlayer() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            runCatching { player.stop() }
            player.release()
        }
    }
}
