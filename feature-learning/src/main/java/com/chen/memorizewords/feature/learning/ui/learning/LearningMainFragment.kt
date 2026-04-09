package com.chen.memorizewords.feature.learning.ui.learning

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.media.MediaPlayer
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.domain.model.words.word.PronunciationType
import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.feature.learning.LearningActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentWordLearningBinding
import com.chen.memorizewords.feature.learning.ui.fragment.WordLearningDetailFragment
import com.chen.memorizewords.feature.learning.ui.fragment.WordLearningTestFragment
import com.chen.memorizewords.feature.learning.ui.speech.audioOutputOrNull
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import com.chen.memorizewords.speech.api.SpeechAudioOutput
import com.chen.memorizewords.speech.api.SpeechTask
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LearningMainFragment :
    BaseFragment<LearningViewModel, FragmentWordLearningBinding>() {

    override val viewModel: LearningViewModel by lazy {
        ViewModelProvider(this)[LearningViewModel::class.java]
    }

    private data class RenderKey(val state: LearningViewModel.LearningState)

    @Inject
    lateinit var synthesizeSpeech: SynthesizeSpeechUseCase

    private var lastRenderKey: RenderKey? = null
    private var autoSpeakKey: String? = null
    private var mediaPlayer: MediaPlayer? = null

    private val testFragmentTag = "learning_test"
    private val detailFragmentTag = "learning_detail"

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        ensureChildFragments()

        val intent = requireActivity().intent
        val initialLearnedCount = intent?.getIntExtra(
            LearningActivity.EXTRA_INIT_LEARNED_COUNT,
            0
        ) ?: 0
        val initialWordIds = readInitialWordIds(intent, arguments)
        val args = arguments
        val sessionType = intent?.getIntExtra(
            LearningActivity.EXTRA_LEARNING_TYPE,
            args?.getInt(LearningActivity.EXTRA_LEARNING_TYPE) ?: 0
        ) ?: 0
        val sessionWordCount = intent?.getIntExtra(
            LearningActivity.EXTRA_LEARNING_COUNT,
            args?.getInt(LearningActivity.EXTRA_LEARNING_COUNT)
                ?.takeIf { it > 0 }
                ?: initialWordIds.size
        )
            ?.takeIf { it > 0 }
            ?: initialWordIds.size

        viewModel.setSessionInfo(sessionType, sessionWordCount)
        viewModel.loadDataByIds(
            initialLearnedCount = initialLearnedCount,
            initialWordIds = initialWordIds
        )

        databind.includeBaseWord.languageRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.btnChinese -> viewModel.onSetPronunciationType(PronunciationType.US)
                R.id.btnEnglish -> viewModel.onSetPronunciationType(PronunciationType.UK)
            }
        }
        databind.includeBaseWord.ivSpeaker.setOnClickListener {
            speakCurrentWord()
        }
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val key = RenderKey(state = state.learningState)
                    if (key != lastRenderKey) {
                        lastRenderKey = key
                        renderState(state.learningState)
                    }
                    autoSpeakIfNeeded(state)
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is LearningViewModel.Route.ToLearningDone -> {
                findNavController().navigate(
                    R.id.action_learningMainFragment_to_learningDoneFragment,
                    bundleOf(
                        "words" to target.wordIds,
                        "sessionType" to target.sessionType,
                        "sessionWordCount" to target.sessionWordCount,
                        "answeredCount" to target.answeredCount,
                        "correctCount" to target.correctCount,
                        "wrongCount" to target.wrongCount,
                        "studyDurationMs" to target.studyDurationMs
                    )
                )
            }

            is LearningViewModel.Route.ToWordExamPractice -> {
                findNavController().navigate(
                    R.id.action_learningMainFragment_to_wordExamPracticeFragment,
                    bundleOf(
                        "wordId" to target.wordId,
                        "wordText" to target.wordText
                    )
                )
            }

            else -> Unit
        }
    }

    override fun onUiEffect(effect: UiEffect) {
        when (effect) {
            is LearningShareEffect.ShowWordShareSheet -> showWordShareSheet(effect.actions)
            is LearningShareEffect.CopyWordShareText -> copyWordShareText(effect.text)
            else -> super.onUiEffect(effect)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onLearningPageVisible()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onLearningPageHidden()
    }

    override fun onDestroyView() {
        releaseMediaPlayer()
        autoSpeakKey = null
        lastRenderKey = null
        super.onDestroyView()
    }

    private fun ensureChildFragments() {
        var changed = false
        val testFragment = childFragmentManager.findFragmentByTag(testFragmentTag)
            ?: WordLearningTestFragment().also { fragment ->
                childFragmentManager.beginTransaction()
                    .add(R.id.fragment_include, fragment, testFragmentTag)
                    .commitNow()
                changed = true
            }
        val detailFragment = childFragmentManager.findFragmentByTag(detailFragmentTag)
            ?: WordLearningDetailFragment().also { fragment ->
                childFragmentManager.beginTransaction()
                    .add(R.id.fragment_include, fragment, detailFragmentTag)
                    .hide(fragment)
                    .commitNow()
                changed = true
            }

        if (changed) {
            childFragmentManager.beginTransaction()
                .show(testFragment)
                .hide(detailFragment)
                .commitNow()
        }
    }

    private fun renderState(state: LearningViewModel.LearningState) {
        val testFragment = childFragmentManager.findFragmentByTag(testFragmentTag) ?: return
        val detailFragment = childFragmentManager.findFragmentByTag(detailFragmentTag) ?: return
        childFragmentManager.beginTransaction()
            .apply {
                if (state == LearningViewModel.LearningState.TEST) {
                    show(testFragment)
                    hide(detailFragment)
                } else {
                    hide(testFragment)
                    show(detailFragment)
                }
            }
            .commit()
    }

    private fun autoSpeakIfNeeded(state: LearningViewModel.LearningUiState) {
        val currentWord = state.currentWord ?: return
        if (state.learningState != LearningViewModel.LearningState.TEST) return
        if (state.currentTestMode != LearningTestMode.LISTENING) return
        if (state.isAnswered) return

        val key = "${state.questionToken}_${currentWord.id}_${state.pronunciationType.name}"
        if (key == autoSpeakKey) return
        autoSpeakKey = key
        speakCurrentWord()
    }

    private fun speakCurrentWord() {
        val state = viewModel.uiState.value
        val word = state.currentWord?.word?.takeIf { it.isNotBlank() } ?: return
        val locale = when (state.pronunciationType) {
            PronunciationType.US -> "en-US"
            PronunciationType.UK -> "en-GB"
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val audioOutput = synthesizeSpeech(
                SpeechTask.SynthesizeWord(
                    text = word,
                    locale = locale
                )
            ).audioOutputOrNull() ?: return@launch
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

    private fun showWordShareSheet(actions: List<LearningShareActionItem>) {
        if (parentFragmentManager.findFragmentByTag(WordShareBottomSheetDialog.TAG) != null) return
        WordShareBottomSheetDialog(
            actions = actions,
            onActionClicked = viewModel::onWordShareActionClicked
        ).show(parentFragmentManager, WordShareBottomSheetDialog.TAG)
    }

    private fun copyWordShareText(text: String) {
        val clipboardManager = requireContext().getSystemService(ClipboardManager::class.java) ?: return
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.learning_share_sheet_title), text)
        )
        viewModel.onWordShareCopied()
    }

    private fun readInitialWordIds(intent: android.content.Intent?, args: Bundle?): List<Long> {
        val idsFromIntent = intent?.getLongArrayExtra(LearningActivity.EXTRA_WORD_IDS)?.toList()
        val idsFromArgs = args?.getLongArray(LearningActivity.EXTRA_WORD_IDS)?.toList()
        return idsFromIntent ?: idsFromArgs.orEmpty()
    }
}
