package com.chen.memorizewords.core.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.dialog.loading.LoadingDialogController
import com.chen.memorizewords.core.ui.dialog.prefabricated.ShowConfirmBottomDialog
import com.chen.memorizewords.core.ui.dialog.prefabricated.ShowConfirmDialog
import com.chen.memorizewords.core.ui.dialog.prefabricated.ShowConfirmEditDialog
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.core.ui.vm.UiEvent
import kotlinx.coroutines.launch

abstract class BaseVmDialogFragment<VM : BaseViewModel> : DialogFragment() {

    protected abstract val viewModel: VM

    private val loadingDialogController by lazy {
        LoadingDialogController(parentFragmentManager)
    }

    abstract fun setLayout(): Int

    abstract fun initView(savedInstanceState: Bundle?)

    abstract fun createObserver()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(setLayout(), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView(savedInstanceState)
        createObserver()
        initData()
        observeBaseUi()
    }

    private fun observeBaseUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiEvent.collect { event ->
                        if (consumeUiEvent(event)) return@collect
                        when (event) {
                            is UiEvent.Navigation.Finish,
                            is UiEvent.Navigation.Back -> dismissAllowingStateLoss()
                            is UiEvent.Navigation.Route -> onNavigationRoute(event)
                            is UiEvent.Dialog.ConfirmEdit -> {
                                ShowConfirmEditDialog(
                                    data = event,
                                    onConfirm = { value -> onConfirmEditDialog(event, value) },
                                    onCancel = { onCancelEditDialog(event) }
                                )
                                    .show(parentFragmentManager, "ShowConfirmEditDialog")
                            }
                            is UiEvent.Dialog.Confirm -> {
                                ShowConfirmDialog(
                                    data = event,
                                    onConfirm = { onConfirmDialog(event) },
                                    onCancel = { onCancelDialog(event) }
                                ).show(parentFragmentManager, "ShowConfirmDialog")
                            }
                            is UiEvent.Dialog.ConfirmBottom -> {
                                ShowConfirmBottomDialog(
                                    data = event,
                                    onConfirm = { onConfirmBottomDialog(event) },
                                    onCancel = { onCancelBottomDialog(event) }
                                ).show(
                                    parentFragmentManager,
                                    "ShowConfirmBottomDialog"
                                )
                            }
                            is UiEvent.Dialog.CustomConfirmDialog -> customConfirmDialog(event)
                            is UiEvent.Toast -> {
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT)
                                    .show()
                            }
                            is UiEffect -> onUiEffect(event)
                        }
                    }
                }
                launch {
                    viewModel.loadingState.collect { state ->
                        if (state.isLoading) {
                            loadingDialogController.show(state.message)
                        } else {
                            loadingDialogController.hide()
                        }
                    }
                }
            }
        }
    }

    open fun consumeUiEvent(event: UiEvent): Boolean = false

    open fun onNavigationRoute(event: UiEvent.Navigation.Route) {
    }

    open fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
    }

    open fun onCancelDialog(event: UiEvent.Dialog.Confirm) {
    }

    open fun onConfirmEditDialog(event: UiEvent.Dialog.ConfirmEdit, value: String) {
    }

    open fun onCancelEditDialog(event: UiEvent.Dialog.ConfirmEdit) {
    }

    open fun onConfirmBottomDialog(event: UiEvent.Dialog.ConfirmBottom) {
    }

    open fun onCancelBottomDialog(event: UiEvent.Dialog.ConfirmBottom) {
    }

    open fun customConfirmDialog(event: UiEvent.Dialog.CustomConfirmDialog) {
    }

    open fun onUiEffect(effect: UiEffect) {
    }

    override fun onDestroyView() {
        loadingDialogController.hide()
        super.onDestroyView()
    }

    open fun initData() {}
}
