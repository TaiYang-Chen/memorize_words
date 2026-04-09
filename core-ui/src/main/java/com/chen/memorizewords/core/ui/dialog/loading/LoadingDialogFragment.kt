package com.chen.memorizewords.core.ui.dialog.loading

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.chen.memorizewords.core.ui.R
import com.chen.memorizewords.core.ui.vm.BaseViewModel

class LoadingDialogFragment : DialogFragment() {

    private var messageText: String = BaseViewModel.DEFAULT_LOADING_MESSAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        messageText = arguments?.getString(ARG_MESSAGE)
            ?: BaseViewModel.DEFAULT_LOADING_MESSAGE
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(false)
            setOnKeyListener { _, keyCode, event ->
                keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_loading, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindMessage(view)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun updateMessage(message: String) {
        messageText = message
        view?.findViewById<TextView>(R.id.tvMessage)?.text = messageText
    }

    private fun bindMessage(rootView: View) {
        rootView.findViewById<TextView>(R.id.tvMessage).text = messageText
    }

    companion object {
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(message: String): LoadingDialogFragment {
            return LoadingDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MESSAGE, message)
                }
            }
        }
    }
}
