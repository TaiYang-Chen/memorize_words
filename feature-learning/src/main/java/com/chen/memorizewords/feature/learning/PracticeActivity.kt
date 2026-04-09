package com.chen.memorizewords.feature.learning

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.feature.learning.databinding.ActivityPracticeBinding
import com.chen.memorizewords.feature.learning.ui.practice.AudioLoopPracticeFragment
import com.chen.memorizewords.feature.learning.ui.practice.ListeningPracticeFragment
import com.chen.memorizewords.feature.learning.ui.practice.PracticeSessionViewModel
import com.chen.memorizewords.feature.learning.ui.practice.ShadowingPracticeFragment
import com.chen.memorizewords.feature.learning.ui.practice.SpellingPracticeFragment
import com.chen.memorizewords.core.navigation.PracticeEntryExtras
import dagger.hilt.android.AndroidEntryPoint

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
    }

    private lateinit var binding: ActivityPracticeBinding
    private val sessionViewModel: PracticeSessionViewModel by viewModels()
    private var practiceMode: PracticeMode = PracticeMode.LISTENING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPracticeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener { finish() }

        practiceMode = intent.getStringExtra(EXTRA_PRACTICE_MODE)
            ?.let { runCatching { PracticeMode.valueOf(it) }.getOrNull() }
            ?: PracticeMode.LISTENING
        val selectedIds = intent.getLongArrayExtra(EXTRA_SELECTED_WORD_IDS)
        val randomCount = intent.getIntExtra(EXTRA_RANDOM_COUNT, 20)
        val entryType = intent.getStringExtra(EXTRA_ENTRY_TYPE)
            ?.let { runCatching { PracticeEntryType.valueOf(it) }.getOrNull() }
            ?: if (selectedIds != null) PracticeEntryType.SELF else PracticeEntryType.RANDOM
        val entryCountRaw = intent.getIntExtra(EXTRA_ENTRY_COUNT, 0).coerceAtLeast(0)
        val entryCount = if (entryCountRaw > 0) {
            entryCountRaw
        } else if (selectedIds != null) {
            selectedIds.size
        } else {
            randomCount.coerceAtLeast(0)
        }
        if (!isAudioLoopMode()) {
            sessionViewModel.startSession(
                mode = practiceMode,
                entryType = entryType,
                entryCount = entryCount,
                selectedIds = selectedIds,
                randomCount = randomCount
            )
            selectedIds?.let { sessionViewModel.setSessionWordIds(it.toList()) }
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
                ARG_SELECTED_WORD_IDS to selectedIds,
                ARG_RANDOM_COUNT to randomCount
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.practice_fragment_container, fragment, practiceMode.name)
                .commit()
        }
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
