package com.chen.memorizewords.feature.learning.ui.test

import androidx.fragment.app.Fragment
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode

interface LearningTestContract {
    val mode: LearningTestMode
    fun createFragment(): Fragment
}
