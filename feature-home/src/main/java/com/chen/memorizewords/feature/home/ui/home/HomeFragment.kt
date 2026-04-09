package com.chen.memorizewords.feature.home.ui.home

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.model.sync.SyncBannerState
import com.chen.memorizewords.feature.home.databinding.FeatureHomeModuleHomeFragmentHomeBinding
import com.chen.memorizewords.core.navigation.LearningEntry
import com.chen.memorizewords.core.navigation.WordBookEntry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : BaseFragment<HomeViewModel, FeatureHomeModuleHomeFragmentHomeBinding>() {

    override val viewModel: HomeViewModel by lazy {
        ViewModelProvider(this)[HomeViewModel::class.java]
    }

    @Inject
    lateinit var learningEntry: LearningEntry

    @Inject
    lateinit var wordBookEntry: WordBookEntry

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.syncBannerState.collect { state ->
                        databind.syncBannerContainer.visibility =
                            if (state == SyncBannerState.Hidden) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.syncBannerText.collect { text ->
                        databind.tvSyncBanner.text = text
                        databind.tvSyncBanner.isSelected = text.isNotBlank()
                    }
                }
            }
        }
    }

    override fun customConfirmDialog(event: UiEvent.Dialog.CustomConfirmDialog) {
        if (event.custom != HomeViewModel.CUSTOM_DIALOG_HOME_BOOST_NEW_WORDS) {
            super.customConfirmDialog(event)
            return
        }

        val requestKey = BoostNewWordsDialogFragment.REQUEST_KEY
        parentFragmentManager.clearFragmentResultListener(requestKey)
        parentFragmentManager.setFragmentResultListener(
            requestKey,
            viewLifecycleOwner
        ) { _, bundle ->
            val selectedAmount = bundle.getInt(
                BoostNewWordsDialogFragment.RESULT_KEY_AMOUNT,
                HomeViewModel.DEFAULT_BOOST_NEW_WORDS
            )
            parentFragmentManager.clearFragmentResultListener(requestKey)
            viewModel.onBoostNewWordsSelected(selectedAmount)
        }

        if (parentFragmentManager.findFragmentByTag(BoostNewWordsDialogFragment.TAG) == null) {
            BoostNewWordsDialogFragment
                .newInstance(HomeViewModel.DEFAULT_BOOST_NEW_WORDS)
                .show(parentFragmentManager, BoostNewWordsDialogFragment.TAG)
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            HomeViewModel.Route.ToWordBook -> {
                startActivity(wordBookEntry.createWordBookIntent(requireContext()))
            }

            is HomeViewModel.Route.ToLearning -> {
                startActivity(
                    learningEntry.createLearningIntent(requireContext(), target.request)
                )
            }
        }
    }
}
