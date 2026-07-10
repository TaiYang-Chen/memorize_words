package com.chen.memorizewords.feature.home.ui.profile

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import coil.transform.CircleCropTransformation
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.navigation.RouteNavigator
import com.chen.memorizewords.core.session.logout.SessionLogoutCoordinator
import com.chen.memorizewords.core.ui.session.logout.SessionLogoutHost
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.model.user.avatarLoadSource
import com.chen.memorizewords.domain.account.model.user.hasReadableLocalAvatar
import com.chen.memorizewords.feature.home.databinding.ModuleHomeFragmentProfileBinding
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.navigation.FeedbackEntry
import com.chen.memorizewords.core.navigation.WordBookEntry
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    @Inject
    lateinit var routeNavigator: RouteNavigator

    @Inject
    lateinit var logoutCoordinator: SessionLogoutCoordinator

    private lateinit var logoutHost: SessionLogoutHost

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        logoutHost = SessionLogoutHost(
            fragment = this,
            coordinator = logoutCoordinator,
            configuration = SessionLogoutHost.Configuration(
                loadingMessage = getString(R.string.home_logout_loading),
                riskTitle = getString(R.string.home_logout_risk_title),
                riskMessage = getString(R.string.home_logout_risk_message),
                onCompleted = { terminal ->
                    viewModel.resolveLogoutCompletionMessage(terminal)?.let(::showLogoutMessage)
                },
                onFailed = { failure ->
                    showLogoutMessage(viewModel.resolveLogoutFailureMessage(failure))
                },
                navigateToAuth = ::navigateToAuthAfterLogout
            )
        )
        logoutHost.bind()
    }

    override fun createObserver() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.user.collect { user ->
                    databind.ivAvatar.load(user.avatarLoadSource()) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.feature_home_ic_avatar_placeholder)
                        error(R.drawable.feature_home_ic_avatar_placeholder)
                        fallback(R.drawable.feature_home_ic_avatar_placeholder)
                        listener(
                            onSuccess = { _, result ->
                                cacheRemoteAvatarIfNeeded(user, result.drawable)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun cacheRemoteAvatarIfNeeded(user: User?, drawable: android.graphics.drawable.Drawable) {
        val avatarUrl = user?.avatarUrl?.trim()?.takeIf { it.isNotBlank() } ?: return
        if (user.hasReadableLocalAvatar()) return
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.Default) {
                drawable.toBitmap().toJpegBytes()
            }
            viewModel.cacheLoadedAvatar(bytes, avatarUrl)
        }
    }

    private fun Bitmap.toJpegBytes(): ByteArray {
        return ByteArrayOutputStream().use { output ->
            compress(Bitmap.CompressFormat.JPEG, 95, output)
            output.toByteArray()
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is AppRoute.Feedback -> {
                startActivity(
                    feedbackEntry.createFeedbackIntent(requireContext(), target.deepLink)
                )
            }
            is AppRoute.WordBook -> {
                startActivity(
                    wordBookEntry.createWordBookIntent(requireContext(), target.deepLink)
                )
            }
            is AppRoute.Auth -> {
                startActivity(
                    authEntry.createAuthIntent(requireContext()).apply {
                        target.deepLink?.let { deepLink ->
                            action = Intent.ACTION_VIEW
                            data = deepLink.toUri()
                        }
                    }
                )
            }
            is AppRoute -> routeNavigator.navigate(target)
            ProfileViewModel.Route.OpenMembership -> {
                startActivity(ProMembershipActivity.createIntent(requireContext()))
            }
            ProfileViewModel.Route.OpenPersonalQr -> {
                startActivity(PersonalQrActivity.createIntent(requireContext()))
            }
            else -> super.onNavigationRoute(event)
        }
    }

    override fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action == ProfileViewModel.ACTION_LOGOUT_CONFIRM) {
            viewModel.onLogoutConfirmed()
            return
        }
        super.onConfirmDialog(event)
    }

    private fun navigateToAuthAfterLogout() {
        startActivity(
            authEntry.createAuthIntent(requireContext()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
    }

    private fun showLogoutMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
