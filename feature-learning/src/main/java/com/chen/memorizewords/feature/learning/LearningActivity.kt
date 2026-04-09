package com.chen.memorizewords.feature.learning

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.learning.LearningSessionRequest
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.feature.learning.databinding.ActivityLearningBinding
import com.chen.memorizewords.core.navigation.LearningEntry
import com.chen.memorizewords.core.navigation.LearningEntryExtras
import com.chen.memorizewords.core.navigation.PracticeEntry
import com.chen.memorizewords.core.navigation.PracticeEntryExtras
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
class LearningActivity :
    BaseVmDbActivity<BaseViewModel, ActivityLearningBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    companion object {
        const val EXTRA_INIT_LEARNED_COUNT = LearningEntryExtras.EXTRA_INIT_LEARNED_COUNT
        const val EXTRA_WORD_IDS = LearningEntryExtras.EXTRA_WORD_IDS
        const val EXTRA_LEARNING_TYPE = LearningEntryExtras.EXTRA_LEARNING_TYPE
        const val EXTRA_LEARNING_COUNT = LearningEntryExtras.EXTRA_LEARNING_COUNT
        const val EXTRA_OPEN_WORD_ID = LearningEntryExtras.EXTRA_OPEN_WORD_ID
        const val EXTRA_OPEN_FROM_FLOATING = LearningEntryExtras.EXTRA_OPEN_FROM_FLOATING
    }

    override fun createObserver() {
    }

    override fun initView(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val wordId = intent.getLongExtra(EXTRA_OPEN_WORD_ID, -1L)
        if (wordId <= 0L) return
        databind.root.post {
            val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_learning)
                    as? NavHostFragment ?: return@post
            navHostFragment.navController.navigate(
                R.id.action_global_wordEntryDetailFragment,
                bundleOf(
                    "wordId" to wordId,
                    "fromFloating" to intent.getBooleanExtra(EXTRA_OPEN_FROM_FLOATING, false)
                )
            )
        }
    }

    override fun navControllerId() = R.id.nav_host_fragment_activity_learning
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LearningFeatureEntryModule {

    @Binds
    @Singleton
    abstract fun bindLearningEntry(impl: DefaultLearningEntry): LearningEntry

    @Binds
    @Singleton
    abstract fun bindPracticeEntry(impl: DefaultPracticeEntry): PracticeEntry
}

@Singleton
class DefaultLearningEntry @Inject constructor() : LearningEntry {
    override fun createLearningIntent(context: Context, request: LearningSessionRequest): Intent {
        return Intent(context, LearningActivity::class.java).apply {
            putExtra(LearningEntryExtras.EXTRA_INIT_LEARNED_COUNT, request.initialLearnedCount)
            putExtra(LearningEntryExtras.EXTRA_WORD_IDS, request.wordIds.toLongArray())
            putExtra(LearningEntryExtras.EXTRA_LEARNING_TYPE, request.sessionType)
            putExtra(LearningEntryExtras.EXTRA_LEARNING_COUNT, request.sessionWordCount)
        }
    }

    override fun createOpenWordIntent(context: Context, wordId: Long, fromFloating: Boolean): Intent {
        return Intent(context, LearningActivity::class.java).apply {
            putExtra(LearningEntryExtras.EXTRA_OPEN_WORD_ID, wordId)
            putExtra(LearningEntryExtras.EXTRA_OPEN_FROM_FLOATING, fromFloating)
        }
    }
}

@Singleton
class DefaultPracticeEntry @Inject constructor() : PracticeEntry {
    override fun createPracticeIntent(
        context: Context,
        mode: PracticeMode,
        randomCount: Int,
        entryType: PracticeEntryType,
        entryCount: Int,
        selectedIds: LongArray?
    ): Intent {
        return Intent(context, PracticeActivity::class.java).apply {
            putExtra(PracticeEntryExtras.EXTRA_PRACTICE_MODE, mode.name)
            putExtra(PracticeEntryExtras.EXTRA_RANDOM_COUNT, randomCount)
            putExtra(PracticeEntryExtras.EXTRA_ENTRY_TYPE, entryType.name)
            putExtra(PracticeEntryExtras.EXTRA_ENTRY_COUNT, entryCount)
            selectedIds?.let { putExtra(PracticeEntryExtras.EXTRA_SELECTED_WORD_IDS, it) }
        }
    }

    override fun createWordPickerIntent(context: Context, initialSelectedIds: LongArray?): Intent {
        return Intent(context, PracticeWordPickerActivity::class.java).apply {
            initialSelectedIds?.let {
                putExtra(PracticeEntryExtras.EXTRA_INITIAL_SELECTED_WORD_IDS, it)
            }
        }
    }

    override fun extractSelectedWordIds(intent: Intent?): LongArray? {
        return intent?.getLongArrayExtra(PracticeEntryExtras.EXTRA_SELECTED_WORD_IDS)
    }
}
