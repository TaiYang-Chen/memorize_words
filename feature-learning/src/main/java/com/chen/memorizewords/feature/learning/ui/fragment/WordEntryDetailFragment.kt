package com.chen.memorizewords.feature.learning.ui.fragment

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import com.chen.memorizewords.domain.practice.speech.SpeechTask
import com.chen.memorizewords.domain.practice.usecase.SynthesizeSpeechUseCase
import com.chen.memorizewords.domain.word.model.word.PronunciationType
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.feature.learning.LinearSpacingItemDecoration
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.adapter.DefinitionsAdapter
import com.chen.memorizewords.feature.learning.adapter.ExamplesAdapter
import com.chen.memorizewords.feature.learning.adapter.FormAdapter
import com.chen.memorizewords.feature.learning.adapter.RootsAdapter
import com.chen.memorizewords.feature.learning.adapter.SynonymsAdapter
import com.chen.memorizewords.feature.learning.databinding.FragmentWordEntryDetailBinding
import com.chen.memorizewords.feature.learning.ui.visibleWordExamples
import com.chen.memorizewords.feature.learning.ui.visibleWordForms
import com.chen.memorizewords.feature.learning.ui.visibleWordRoots
import com.chen.memorizewords.feature.learning.ui.speech.audioOutputOrNull
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
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
    private var pronunciationType: PronunciationType = PronunciationType.US

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.ivBack.setOnClickListener { handleBack() }
        databind.ivSpeaker.setOnClickListener { speakCurrentWord() }
        databind.languageRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            pronunciationType = when (checkedId) {
                R.id.btnEnglish -> PronunciationType.UK
                else -> PronunciationType.US
            }
            renderPhonetic(viewModel.currentWord.value)
        }
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

        hideOptionalSections()
        viewModel.loadWord(args.wordId, args.wordText)
    }

    override fun createObserver() {
        lifecycleScope.launch {
            launch {
                viewModel.currentWord.collect { word ->
                    renderPhonetic(word)
                    val hasRelations = synonymsAdapter.submitRelations(
                        word?.synonyms.orEmpty(),
                        word?.antonyms.orEmpty()
                    )
                    setOptionalSectionVisible(
                        databind.sectionSynonymsHeader,
                        databind.rvSynonyms,
                        hasRelations
                    )
                }
            }
            launch {
                viewModel.definitions.collect { definitions ->
                    definitionsAdapter.submitList(definitions)
                }
            }
            launch {
                viewModel.wordExamples.collect { examples ->
                    val visibleExamples = examples.visibleWordExamples()
                    examplesAdapter.submitList(visibleExamples)
                    setOptionalSectionVisible(
                        databind.sectionExamplesHeader,
                        databind.rvExamples,
                        visibleExamples.isNotEmpty()
                    )
                }
            }
            launch {
                viewModel.wordRoots.collect { roots ->
                    val visibleRoots = roots.visibleWordRoots()
                    rootsAdapter.submitList(visibleRoots)
                    setOptionalSectionVisible(
                        databind.sectionRootsHeader,
                        databind.rvRoot,
                        visibleRoots.isNotEmpty()
                    )
                }
            }
            launch {
                viewModel.wordForm.collect { forms ->
                    val visibleForms = forms.visibleWordForms()
                    formAdapter.submitList(visibleForms)
                    setOptionalSectionVisible(
                        databind.sectionInflectionHeader,
                        databind.rvInflection,
                        visibleForms.isNotEmpty()
                    )
                }
            }
        }
    }

    private fun hideOptionalSections() {
        setOptionalSectionVisible(databind.sectionExamplesHeader, databind.rvExamples, false)
        setOptionalSectionVisible(databind.sectionInflectionHeader, databind.rvInflection, false)
        setOptionalSectionVisible(databind.sectionRootsHeader, databind.rvRoot, false)
        setOptionalSectionVisible(databind.sectionSynonymsHeader, databind.rvSynonyms, false)

        Log.d("TAG", "hideOptionalSections: ${databind.rvSynonyms}")
    }

    private fun setOptionalSectionVisible(header: View, list: View, visible: Boolean) {
        header.isVisible = visible
        list.isVisible = visible
    }

    private fun renderPhonetic(word: Word?) {
        databind.tvPhonetic.text = when (pronunciationType) {
            PronunciationType.US -> word?.phoneticUS.orEmpty()
            PronunciationType.UK -> word?.phoneticUK.orEmpty()
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

    private fun speakCurrentWord() {
        val word = viewModel.currentWord.value?.word?.takeIf { it.isNotBlank() } ?: return
        val locale = when (pronunciationType) {
            PronunciationType.US -> "en-US"
            PronunciationType.UK -> "en-GB"
        }
        speakWord(word, locale)
    }

    private fun speakWord(word: String, locale: String) {
        if (word.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val audioOutput = synthesizeSpeech(
                SpeechTask.SynthesizeWord(
                    text = word,
                    locale = locale
                )
            ).audioOutputOrNull()
            if (audioOutput == null) {
                viewModel.showToast(getString(R.string.practice_audio_unavailable))
                return@launch
            }
            playSpeechOutput(audioOutput)
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
