package com.chen.memorizewords.startup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptSafeActivityClassifierTest {

    private val classifier = PromptSafeActivityClassifier()

    @Test
    fun `splash is not prompt safe`() {
        assertFalse(
            classifier.isPromptSafe(
                activityClassName = "com.chen.memorizewords.SplashActivity",
                visibleFragmentClassName = null,
                isSplashActivity = true
            )
        )
    }

    @Test
    fun `learning and practice surfaces are not prompt safe`() {
        assertFalse(
            classifier.isPromptSafe(
                activityClassName = "com.chen.memorizewords.feature.learning.LearningActivity",
                visibleFragmentClassName = null,
                isSplashActivity = false
            )
        )
        assertFalse(
            classifier.isPromptSafe(
                activityClassName = "com.chen.memorizewords.feature.home.HomeActivity",
                visibleFragmentClassName = "com.chen.memorizewords.feature.home.ui.practice.PracticeFragment",
                isSplashActivity = false
            )
        )
    }

    @Test
    fun `home surface is prompt safe`() {
        assertTrue(
            classifier.isPromptSafe(
                activityClassName = "com.chen.memorizewords.feature.home.HomeActivity",
                visibleFragmentClassName = "com.chen.memorizewords.feature.home.ui.TodayFragment",
                isSplashActivity = false
            )
        )
    }
}
