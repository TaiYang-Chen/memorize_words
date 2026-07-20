package com.chen.memorizewords.core.ui.dialog.prefabricated

import android.content.DialogInterface
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.bottomsheetdialogfragment.BaseBottomSheetDialogFragment
import com.chen.memorizewords.core.ui.databinding.BottomDialogWarnBinding
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent

class ShowConfirmBottomDialog() :
    BaseBottomSheetDialogFragment<BaseViewModel, BottomDialogWarnBinding>() {

    private var resultSent = false

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        val args = requireArguments()
        databind.title.text = args.getString(ARG_TITLE).orEmpty()
        databind.message.text = args.getString(ARG_MESSAGE).orEmpty()
        databind.confirm.text = args.getString(ARG_CONFIRM_TEXT).orEmpty()
        databind.cancel.text = args.getString(ARG_CANCEL_TEXT).orEmpty()
        databind.confirm.setOnClickListener {
            sendResult(confirmed = true)
            dismiss()
        }
        databind.cancel.setOnClickListener {
            sendResult(confirmed = false)
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        sendResult(confirmed = false)
        super.onCancel(dialog)
    }

    private fun sendResult(confirmed: Boolean) {
        if (resultSent) return
        resultSent = true
        val resultKey = arguments?.getString(ARG_RESULT_KEY)?.takeIf(String::isNotBlank) ?: return
        parentFragmentManager.setFragmentResult(
            resultKey,
            bundleOf(
                RESULT_CONFIRMED to confirmed,
                ARG_ACTION to arguments?.getString(ARG_ACTION),
                ARG_TITLE to arguments?.getString(ARG_TITLE).orEmpty(),
                ARG_MESSAGE to arguments?.getString(ARG_MESSAGE).orEmpty(),
                ARG_CONFIRM_TEXT to arguments?.getString(ARG_CONFIRM_TEXT).orEmpty(),
                ARG_CANCEL_TEXT to arguments?.getString(ARG_CANCEL_TEXT).orEmpty()
            )
        )
    }

    companion object {
        const val RESULT_CONFIRMED = "confirm_bottom_result_confirmed"

        fun newInstance(
            data: UiEvent.Dialog.ConfirmBottom,
            resultKey: String
        ): ShowConfirmBottomDialog = ShowConfirmBottomDialog().apply {
            arguments = createArguments(data, resultKey)
        }

        fun eventFromResult(result: Bundle): UiEvent.Dialog.ConfirmBottom =
            UiEvent.Dialog.ConfirmBottom(
                action = result.getString(ARG_ACTION),
                title = result.getString(ARG_TITLE).orEmpty(),
                message = result.getString(ARG_MESSAGE).orEmpty(),
                confirmText = result.getString(ARG_CONFIRM_TEXT).orEmpty(),
                cancelText = result.getString(ARG_CANCEL_TEXT).orEmpty()
            )

        private fun createArguments(
            data: UiEvent.Dialog.ConfirmBottom,
            resultKey: String?
        ) = bundleOf(
            ARG_ACTION to data.action,
            ARG_TITLE to data.title,
            ARG_MESSAGE to data.message,
            ARG_CONFIRM_TEXT to data.confirmText,
            ARG_CANCEL_TEXT to data.cancelText,
            ARG_RESULT_KEY to resultKey
        )

        private const val ARG_TITLE = "confirm_bottom_title"
        private const val ARG_ACTION = "confirm_bottom_action"
        private const val ARG_MESSAGE = "confirm_bottom_message"
        private const val ARG_CONFIRM_TEXT = "confirm_bottom_confirm_text"
        private const val ARG_CANCEL_TEXT = "confirm_bottom_cancel_text"
        private const val ARG_RESULT_KEY = "confirm_bottom_result_key"
    }
}
