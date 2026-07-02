package com.chen.memorizewords.startup.appupdate

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.chen.memorizewords.R

class AppUpdateDialogFragment : DialogFragment(R.layout.app_dialog_update) {
    interface Listener {
        fun onAppUpdateLater(info: DialogInfo)
        fun onAppUpdateNow(info: DialogInfo)
    }

    data class DialogInfo(
        val releaseId: Long,
        val versionSpan: String,
        val forceUpdate: Boolean,
        val releaseNotes: List<String>
    )

    private val dialogInfo: DialogInfo
        get() = DialogInfo(
            releaseId = requireArguments().getLong(ARG_RELEASE_ID),
            versionSpan = requireArguments().getString(ARG_VERSION_SPAN).orEmpty(),
            forceUpdate = requireArguments().getBoolean(ARG_FORCE_UPDATE),
            releaseNotes = requireArguments().getStringArrayList(ARG_RELEASE_NOTES).orEmpty()
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = !dialogInfo.forceUpdate
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        if (dialogInfo.forceUpdate) {
            dialog.setCanceledOnTouchOutside(false)
            dialog.setOnKeyListener { _, keyCode, event ->
                keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val info = dialogInfo
        view.findViewById<TextView>(R.id.tvUpdateVersionSpan).text = info.versionSpan
        val noteContainer = view.findViewById<LinearLayout>(R.id.llUpdateNotes)
        noteContainer.removeAllViews()
        info.releaseNotes.take(5).forEachIndexed { index, note ->
            noteContainer.addView(createNoteView(index + 1, note))
        }
        val laterButton = view.findViewById<TextView>(R.id.tvUpdateLater)
        laterButton.visibility = if (info.forceUpdate) View.GONE else View.VISIBLE
        val updateNowButton = view.findViewById<TextView>(R.id.tvUpdateNow)
        if (info.forceUpdate) {
            updateNowButton.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                updateNowButton.layoutParams.height
            )
        }
        laterButton.setOnClickListener {
            (activity as? Listener)?.onAppUpdateLater(info)
            dismissAllowingStateLoss()
        }
        updateNowButton.setOnClickListener {
            (activity as? Listener)?.onAppUpdateNow(info)
        }
    }

    fun setDownloading(downloading: Boolean) {
        val view = view ?: return
        view.findViewById<TextView>(R.id.tvUpdateLater).isEnabled = !downloading
        view.findViewById<TextView>(R.id.tvUpdateNow).apply {
            isEnabled = !downloading
            text = if (downloading) "正在下载..." else "立即更新"
            alpha = if (downloading) 0.72f else 1f
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun createNoteView(index: Int, text: String): TextView {
        return TextView(requireContext()).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#334155"))
            this.text = "$index. $text"
            setPadding(0, 8, 0, 0)
            setLineSpacing(3f, 1f)
        }
    }

    companion object {
        const val TAG = "AppUpdateDialogFragment"
        private const val ARG_RELEASE_ID = "release_id"
        private const val ARG_VERSION_SPAN = "version_span"
        private const val ARG_FORCE_UPDATE = "force_update"
        private const val ARG_RELEASE_NOTES = "release_notes"

        fun newInstance(info: com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo): AppUpdateDialogFragment {
            return AppUpdateDialogFragment().apply {
                arguments = bundleOf(
                    ARG_RELEASE_ID to info.releaseId,
                    ARG_VERSION_SPAN to info.versionSpan,
                    ARG_FORCE_UPDATE to info.forceUpdate,
                    ARG_RELEASE_NOTES to ArrayList(info.releaseNotes)
                )
            }
        }
    }
}
