package com.chen.memorizewords.feature.home.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import coil.transform.CircleCropTransformation
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.home.databinding.ModuleHomeFragmentProfileBinding
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.navigation.FeedbackEntry
import com.chen.memorizewords.core.navigation.WordBookEntry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : BaseFragment<ProfileViewModel, ModuleHomeFragmentProfileBinding>() {

    override val viewModel: ProfileViewModel by lazy {
        ViewModelProvider(this)[ProfileViewModel::class.java]
    }

    @Inject
    lateinit var authEntry: AuthEntry

    @Inject
    lateinit var feedbackEntry: FeedbackEntry

    @Inject
    lateinit var wordBookEntry: WordBookEntry

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
    }

    override fun createObserver() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.user.collect { user ->
                    databind.ivAvatar.load(user?.avatarUrl) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.feature_home_ic_avatar_placeholder)
                        error(R.drawable.feature_home_ic_avatar_placeholder)
                        fallback(R.drawable.feature_home_ic_avatar_placeholder)
                    }
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        val target = event.target as? ProfileViewModel.Route.Open ?: return
        val intent = when (target.screen) {
            ProfileViewModel.Route.Screen.WORD_BOOK ->
                wordBookEntry.createWordBookIntent(requireContext())
            ProfileViewModel.Route.Screen.FEEDBACK ->
                feedbackEntry.createFeedbackIntent(requireContext())
            ProfileViewModel.Route.Screen.AUTH ->
                authEntry.createAuthIntent(requireContext())
        }

        startActivity(intent.apply {
            target.deepLink?.let { uri ->
                action = Intent.ACTION_VIEW
                data = uri.toUri()
            }
            if (target.clearTask) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        })
    }

    override fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action == ProfileViewModel.ACTION_FORCE_LOGOUT) {
            viewModel.onForceLogoutConfirmed()
            return
        }
        super.onConfirmDialog(event)
    }
}
