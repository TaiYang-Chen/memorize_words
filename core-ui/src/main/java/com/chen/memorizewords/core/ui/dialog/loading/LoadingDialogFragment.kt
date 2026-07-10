package com.chen.memorizewords.core.ui.dialog.loading

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import com.chen.memorizewords.core.ui.R
import com.chen.memorizewords.core.ui.vm.BaseViewModel

class LoadingDialogFragment : DialogFragment() {

    private var messageText: String = BaseViewModel.DEFAULT_LOADING_MESSAGE
    private var firstFrameDrawn = false
    private var firstFrameCallbackScheduled = false
    private var viewGeneration = 0L
    private var onFirstFrameDrawn: (() -> Unit)? = null

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
        invalidateFirstFrame()
        bindMessage(view)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        view?.let(::awaitFirstFrame)
    }

    override fun onStop() {
        invalidateFirstFrame()
        super.onStop()
    }

    override fun onDestroyView() {
        invalidateFirstFrame()
        super.onDestroyView()
    }

    fun updateMessage(message: String) {
        messageText = message
        view?.findViewById<TextView>(R.id.tvMessage)?.text = messageText
    }

    fun setOnFirstFrameDrawnListener(listener: (() -> Unit)?) {
        onFirstFrameDrawn = listener
        if (firstFrameDrawn && listener != null) {
            val currentView = view ?: return
            val generation = viewGeneration
            currentView.postOnAnimation {
                dispatchFirstFrameIfCurrent(currentView, generation)
            }
        }
    }

    private fun awaitFirstFrame(rootView: View) {
        val generation = viewGeneration
        rootView.doOnPreDraw {
            if (generation != viewGeneration || firstFrameDrawn || firstFrameCallbackScheduled) {
                return@doOnPreDraw
            }
            firstFrameCallbackScheduled = true
            rootView.postOnAnimation {
                if (generation != viewGeneration || view !== rootView) return@postOnAnimation
                firstFrameCallbackScheduled = false
                firstFrameDrawn = true
                dispatchFirstFrameIfCurrent(rootView, generation)
            }
        }
    }

    private fun dispatchFirstFrameIfCurrent(rootView: View, generation: Long) {
        if (generation != viewGeneration || view !== rootView) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val listener = onFirstFrameDrawn ?: return
        onFirstFrameDrawn = null
        listener.invoke()
    }

    private fun invalidateFirstFrame() {
        viewGeneration += 1L
        firstFrameDrawn = false
        firstFrameCallbackScheduled = false
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
