package com.chen.memorizewords.feature.feedback.ui.util

import android.graphics.Typeface
import android.view.LayoutInflater
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.chen.memorizewords.feature.feedback.R
import com.google.android.material.tabs.TabLayout

private data class FeedbackTabSpec(
    val titleRes: Int,
    @IdRes val destinationId: Int
)

private val feedbackTabs = listOf(
    FeedbackTabSpec(
        titleRes = R.string.module_feedback_nav_feedback_title,
        destinationId = R.id.feedbackFragment
    ),
    FeedbackTabSpec(
        titleRes = R.string.module_feedback_nav_about_title,
        destinationId = R.id.aboutFragment
    )
)

fun Fragment.setupFeedbackTabs(tabLayout: TabLayout, @IdRes currentDestinationId: Int) {
    tabLayout.removeAllTabs()
    tabLayout.clearOnTabSelectedListeners()

    feedbackTabs.forEach { tabSpec ->
        val isSelected = tabSpec.destinationId == currentDestinationId
        val tab = tabLayout.newTab()
            .setTag(tabSpec.destinationId)
            .setCustomView(createFeedbackTabView(tabLayout, getString(tabSpec.titleRes)))
        tabLayout.addTab(tab, isSelected)
        updateFeedbackTabStyle(tab, isSelected)
    }

    tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            updateFeedbackTabStyle(tab, true)
            val targetId = tab.tag as? Int ?: return
            val navController = findNavController()
            if (navController.currentDestination?.id == targetId) return
            navController.navigate(
                targetId,
                null,
                navOptions {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                }
            )
        }

        override fun onTabUnselected(tab: TabLayout.Tab) {
            updateFeedbackTabStyle(tab, false)
        }

        override fun onTabReselected(tab: TabLayout.Tab) = Unit
    })
}

private fun Fragment.createFeedbackTabView(tabLayout: TabLayout, title: String): TextView {
    val tabView = LayoutInflater.from(tabLayout.context)
        .inflate(R.layout.module_feedback_item_tab_text, tabLayout, false) as TextView
    tabView.text = title
    return tabView
}

private fun Fragment.updateFeedbackTabStyle(tab: TabLayout.Tab, isSelected: Boolean) {
    (tab.customView as? TextView)?.apply {
        setTextColor(
            requireContext().getColor(
                if (isSelected) {
                    R.color.module_feedback_tab_text_selected
                } else {
                    R.color.module_feedback_tab_text_unselected
                }
            )
        )
        typeface = Typeface.create(
            if (isSelected) "sans-serif-medium" else "sans-serif",
            Typeface.NORMAL
        )
    }
}
