package com.chen.memorizewords.feature.learning.ui.fragment

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.feature.learning.LinearSpacingItemDecoration
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.adapter.DefinitionsAdapter
import com.chen.memorizewords.feature.learning.adapter.ExamplesAdapter
import com.chen.memorizewords.feature.learning.adapter.FormAdapter
import com.chen.memorizewords.feature.learning.adapter.RootsAdapter
import com.chen.memorizewords.feature.learning.adapter.SynonymsAdapter
import com.chen.memorizewords.feature.learning.databinding.FragmentWordEntryDetailBinding
import com.chen.memorizewords.feature.learning.ui.speech.audioOutputOrNull
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import com.chen.memorizewords.speech.api.SpeechAudioOutput
import com.chen.memorizewords.speech.api.SpeechTask
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WordEntryDetailFragment :
    BaseFragment<WordEntryDetailViewModel, FragmentWordEntryDetailBinding>() {

    override val viewModel: WordEntryDetailViewModel by lazy {
        ViewModelProvider(this)[WordEntryDetailViewModel::class.java]
    }

    @Inject
    lateinit var synthesizeSpeech: SynthesizeSpeechUseCase

    private val args: WordEntryDetailFragmentArgs by navArgs()
    private val definitionsAdapter = DefinitionsAdapter()
    private val examplesAdapter = ExamplesAdapter(
        onclickWord = { _, _ -> },
        onSpeakSentence = { sentence ->
            speakSentence(sentence)
        }
    )
    private val rootsAdapter = RootsAdapter()
    private val synonymsAdapter = SynonymsAdapter()
    private val formAdapter = FormAdapter()
    private var mediaPlayer: MediaPlayer? = null

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.ivBack.setOnClickListener { handleBack() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBack()
                }
            }
        )

        databind.rvDefinitions.adapter = definitionsAdapter
        databind.rvDefinitions.layoutManager = LinearLayoutManager(requireContext())
        databind.rvDefinitions.isNestedScrollingEnabled = false
        databind.rvDefinitions.addItemDecoration(LinearSpacingItemDecoration(8.dpToPx(requireContext())))

        databind.rvExamples.adapter = examplesAdapter
        databind.rvExamples.layoutManager = LinearLayoutManager(requireContext())
        databind.rvExamples.isNestedScrollingEnabled = false
        databind.rvExamples.addItemDecoration(LinearSpacingItemDecoration(16.dpToPx(requireContext())))

        databind.rvRoot.adapter = rootsAdapter
        databind.rvRoot.layoutManager = LinearLayoutManager(requireContext())
        databind.rvRoot.isNestedScrollingEnabled = false
        databind.rvRoot.addItemDecoration(LinearSpacingItemDecoration(16.dpToPx(requireContext())))

        databind.rvSynonyms.adapter = synonymsAdapter
        databind.rvSynonyms.layoutManager = WaterfallFlowLayoutManager()
        databind.rvSynonyms.isNestedScrollingEnabled = false

        databind.rvInflection.adapter = formAdapter
        databind.rvInflection.layoutManager = LinearLayoutManager(requireContext())
        databind.rvInflection.isNestedScrollingEnabled = false

        viewModel.loadWord(args.wordId, args.wordText)
    }

    override fun createObserver() {
        lifecycleScope.launch {
            launch {
                viewModel.currentWord.collect { word ->
                    word?.let {
                        synonymsAdapter.submitList(it.synonyms, it.antonyms)
                    }
                }
            }
            launch {
                viewModel.definitions.collect { definitions ->
                    definitionsAdapter.submitList(definitions)
                }
            }
            launch {
                viewModel.wordExamples.collect { examples ->
                    examplesAdapter.submitList(examples)
                }
            }
            launch {
                viewModel.wordRoots.collect { roots ->
                    rootsAdapter.submitList(roots)
                }
            }
            launch {
                viewModel.wordForm.collect { forms ->
                    formAdapter.submitList(forms)
                }
            }
        }
    }

    override fun onDestroyView() {
        releaseMediaPlayer()
        super.onDestroyView()
    }

    private fun handleBack() {
        if (args.fromFloating) {
            requireActivity().finish()
        } else {
            viewModel.back()
        }
    }

    private fun speakSentence(sentence: String) {
        if (sentence.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val audioOutput = synthesizeSpeech(
                SpeechTask.SynthesizeSentence(
                    text = sentence,
                    locale = "en-US"
                )
            ).audioOutputOrNull()
            if (audioOutput == null) {
                viewModel.showToast(getString(R.string.practice_audio_unavailable))
                return@launch
            }
            playSpeechOutput(audioOutput)
        }
    }

    private fun playSpeechOutput(output: SpeechAudioOutput) {
        releaseMediaPlayer()
        val player = MediaPlayer()
        val prepared = player.prepareSpeechOutputAsync(
            output = output,
            onPrepared = { preparedPlayer ->
                preparedPlayer.setOnCompletionListener {
                    if (mediaPlayer === preparedPlayer) {
                        releaseMediaPlayer()
                    } else {
                        runCatching { preparedPlayer.release() }
                    }
                }
                preparedPlayer.start()
            },
            onError = { failedPlayer ->
                if (mediaPlayer === failedPlayer) {
                    releaseMediaPlayer()
                } else {
                    runCatching { failedPlayer.release() }
                }
                viewModel.showToast(getString(R.string.practice_audio_unavailable))
            }
        )
        if (prepared) {
            mediaPlayer = player
        }
    }

    private fun releaseMediaPlayer() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching { player.stop() }
        runCatching { player.release() }
    }
}
