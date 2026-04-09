package com.chen.memorizewords.feature.feedback.ui.about

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.feedback.R
import com.chen.memorizewords.feature.feedback.databinding.ModuleFeedbackFragmentAboutBinding
import com.chen.memorizewords.feature.feedback.ui.util.AppInfoProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AboutFragment : BaseFragment<AboutViewModel, ModuleFeedbackFragmentAboutBinding>() {

    override val viewModel: AboutViewModel by lazy {
        ViewModelProvider(this)[AboutViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.btnBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        databind.tvAppName.text = getString(R.string.module_feedback_app_name)
        databind.tvVersion.text = getString(
            R.string.module_feedback_version_label,
            AppInfoProvider.getVersionName(requireContext())
        )
        databind.rowCheckUpdate.setOnClickListener {
            viewModel.onCheckUpdateClicked()
        }
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.missionText.collect { text ->
                        databind.tvMission.text = text
                    }
                }
                launch {
                    viewModel.updateStatusText.collect { text ->
                        databind.tvCheckUpdateStatus.text = text
                    }
                }
            }
        }
    }
}
