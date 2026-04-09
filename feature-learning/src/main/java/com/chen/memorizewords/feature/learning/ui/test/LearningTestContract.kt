package com.chen.memorizewords.feature.learning.ui.test

import androidx.fragment.app.Fragment
import com.chen.memorizewords.domain.model.learning.LearningTestMode

interface LearningTestContract {
    val mode: LearningTestMode
    fun createFragment(): Fragment
}
