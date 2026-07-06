package com.chen.memorizewords.feature.learning.ui.learning

import android.content.ClipData
import android.content.ClipboardManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import com.chen.memorizewords.core.ui.ext.dpToPx
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.word.model.word.PronunciationType
import com.chen.memorizewords.domain.practice.usecase.SynthesizeSpeechUseCase
import com.chen.memorizewords.feature.learning.LearningActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentWordLearningBinding
import com.chen.memorizewords.feature.learning.ui.fragment.WordLearningDetailFragment
import com.chen.memorizewords.feature.learning.ui.fragment.WordLearningTestFragment
import com.chen.memorizewords.feature.learning.ui.speech.audioOutputOrNull
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import com.chen.memorizewords.domain.practice.speech.SpeechTask
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
    private var lastAutoPlayedWordKey: LearningAutoPlayWordKey? = null
    private var mediaPlayer: MediaPlayer? = null
    private val updateBottomPaddingRunnable = Runnable {
        updateLearningScrollBottomPadding()
    }

    private val testFragmentTag = "learning_test"
    private val detailFragmentTag = "learning_detail"

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        ensureTestFragment()

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

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.requestExitLearningConfirm()
                }
            }
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
        scheduleLearningScrollBottomPaddingUpdate()
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
                    scheduleAutoPlayCurrentWord(state)
                    scheduleLearningScrollBottomPaddingUpdate()
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            LearningViewModel.Route.ToCheckIn -> {
                navigateFromLearningMain(R.id.action_learningMainFragment_to_learningCheckInFragment)
            }

            is LearningViewModel.Route.ToLearningDone -> {
                navigateFromLearningMain(
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
                navigateFromLearningMain(
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

    private fun navigateFromLearningMain(actionId: Int, args: Bundle? = null) {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.learningMainFragment) return
        navController.navigate(actionId, args)
    }

    override fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action == LearningViewModel.ACTION_EXIT_LEARNING) {
            requireActivity().finish()
            return
        }
        super.onConfirmDialog(event)
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
        view?.removeCallbacks(updateBottomPaddingRunnable)
        databind.composeBtn.removeCallbacks(updateBottomPaddingRunnable)
        releaseMediaPlayer()
        lastRenderKey = null
        lastAutoPlayedWordKey = null
        super.onDestroyView()
    }

    private fun ensureTestFragment() {
        if (childFragmentManager.findFragmentByTag(testFragmentTag) != null) return
        childFragmentManager.beginTransaction()
            .add(R.id.fragment_include, WordLearningTestFragment(), testFragmentTag)
            .commitNow()
    }

    private fun ensureDetailFragment() {
        if (childFragmentManager.findFragmentByTag(detailFragmentTag) != null) return
        val detailFragment = WordLearningDetailFragment()
        childFragmentManager.beginTransaction()
            .add(R.id.fragment_include, detailFragment, detailFragmentTag)
            .hide(detailFragment)
            .commitNow()
    }

    private fun renderState(state: LearningViewModel.LearningState) {
        ensureTestFragment()
        if (state == LearningViewModel.LearningState.DETAIL) {
            ensureDetailFragment()
        }
        val testFragment = childFragmentManager.findFragmentByTag(testFragmentTag) ?: return
        val detailFragment = childFragmentManager.findFragmentByTag(detailFragmentTag)
        childFragmentManager.beginTransaction()
            .apply {
                if (state == LearningViewModel.LearningState.TEST) {
                    show(testFragment)
                    detailFragment?.let(::hide)
                } else {
                    hide(testFragment)
                    detailFragment?.let(::show)
                }
            }
            .commit()
    }

    private fun scheduleLearningScrollBottomPaddingUpdate() {
        val root = view ?: return
        root.removeCallbacks(updateBottomPaddingRunnable)
        root.post(updateBottomPaddingRunnable)
    }

    private fun updateLearningScrollBottomPadding() {
        val root = view ?: return
        val context = root.context
        val binding = databind
        val layoutParams = binding.composeBtn.layoutParams as? ViewGroup.MarginLayoutParams
        val buttonHeight = LEARNING_BOTTOM_BUTTON_HEIGHT_DP.dpToPx(context)
        val bottomPadding = buttonHeight +
            (layoutParams?.topMargin ?: 0) +
            (layoutParams?.bottomMargin ?: 0) +
            LEARNING_SCROLL_EXTRA_BOTTOM_PADDING_DP.dpToPx(context)
        binding.nestedScrollView2.setPadding(
            binding.nestedScrollView2.paddingLeft,
            binding.nestedScrollView2.paddingTop,
            binding.nestedScrollView2.paddingRight,
            bottomPadding
        )
    }

    private fun scheduleAutoPlayCurrentWord(state: LearningViewModel.LearningUiState) {
        val key = resolveLearningAutoPlayWordKey(state) ?: return
        if (key == lastAutoPlayedWordKey) return
        lastAutoPlayedWordKey = key
        databind.root.post {
            if (view == null) return@post
            if (resolveLearningAutoPlayWordKey(viewModel.uiState.value) == key) {
                speakCurrentWord()
            }
        }
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

    private companion object {
        const val LEARNING_BOTTOM_BUTTON_HEIGHT_DP = 54
        const val LEARNING_SCROLL_EXTRA_BOTTOM_PADDING_DP = 16
    }
}
