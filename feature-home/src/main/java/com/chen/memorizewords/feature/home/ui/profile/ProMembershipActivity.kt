package com.chen.memorizewords.feature.home.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.feature.home.databinding.FeatureHomeActivityProMembershipBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProMembershipActivity :
    BaseVmDbActivity<ProMembershipViewModel, FeatureHomeActivityProMembershipBinding>() {

    override val viewModel: ProMembershipViewModel by lazy {
        ViewModelProvider(this)[ProMembershipViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.featureHomeBtnMemberBack.setOnClickListener { finish() }
        databind.featureHomeTvMemberBenefitBooks.text =
            "- ${getString(R.string.feature_home_profile_member_benefit_books)}"
        databind.featureHomeTvMemberBenefitStats.text =
            "- ${getString(R.string.feature_home_profile_member_benefit_stats)}"
        databind.featureHomeTvMemberBenefitPractice.text =
            "- ${getString(R.string.feature_home_profile_member_benefit_practice)}"
        databind.featureHomeTvMemberBenefitSync.text =
            "- ${getString(R.string.feature_home_profile_member_benefit_sync)}"
        databind.featureHomeBtnOpenMember.setOnClickListener {
            viewModel.checkIn()
        }
    }

    override fun createObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    databind.featureHomeTvMemberStatus.text = state.title
                    databind.featureHomeTvMemberStatusSubtitle.text = state.subtitle
                    databind.featureHomeTvMemberNote.text = state.note
                    databind.featureHomeBtnOpenMember.text = state.buttonText
                    databind.featureHomeBtnOpenMember.isEnabled = state.checkInEnabled
                    databind.featureHomeBtnOpenMember.alpha = if (state.checkInEnabled) 1f else 0.55f
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ProMembershipActivity::class.java)
        }
    }
}
