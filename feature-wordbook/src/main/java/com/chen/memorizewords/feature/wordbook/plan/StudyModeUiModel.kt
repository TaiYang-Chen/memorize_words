package com.chen.memorizewords.feature.wordbook.plan

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.chen.memorizewords.domain.model.learning.LearningTestMode

data class StudyModeUiModel(
    val mode: LearningTestMode,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int
)
