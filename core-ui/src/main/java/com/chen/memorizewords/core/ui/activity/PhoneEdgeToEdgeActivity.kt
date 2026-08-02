package com.chen.memorizewords.core.ui.activity

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import com.chen.memorizewords.core.ui.insets.applyPhoneWindowInsets
import com.chen.memorizewords.core.ui.insets.configurePhoneWindowForInsets

/**
 * Shared phone activity host for edge-to-edge content.
 *
 * Window configuration happens before content is installed, while every content root receives one
 * explicit inset policy. Screens with a dedicated bottom bar can override the policy instead of
 * adding their own window listeners.
 */
abstract class PhoneEdgeToEdgeActivity : AppCompatActivity() {

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configurePhoneWindowForInsets()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyInsetsToContentRoot()
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        applyInsetsToContentRoot()
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        super.setContentView(view, params)
        applyInsetsToContentRoot()
    }

    /**
     * Override when individual descendants, such as a fixed bottom navigation bar, own an edge.
     */
    protected open fun applyContentWindowInsets(content: View) {
        content.applyPhoneWindowInsets()
    }

    private fun applyInsetsToContentRoot() {
        val contentContainer = findViewById<ViewGroup>(android.R.id.content) ?: return
        val contentRoot = if (contentContainer.childCount == 1) {
            contentContainer.getChildAt(0)
        } else {
            contentContainer
        }
        applyContentWindowInsets(contentRoot)
    }
}
