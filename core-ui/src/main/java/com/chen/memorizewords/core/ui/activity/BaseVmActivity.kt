package com.chen.memorizewords.core.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

abstract class BaseVmActivity<VM : BaseViewModel> : AppCompatActivity() {

    protected abstract val viewModel: VM

    private val loadingDialogController by lazy {
        LoadingDialogController(supportFragmentManager)
    }

    abstract fun createObserver()

    abstract fun layoutId(): Int

    abstract fun initView(savedInstanceState: Bundle?)

    open fun initDataBind(): View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = initDataBind()
        if (view != null) {
            setContentView(view)
        } else {
            setContentView(layoutId())
        }
        createObserver()
        initView(savedInstanceState)
        observeBaseUi()
    }

    private fun observeBaseUi() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiEvent.collect { event ->
                        if (consumeUiEvent(event)) return@collect
                        when (event) {
                            is UiEvent.Navigation.Finish -> finish()
                            is UiEvent.Navigation.Back -> onBackPressedDispatcher.onBackPressed()
                            is UiEvent.Navigation.Route -> onNavigationRoute(event)
                            is UiEvent.Dialog.ConfirmEdit -> {
                                ShowConfirmEditDialog(
                                    data = event,
                                    onConfirm = { value -> onConfirmEditDialog(event, value) },
                                    onCancel = { onCancelEditDialog(event) }
                                )
                                    .show(supportFragmentManager, "ShowConfirmEditDialog")
                            }
                            is UiEvent.Dialog.Confirm -> {
                                ShowConfirmDialog(
                                    data = event,
                                    onConfirm = { onConfirmDialog(event) },
                                    onCancel = { onCancelDialog(event) }
                                ).show(supportFragmentManager, "ShowConfirmDialog")
                            }
                            is UiEvent.Dialog.ConfirmBottom -> {
                                ShowConfirmBottomDialog(
                                    data = event,
                                    onConfirm = { onConfirmBottomDialog(event) },
                                    onCancel = { onCancelBottomDialog(event) }
                                ).show(
                                    supportFragmentManager,
                                    "ShowConfirmBottomDialog"
                                )
                            }
                            is UiEvent.Dialog.CustomConfirmDialog -> customConfirmDialog(event)
                            is UiEvent.Toast -> {
                                Toast.makeText(this@BaseVmActivity, event.message, Toast.LENGTH_SHORT)
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

    override fun onDestroy() {
        loadingDialogController.hide()
        super.onDestroy()
    }
}
