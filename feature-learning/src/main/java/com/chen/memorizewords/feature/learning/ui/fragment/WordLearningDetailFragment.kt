package com.chen.memorizewords.feature.learning.ui.fragment

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.practice.usecase.SynthesizeSpeechUseCase
import com.chen.memorizewords.feature.learning.LinearSpacingItemDecoration
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.adapter.DefinitionsAdapter
import com.chen.memorizewords.feature.learning.adapter.ExamplesAdapter
import com.chen.memorizewords.feature.learning.adapter.FormAdapter
import com.chen.memorizewords.feature.learning.adapter.RootsAdapter
import com.chen.memorizewords.feature.learning.adapter.SynonymsAdapter
import com.chen.memorizewords.feature.learning.adapter.clearGlobalClickableWordHighlight
import com.chen.memorizewords.feature.learning.databinding.FragmentWordLearningDetailBinding
import com.chen.memorizewords.feature.learning.ui.learning.LearningViewModel
import com.chen.memorizewords.feature.learning.ui.visibleWordExamples
import com.chen.memorizewords.feature.learning.ui.visibleWordForms
import com.chen.memorizewords.feature.learning.ui.visibleWordRoots
import com.chen.memorizewords.feature.learning.ui.speech.audioOutputOrNull
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import com.chen.memorizewords.domain.practice.speech.SpeechTask
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WordLearningDetailFragment :
    BaseFragment<WordLearningDetailViewModel, FragmentWordLearningDetailBinding>() {

    override val viewModel: WordLearningDetailViewModel by lazy {
        ViewModelProvider(this)[WordLearningDetailViewModel::class.java]
    }

    @Inject
    lateinit var synthesizeSpeech: SynthesizeSpeechUseCase

    private val examplesAdapter = ExamplesAdapter(
        onclickWord = { token, rect ->
            viewModel.requestWordQuickLookup(token, rect)
        },
        onSpeakSentence = { sentence ->
            speakSentence(sentence)
        }
    )
    private val definitionsAdapter = DefinitionsAdapter()
    private val rootsAdapter = RootsAdapter()
    private val synonymsAdapter = SynonymsAdapter()
    private val formAdapter = FormAdapter()

    private val parentViewModel: LearningViewModel by viewModels({ requireParentFragment() })

    private lateinit var popupController: WordQuickPopupController
    private var mediaPlayer: MediaPlayer? = null

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        popupController = WordQuickPopupController(
            activity = requireActivity(),
            onSpeakUs = { speakWord(it, "en-US") },
            onSpeakUk = { speakWord(it, "en-GB") },
            onViewFull = { navigateToWordEntry(it) },
            onDismissed = {
                clearGlobalClickableWordHighlight()
                viewModel.dismissWordQuickPopup()
            }
        )
        initRvDefinitions()
        initRvExamples()
        initRvRoot()
        initRvSynonyms()
        initRvInflection()
        hideOptionalSections()
        bindParentScrollDismiss()
    }

    private fun initRvDefinitions() {
        databind.rvDefinitions.adapter = definitionsAdapter
        databind.rvDefinitions.layoutManager = LinearLayoutManager(requireContext())
        databind.rvDefinitions.isNestedScrollingEnabled = false
        databind.rvDefinitions.addItemDecoration(LinearSpacingItemDecoration(8.dpToPx(requireContext())))
    }

    private fun initRvExamples() {
        databind.rvExamples.adapter = examplesAdapter
        databind.rvExamples.layoutManager = LinearLayoutManager(requireContext())
        databind.rvExamples.isNestedScrollingEnabled = false
        databind.rvExamples.addItemDecoration(LinearSpacingItemDecoration(16.dpToPx(requireContext())))
    }

    private fun initRvRoot() {
        databind.rvRoot.adapter = rootsAdapter
        databind.rvRoot.layoutManager = LinearLayoutManager(requireContext())
        databind.rvRoot.isNestedScrollingEnabled = false
        databind.rvRoot.addItemDecoration(LinearSpacingItemDecoration(16.dpToPx(requireContext())))
    }

    private fun initRvSynonyms() {
        databind.rvSynonyms.adapter = synonymsAdapter
        databind.rvSynonyms.layoutManager = FlexboxLayoutManager(requireContext()).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }
        databind.rvSynonyms.isNestedScrollingEnabled = false
    }

    private fun initRvInflection() {
        databind.rvInflection.adapter = formAdapter
        databind.rvInflection.layoutManager = LinearLayoutManager(requireContext())
        databind.rvInflection.isNestedScrollingEnabled = false
    }

    override fun createObserver() {
        lifecycleScope.launch {
            launch {
                viewModel.definitions.collect { definitionsAdapter.submitGroupedDefinitions(it) }
            }
            launch {
                viewModel.wordExamples.collect {
                    val examples = it.visibleWordExamples()
                    examplesAdapter.submitList(examples)
                    setOptionalSectionVisible(
                        databind.sectionExamplesHeader,
                        databind.rvExamples,
                        examples.isNotEmpty()
                    )
                }
            }
            launch {
                viewModel.wordRoots.collect {
                    val roots = it.visibleWordRoots()
                    rootsAdapter.submitList(roots)
                    setOptionalSectionVisible(
                        databind.sectionRootsHeader,
                        databind.rvRoot,
                        roots.isNotEmpty()
                    )
                }
            }
            launch {
                viewModel.currentWord.collect { word ->
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
                viewModel.wordForm.collect {
                    val forms = it.visibleWordForms()
                    formAdapter.submitList(forms)
                    setOptionalSectionVisible(
                        databind.sectionInflectionHeader,
                        databind.rvInflection,
                        forms.isNotEmpty()
                    )
                }
            }
            launch {
                parentViewModel.uiState.collect { ui ->
                    ui.currentWord?.let { viewModel.setWord(it) }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.wordQuickPopupState.collect { state ->
                    if (state == null) {
                        popupController.dismiss()
                        clearGlobalClickableWordHighlight()
                    } else {
                        popupController.render(state)
                    }
                }
            }
        }
    }

    private fun hideOptionalSections() {
        setOptionalSectionVisible(databind.sectionExamplesHeader, databind.rvExamples, false)
        setOptionalSectionVisible(databind.sectionInflectionHeader, databind.rvInflection, false)
        setOptionalSectionVisible(databind.sectionRootsHeader, databind.rvRoot, false)
        setOptionalSectionVisible(databind.sectionSynonymsHeader, databind.rvSynonyms, false)
    }

    private fun setOptionalSectionVisible(header: View, list: View, visible: Boolean) {
        header.isVisible = visible
        list.isVisible = visible
    }

    override fun onStop() {
        super.onStop()
        dismissQuickPopup()
    }

    override fun onDestroyView() {
        dismissQuickPopup()
        releaseMediaPlayer()
        super.onDestroyView()
    }

    private fun dismissQuickPopup() {
        popupController.dismiss()
        clearGlobalClickableWordHighlight()
        viewModel.dismissWordQuickPopup()
    }

    private fun bindParentScrollDismiss() {
        val scrollView =
            requireParentFragment().view?.findViewById<NestedScrollView>(R.id.nestedScrollView2)
        scrollView?.setOnScrollChangeListener { _, _, _, _, _ ->
            dismissQuickPopup()
        }
    }

    private fun navigateToWordEntry(word: Word) {
        dismissQuickPopup()
        requireParentFragment().findNavController().navigate(
            R.id.action_global_wordEntryDetailFragment,
            bundleOf(
                "wordId" to word.id,
                "wordText" to word.word
            )
        )
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
