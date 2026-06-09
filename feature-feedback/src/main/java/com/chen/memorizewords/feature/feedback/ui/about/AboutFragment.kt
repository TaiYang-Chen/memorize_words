package com.chen.memorizewords.feature.feedback.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
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
        databind.viewModel = viewModel
        databind.tvVersion.text = getString(
            R.string.module_feedback_version_label,
            AppInfoProvider.getVersionName(requireContext())
        )
        databind.rowCheckUpdate.setOnClickListener {
            viewModel.onCheckUpdateClicked()
        }
        databind.rowRateUs.setOnClickListener {
            viewModel.onRateUsClicked(requireContext().packageName)
        }
        databind.rowOfficialWebsite.setOnClickListener {
            viewModel.onOfficialWebsiteClicked()
        }
        databind.rowTerms.setOnClickListener {
            viewModel.onTermsClicked()
        }
        databind.rowPrivacy.setOnClickListener {
            viewModel.onPrivacyClicked()
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

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is AboutViewModel.Route.OpenReleasePage -> openUrl(target.url)
            is AboutViewModel.Route.OpenUrl -> openUrl(target.url)
            is AboutViewModel.Route.OpenAppMarket -> openAppMarket(target.packageName)
            else -> super.onNavigationRoute(event)
        }
    }

    private fun openAppMarket(packageName: String) {
        val safePackage = packageName.trim()
        if (safePackage.isBlank()) {
            viewModel.showToast(getString(R.string.feature_feedback_about_link_unavailable))
            return
        }

        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$safePackage".toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }

        try {
            startActivity(marketIntent)
        } catch (error: ActivityNotFoundException) {
            openUrl("https://play.google.com/store/apps/details?id=$safePackage")
        } catch (error: SecurityException) {
            openUrl("https://play.google.com/store/apps/details?id=$safePackage")
        }
    }

    private fun openUrl(url: String) {
        val safeUrl = url.trim()
        if (!safeUrl.startsWith("https://", ignoreCase = true) &&
            !safeUrl.startsWith("http://", ignoreCase = true)
        ) {
            viewModel.showToast(getString(R.string.feature_feedback_about_link_unavailable))
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, safeUrl.toUri())
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            viewModel.showToast(getString(R.string.feature_feedback_about_no_handler))
        } catch (error: SecurityException) {
            viewModel.showToast(getString(R.string.feature_feedback_about_no_handler))
        }
    }
}
