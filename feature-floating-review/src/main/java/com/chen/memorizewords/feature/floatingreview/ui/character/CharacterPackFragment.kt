package com.chen.memorizewords.feature.floatingreview.ui.character

import android.app.ForegroundServiceStartNotAllowedException
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.core.navigation.CharacterSelectionMode
import com.chen.memorizewords.core.ui.dialog.prefabricated.ShowConfirmBottomDialog
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.floatingreview.FloatingReviewActivity
import com.chen.memorizewords.feature.floatingreview.R
import com.chen.memorizewords.feature.floatingreview.databinding.ModuleFloatingReviewFragmentCharacterPacksBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CharacterPackFragment :
    BaseVmDbFragment<CharacterPackViewModel, ModuleFloatingReviewFragmentCharacterPacksBinding>() {

    override val viewModel: CharacterPackViewModel by viewModels()

    @Inject
    lateinit var floatingWordEntry: FloatingWordEntry

    private lateinit var adapter: CharacterPackAdapter

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onOverlayPermissionResult(Settings.canDrawOverlays(requireContext()))
    }

    override fun setLayout(): Int = R.layout.module_floating_review_fragment_character_packs

    override fun initView(savedInstanceState: Bundle?) {
        adapter = CharacterPackAdapter(
            activationMode = viewModel.mode == CharacterSelectionMode.ACTIVATE,
            onPrimary = viewModel::onPrimary,
            onCancel = viewModel::onCancel,
            onDelete = viewModel::onDelete
        )
        databind.rvCharacterPacks.layoutManager = LinearLayoutManager(requireContext())
        databind.rvCharacterPacks.adapter = adapter
        databind.btnCharacterBack.setOnClickListener {
            if (viewModel.mode == CharacterSelectionMode.ACTIVATE) {
                viewModel.cancelActivationAndExit()
            } else {
                exitPage()
            }
        }
        if (viewModel.mode == CharacterSelectionMode.ACTIVATE) {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                viewModel.cancelActivationAndExit()
            }
        }
        parentFragmentManager.setFragmentResultListener(
            OVERLAY_PERMISSION_RESULT,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getBoolean(ShowConfirmBottomDialog.RESULT_CONFIRMED)) {
                launchOverlayPermissionSettings()
            } else {
                viewModel.onOverlayPermissionDenied()
            }
        }
        parentFragmentManager.setFragmentResultListener(
            DELETE_CHARACTER_RESULT,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getBoolean(ShowConfirmBottomDialog.RESULT_CONFIRMED)) {
                viewModel.confirmPendingDelete()
            } else {
                viewModel.cancelPendingDelete()
            }
        }
        databind.btnCharacterRefresh.setOnClickListener { viewModel.refresh() }
        if (viewModel.mode == CharacterSelectionMode.ACTIVATE) {
            databind.tvCharacterTitle.setText(R.string.module_floating_review_character_activate_title)
            databind.tvCharacterSubtitle.setText(
                R.string.module_floating_review_character_activate_subtitle
            )
        }
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { items ->
                        adapter.submitItems(items)
                        databind.tvCharacterEmpty.visibility =
                            if (items.isEmpty()) View.VISIBLE else View.GONE
                        val completedDownload = viewModel.claimSelectedPackReloadRequest(items)
                        if (completedDownload != null) {
                            if (!applyCharacterPackIfRunning(completedDownload)) {
                                viewModel.releasePackReloadRequestClaim(completedDownload)
                            }
                        }
                    }
                }
                launch {
                    viewModel.readyActivationRequestId.collect { requestId ->
                        if (requestId != null) {
                            viewModel.continueActivation(
                                canDrawOverlays = Settings.canDrawOverlays(requireContext()),
                                requestId = requestId
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            CharacterPackViewModel.Route.ApplyCharacterPack -> applyCharacterPackIfRunning()
            CharacterPackViewModel.Route.RequestOverlayPermission -> showOverlayPermissionDialog()
            is CharacterPackViewModel.Route.StartFloating -> {
                if (dispatchFloatingStart(target.activationRequestId)) {
                    requireActivity().finish()
                }
            }
            CharacterPackViewModel.Route.StopFloating -> {
                floatingWordEntry.dispatchServiceAction(
                    requireContext(),
                    FloatingWordActions.ACTION_STOP
                )
            }
            CharacterPackViewModel.Route.Exit -> exitPage()
            is CharacterPackViewModel.Route.ConfirmDelete -> showDeleteConfirmation()
        }
    }

    private fun applyCharacterPackIfRunning(
        completedDownload: CompletedCharacterPackDownload? = null
    ): Boolean {
        return floatingWordEntry.tryDispatchServiceAction(
            context = requireContext(),
            action = FloatingWordActions.ACTION_APPLY_CHARACTER_PACK,
            characterPackId = completedDownload?.packId,
            downloadRequestId = completedDownload?.requestId
        )
    }

    private fun showOverlayPermissionDialog() {
        if (parentFragmentManager.findFragmentByTag(TAG_OVERLAY_PERMISSION) != null) return
        ShowConfirmBottomDialog.newInstance(
            data = UiEvent.Dialog.ConfirmBottom(
                title = getString(R.string.module_floating_review_permission_title),
                message = getString(R.string.module_floating_review_permission_message),
                confirmText = getString(R.string.module_floating_review_permission_confirm),
                cancelText = getString(R.string.module_floating_review_permission_cancel)
            ),
            resultKey = OVERLAY_PERMISSION_RESULT
        ).show(parentFragmentManager, TAG_OVERLAY_PERMISSION)
    }

    private fun launchOverlayPermissionSettings() {
        overlayPermissionLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            )
        )
    }

    private fun dispatchFloatingStart(activationRequestId: String): Boolean {
        return try {
            floatingWordEntry.dispatchServiceAction(
                context = requireContext(),
                action = FloatingWordActions.ACTION_START,
                activationRequestId = activationRequestId
            )
            true
        } catch (failure: RuntimeException) {
            if (!isExpectedFloatingServiceStartFailure(failure)) throw failure
            viewModel.onForegroundServiceStartRejected(activationRequestId)
            showFloatingStartFailed()
            false
        }
    }

    private fun isExpectedFloatingServiceStartFailure(failure: RuntimeException): Boolean =
        failure is IllegalStateException ||
            failure is SecurityException ||
            (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    Android12ForegroundServiceStartFailure.isForegroundServiceStartNotAllowed(failure)
                )

    private object Android12ForegroundServiceStartFailure {
        fun isForegroundServiceStartNotAllowed(failure: RuntimeException): Boolean =
            failure is ForegroundServiceStartNotAllowedException
    }


    private fun showFloatingStartFailed() {
        Toast.makeText(
            requireContext(),
            R.string.module_floating_review_start_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun exitPage() {
        if (viewModel.mode == CharacterSelectionMode.ACTIVATE) {
            (activity as? FloatingReviewActivity)?.returnToOrigin() ?: requireActivity().finish()
            return
        }
        if (!findNavController().navigateUp()) requireActivity().finish()
    }

    private fun showDeleteConfirmation() {
        if (parentFragmentManager.findFragmentByTag(TAG_DELETE_CHARACTER) != null) return
        ShowConfirmBottomDialog.newInstance(
            data = UiEvent.Dialog.ConfirmBottom(
                title = getString(R.string.module_floating_review_character_delete_generic_title),
                message = getString(R.string.module_floating_review_character_delete_generic_message)
            ),
            resultKey = DELETE_CHARACTER_RESULT
        ).show(parentFragmentManager, TAG_DELETE_CHARACTER)
    }

    private companion object {
        const val TAG_OVERLAY_PERMISSION = "CharacterOverlayPermission"
        const val TAG_DELETE_CHARACTER = "DeleteActiveCharacter"
        const val OVERLAY_PERMISSION_RESULT = "CharacterOverlayPermissionResult"
        const val DELETE_CHARACTER_RESULT = "DeleteCharacterResult"
    }
}
