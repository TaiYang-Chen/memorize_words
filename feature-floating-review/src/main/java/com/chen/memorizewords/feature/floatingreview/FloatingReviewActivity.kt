package com.chen.memorizewords.feature.floatingreview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chen.memorizewords.core.navigation.OnboardingGuardDelegate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FloatingReviewActivity : AppCompatActivity() {

    @Inject
    lateinit var onboardingGuardDelegate: OnboardingGuardDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (onboardingGuardDelegate.guard(this)) return
        setContentView(R.layout.module_floating_review_activity_settings)
    }

    override fun onResume() {
        super.onResume()
        onboardingGuardDelegate.guard(this)
    }
}
