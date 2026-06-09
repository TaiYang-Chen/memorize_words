package com.chen.memorizewords.feature.home.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.feature.home.databinding.FeatureHomeActivityProMembershipBinding

class ProMembershipActivity :
    BaseVmDbActivity<BaseViewModel, FeatureHomeActivityProMembershipBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
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
            viewModel.showSingleConfirmDialog(
                title = getString(R.string.feature_home_profile_member_payment_pending_title),
                message = getString(R.string.feature_home_profile_member_payment_pending_message)
            )
        }
    }

    override fun createObserver() = Unit

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ProMembershipActivity::class.java)
        }
    }
}
