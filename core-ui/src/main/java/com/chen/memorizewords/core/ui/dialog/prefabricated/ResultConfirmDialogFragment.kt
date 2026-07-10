package com.chen.memorizewords.core.ui.dialog.prefabricated

import android.graphics.Color
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.databinding.DialogWarnBinding
import com.chen.memorizewords.core.ui.dialog.BaseDialogFragment
import com.chen.memorizewords.core.ui.vm.BaseViewModel

class ResultConfirmDialogFragment :
    BaseDialogFragment<BaseViewModel, DialogWarnBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = requireArguments().getBoolean(ARG_CANCELABLE)
    }

    override fun initView(savedInstanceState: Bundle?) {
        val args = requireArguments()
        databind.title.text = args.getString(ARG_TITLE).orEmpty()
        databind.message.text = args.getString(ARG_MESSAGE).orEmpty()
        databind.confirm.text = args.getString(ARG_CONFIRM_TEXT).orEmpty()
        databind.cancel.text = args.getString(ARG_CANCEL_TEXT).orEmpty()
        databind.cancel.setTextColor(Color.parseColor("#6B7280"))
        databind.confirm.setTextColor(Color.parseColor("#6B7280"))
        databind.confirm.setOnClickListener {
            publishResult(confirmed = true)
            dismiss()
        }
        databind.cancel.setOnClickListener {
            publishResult(confirmed = false)
            dismiss()
        }
    }

    private fun publishResult(confirmed: Boolean) {
        val args = requireArguments()
        parentFragmentManager.setFragmentResult(
            args.getString(ARG_RESULT_KEY).orEmpty(),
            Bundle().apply {
                putLong(RESULT_REQUEST_ID, args.getLong(ARG_REQUEST_ID))
                putBoolean(RESULT_CONFIRMED, confirmed)
            }
        )
    }

    companion object {
        const val RESULT_REQUEST_ID = "request_id"
        const val RESULT_CONFIRMED = "confirmed"

        private const val ARG_RESULT_KEY = "result_key"
        private const val ARG_REQUEST_ID = "request_id"
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_CONFIRM_TEXT = "confirm_text"
        private const val ARG_CANCEL_TEXT = "cancel_text"
        private const val ARG_CANCELABLE = "cancelable"

        fun newInstance(
            resultKey: String,
            requestId: Long,
            title: String,
            message: String,
            confirmText: String = "\u786e\u5b9a",
            cancelText: String = "\u53d6\u6d88",
            cancelable: Boolean = true
        ): ResultConfirmDialogFragment {
            return ResultConfirmDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RESULT_KEY, resultKey)
                    putLong(ARG_REQUEST_ID, requestId)
                    putString(ARG_TITLE, title)
                    putString(ARG_MESSAGE, message)
                    putString(ARG_CONFIRM_TEXT, confirmText)
                    putString(ARG_CANCEL_TEXT, cancelText)
                    putBoolean(ARG_CANCELABLE, cancelable)
                }
            }
        }
    }
}
