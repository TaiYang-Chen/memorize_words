package com.chen.memorizewords.feature.home.ui.home

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.feature.home.R
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTextFormatterTest {

    private val formatter = HomeTextFormatter(FakeResourceProvider)

    @Test
    fun `learn button subtitle shows full plan before learning starts`() {
        val text = formatter.formatLearnButtonSubtitleText(
            newCount = 0,
            plan = StudyPlan(dailyNewCount = 15)
        )

        assertEquals("新学15个单词，预计15分钟", text)
    }

    @Test
    fun `learn button subtitle shows completed and remaining counts while continuing`() {
        val text = formatter.formatLearnButtonSubtitleText(
            newCount = 6,
            plan = StudyPlan(dailyNewCount = 15)
        )

        assertEquals("已学6个单词，还剩9个单词即可完成任务，预计9分钟", text)
    }

    @Test
    fun `learn button subtitle offers boost after completing the plan`() {
        val text = formatter.formatLearnButtonSubtitleText(
            newCount = 15,
            plan = StudyPlan(dailyNewCount = 15)
        )

        assertEquals("今日已学15个单词，可继续加量新学", text)
    }

    @Test
    fun `learn button boost subtitle keeps actual over plan count`() {
        val text = formatter.formatLearnButtonSubtitleText(
            newCount = 18,
            plan = StudyPlan(dailyNewCount = 15)
        )

        assertEquals("今日已学18个单词，可继续加量新学", text)
    }

    private object FakeResourceProvider : ResourceProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String {
            return when (resId) {
                R.string.home_learn_subtitle_start ->
                    "新学${formatArgs[0]}个单词，预计${formatArgs[1]}分钟"

                R.string.home_learn_subtitle_continue ->
                    "已学${formatArgs[0]}个单词，还剩${formatArgs[1]}个单词即可完成任务，预计${formatArgs[2]}分钟"

                R.string.home_learn_subtitle_new_more ->
                    "今日已学${formatArgs[0]}个单词，可继续加量新学"

                else -> error("Unexpected string resource: $resId")
            }
        }
    }
}
