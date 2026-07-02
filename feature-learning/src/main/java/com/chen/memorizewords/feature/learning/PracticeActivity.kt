package com.chen.memorizewords.feature.learning

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.os.bundleOf
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.feature.learning.databinding.ActivityPracticeBinding
import com.chen.memorizewords.feature.learning.ui.practice.AudioLoopPracticeFragment
import com.chen.memorizewords.feature.learning.ui.practice.ListeningPracticeFragment
import com.chen.memorizewords.feature.learning.ui.practice.PracticeSessionViewModel
import com.chen.memorizewords.feature.learning.ui.practice.ShadowingPracticeFragment
import com.chen.memorizewords.feature.learning.ui.practice.SpellingCompletionResult
import com.chen.memorizewords.feature.learning.ui.practice.SpellingPracticeDoneFragment
import com.chen.memorizewords.feature.learning.ui.practice.SpellingPracticeFragment
import com.chen.memorizewords.core.navigation.PracticeEntryExtras
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PracticeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRACTICE_MODE = PracticeEntryExtras.EXTRA_PRACTICE_MODE
        const val EXTRA_SELECTED_WORD_IDS = PracticeEntryExtras.EXTRA_SELECTED_WORD_IDS
        const val EXTRA_RANDOM_COUNT = PracticeEntryExtras.EXTRA_RANDOM_COUNT
        const val EXTRA_ENTRY_TYPE = PracticeEntryExtras.EXTRA_ENTRY_TYPE
        const val EXTRA_ENTRY_COUNT = PracticeEntryExtras.EXTRA_ENTRY_COUNT

        const val ARG_SELECTED_WORD_IDS = PracticeEntryExtras.ARG_SELECTED_WORD_IDS
        const val ARG_RANDOM_COUNT = PracticeEntryExtras.ARG_RANDOM_COUNT
        const val ARG_PRACTICE_MODE = "arg_practice_mode"
    }

    private lateinit var binding: ActivityPracticeBinding
    private val sessionViewModel: PracticeSessionViewModel by viewModels()
    private var practiceMode: PracticeMode = PracticeMode.LISTENING
    private var selectedWordIds: LongArray? = null
    private var randomCount: Int = 20
    private var entryType: PracticeEntryType = PracticeEntryType.RANDOM
    private var entryCount: Int = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPracticeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener { finish() }

        practiceMode = intent.getStringExtra(EXTRA_PRACTICE_MODE)
            ?.let { runCatching { PracticeMode.valueOf(it) }.getOrNull() }
            ?: PracticeMode.LISTENING
        binding.topAppBar.isVisible =
            practiceMode != PracticeMode.LISTENING &&
                practiceMode != PracticeMode.SHADOWING &&
                practiceMode != PracticeMode.SPELLING &&
                practiceMode != PracticeMode.AUDIO_LOOP
        selectedWordIds = intent.getLongArrayExtra(EXTRA_SELECTED_WORD_IDS)
        randomCount = intent.getIntExtra(EXTRA_RANDOM_COUNT, 20)
        entryType = intent.getStringExtra(EXTRA_ENTRY_TYPE)
            ?.let { runCatching { PracticeEntryType.valueOf(it) }.getOrNull() }
            ?: if (selectedWordIds != null) PracticeEntryType.SELF else PracticeEntryType.RANDOM
        val entryCountRaw = intent.getIntExtra(EXTRA_ENTRY_COUNT, 0).coerceAtLeast(0)
        entryCount = if (entryCountRaw > 0) {
            entryCountRaw
        } else if (selectedWordIds != null) {
            selectedWordIds?.size ?: 0
        } else {
            randomCount.coerceAtLeast(0)
        }
        if (!isAudioLoopMode()) {
            sessionViewModel.startSession(
                mode = practiceMode,
                entryType = entryType,
                entryCount = entryCount,
                selectedIds = selectedWordIds,
                randomCount = randomCount
            )
            selectedWordIds?.let { sessionViewModel.setSessionWordIds(it.toList()) }
        }

        if (savedInstanceState == null) {
            binding.topAppBar.title = titleOf(practiceMode)
            val fragment = when (practiceMode) {
                PracticeMode.LISTENING -> ListeningPracticeFragment()
                PracticeMode.SHADOWING -> ShadowingPracticeFragment()
                PracticeMode.SPELLING -> SpellingPracticeFragment()
                PracticeMode.AUDIO_LOOP -> AudioLoopPracticeFragment()
                PracticeMode.EXAM -> ListeningPracticeFragment()
            }
            fragment.arguments = bundleOf(
                ARG_SELECTED_WORD_IDS to selectedWordIds,
                ARG_RANDOM_COUNT to randomCount,
                ARG_PRACTICE_MODE to practiceMode.name
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.practice_fragment_container, fragment, practiceMode.name)
                .commit()
        }
    }

    fun showSpellingDone(result: SpellingCompletionResult) {
        if (practiceMode != PracticeMode.SPELLING) return
        sessionViewModel.updateSessionSummary(result.summary)
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.practice_fragment_container,
                SpellingPracticeDoneFragment.newInstance(result),
                SpellingPracticeDoneFragment.TAG
            )
            .commit()
    }

    fun restartSpellingPracticeWithWrongWords(wordIds: LongArray) {
        if (wordIds.isEmpty()) return
        sessionViewModel.finishSession()
        selectedWordIds = wordIds
        randomCount = wordIds.size
        entryType = PracticeEntryType.SELF
        entryCount = wordIds.size
        startPracticeSessionForCurrentArgs()
        replaceWithSpellingPractice()
    }

    fun restartCurrentSpellingPracticeSet() {
        sessionViewModel.finishSession()
        startPracticeSessionForCurrentArgs()
        replaceWithSpellingPractice()
    }

    override fun onResume() {
        super.onResume()
        if (!isAudioLoopMode()) {
            sessionViewModel.onPageVisible()
        }
    }

    override fun onPause() {
        if (!isAudioLoopMode()) {
            sessionViewModel.onPageHidden()
        }
        super.onPause()
    }

    override fun onStop() {
        if (!isAudioLoopMode() && isFinishing) {
            sessionViewModel.finishSession()
        }
        super.onStop()
    }

    private fun isAudioLoopMode(): Boolean {
        return practiceMode == PracticeMode.AUDIO_LOOP
    }

    private fun startPracticeSessionForCurrentArgs() {
        if (isAudioLoopMode()) return
        sessionViewModel.startSession(
            mode = practiceMode,
            entryType = entryType,
            entryCount = entryCount,
            selectedIds = selectedWordIds,
            randomCount = randomCount
        )
        selectedWordIds?.let { sessionViewModel.setSessionWordIds(it.toList()) }
        sessionViewModel.onPageVisible()
    }

    private fun replaceWithSpellingPractice() {
        val fragment = SpellingPracticeFragment().apply {
            arguments = bundleOf(
                ARG_SELECTED_WORD_IDS to selectedWordIds,
                ARG_RANDOM_COUNT to randomCount,
                ARG_PRACTICE_MODE to practiceMode.name
            )
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.practice_fragment_container, fragment, practiceMode.name)
            .commit()
    }

    private fun titleOf(mode: PracticeMode): String {
        return when (mode) {
            PracticeMode.LISTENING -> getString(R.string.practice_mode_title_listening)
            PracticeMode.SHADOWING -> getString(R.string.practice_mode_title_shadowing)
            PracticeMode.SPELLING -> getString(R.string.practice_mode_title_spelling)
            PracticeMode.AUDIO_LOOP -> getString(R.string.practice_mode_title_audio_loop)
            PracticeMode.EXAM -> "真题练习"
        }
    }
}

internal fun shouldUseListeningCustomHeader(mode: PracticeMode): Boolean {
    return mode == PracticeMode.LISTENING
}
