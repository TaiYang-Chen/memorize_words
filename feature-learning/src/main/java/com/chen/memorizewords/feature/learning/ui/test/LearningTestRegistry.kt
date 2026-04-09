package com.chen.memorizewords.feature.learning.ui.test

import androidx.fragment.app.Fragment
import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.feature.learning.ui.fragment.WordLearningTestFragment

object LearningTestRegistry {

    private val contracts: Map<LearningTestMode, LearningTestContract> = listOf(
        object : LearningTestContract {
            override val mode: LearningTestMode = LearningTestMode.MEANING_CHOICE
            override fun createFragment(): Fragment = WordLearningTestFragment()
        },
        object : LearningTestContract {
            override val mode: LearningTestMode = LearningTestMode.SPELLING
            override fun createFragment(): Fragment = WordLearningTestFragment()
        },
        object : LearningTestContract {
            override val mode: LearningTestMode = LearningTestMode.LISTENING
            override fun createFragment(): Fragment = WordLearningTestFragment()
        }
    ).associateBy { it.mode }

    fun createFragment(mode: LearningTestMode): Fragment {
        return contracts[mode]?.createFragment()
            ?: contracts[LearningTestMode.MEANING_CHOICE]!!.createFragment()
    }

    fun supportedModes(): Set<LearningTestMode> = contracts.keys
}
