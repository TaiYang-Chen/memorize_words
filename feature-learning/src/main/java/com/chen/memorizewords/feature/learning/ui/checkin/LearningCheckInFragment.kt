package com.chen.memorizewords.feature.learning.ui.checkin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentLearningCheckInBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LearningCheckInFragment :
    BaseFragment<LearningCheckInViewModel, FragmentLearningCheckInBinding>() {

    override val viewModel: LearningCheckInViewModel by lazy {
        ViewModelProvider(this)[LearningCheckInViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.btnShareCheckIn.setOnClickListener { viewModel.onShareClicked() }
        databind.btnCheckInBackHome.setOnClickListener { viewModel.onBackHomeClicked() }
        viewModel.initialize()
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { ui ->
                        databind.progressContainer.visibility =
                            if (ui.isLoading) View.VISIBLE else View.GONE
                        databind.contentContainer.visibility =
                            if (ui.showContent) View.VISIBLE else View.GONE

                        databind.tvCheckInTitle.text = ui.title
                        databind.tvCheckInSubtitle.text = ui.subtitle
                        databind.tvCheckInDate.text = ui.dateText
                        databind.tvCheckInStreakValue.text = ui.streakValueText
                        databind.tvCheckInDurationValue.text = ui.totalDurationText
                        databind.tvCheckInWordsValue.text = ui.totalWordsText
                    }
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        val target = event.target as? LearningCheckInViewModel.Route.Share ?: return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, target.content)
                },
                getString(R.string.learning_check_in_share_chooser)
            )
        )
    }
}
