package com.chen.memorizewords.feature.wordbook.plan

import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.domain.repository.WordOrderType
import com.chen.memorizewords.feature.wordbook.R
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanSettingViewModelMappingsTest {

    @Test
    fun `study mode mapping exposes correct title resource`() {
        assertEquals(
            R.string.module_wordbook_study_mode_meaning_title,
            StudyPlanSettingViewModel.studyModeUiModelFor(LearningTestMode.MEANING_CHOICE).titleRes
        )
        assertEquals(
            R.string.module_wordbook_study_mode_spelling_title,
            StudyPlanSettingViewModel.studyModeUiModelFor(LearningTestMode.SPELLING).titleRes
        )
        assertEquals(
            R.string.module_wordbook_study_mode_listening_title,
            StudyPlanSettingViewModel.studyModeUiModelFor(LearningTestMode.LISTENING).titleRes
        )
    }

    @Test
    fun `word order mapping exposes correct title resource`() {
        assertEquals(
            R.string.module_wordbook_word_order_random,
            StudyPlanSettingViewModel.wordOrderLabelRes(WordOrderType.RANDOM)
        )
        assertEquals(
            R.string.module_wordbook_word_order_alpha_asc,
            StudyPlanSettingViewModel.wordOrderLabelRes(WordOrderType.ALPHABETIC_ASC)
        )
        assertEquals(
            R.string.module_wordbook_word_order_alpha_desc,
            StudyPlanSettingViewModel.wordOrderLabelRes(WordOrderType.ALPHABETIC_DESC)
        )
        assertEquals(
            R.string.module_wordbook_word_order_length_asc,
            StudyPlanSettingViewModel.wordOrderLabelRes(WordOrderType.LENGTH_ASC)
        )
        assertEquals(
            R.string.module_wordbook_word_order_length_desc,
            StudyPlanSettingViewModel.wordOrderLabelRes(WordOrderType.LENGTH_DESC)
        )
    }
}
