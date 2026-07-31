package com.chen.memorizewords.feature.floatingreview.ui.character

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.navigation.CharacterSelectionMode
import com.chen.memorizewords.core.ui.dialog.prefabricated.ShowConfirmBottomDialog
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.floatingreview.FloatingReviewActivity
import com.chen.memorizewords.feature.floatingreview.R
import com.chen.memorizewords.feature.floatingreview.databinding.ModuleFloatingReviewFragmentCharacterPacksBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CharacterPackFragment :
    BaseVmDbFragment<CharacterPackViewModel, ModuleFloatingReviewFragmentCharacterPacksBinding>() {

    override val viewModel: CharacterPackViewModel by viewModels()

    private lateinit var adapter: CharacterPackAdapter

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
                    }
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (event.target) {
            CharacterPackViewModel.Route.Exit -> exitPage()
            is CharacterPackViewModel.Route.ConfirmDelete -> showDeleteConfirmation()
        }
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
        const val TAG_DELETE_CHARACTER = "DeleteActiveCharacter"
        const val DELETE_CHARACTER_RESULT = "DeleteCharacterResult"
    }
}
