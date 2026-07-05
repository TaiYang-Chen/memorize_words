package com.chen.memorizewords.startup.appupdate

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.chen.memorizewords.R
import com.chen.memorizewords.core.ui.R as CoreUiR
import com.chen.memorizewords.core.ui.ext.dimenPx
import com.chen.memorizewords.core.ui.ext.dimenPxFloat
import com.chen.memorizewords.core.ui.ext.setTextSizeFromDimen
import java.util.Locale

class AppUpdateDialogFragment : DialogFragment(R.layout.app_dialog_update) {
    interface Listener {
        fun onAppUpdateLater(info: DialogInfo)
        fun onAppUpdateNow(info: DialogInfo)
    }

    data class DialogInfo(
        val releaseId: Long,
        val versionSpan: String,
        val forceUpdate: Boolean,
        val releaseNotes: List<String>,
        val publishedAt: String?,
        val fileSizeBytes: Long?,
        val riskTips: List<String>
    )

    private val dialogInfo: DialogInfo
        get() = DialogInfo(
            releaseId = requireArguments().getLong(ARG_RELEASE_ID),
            versionSpan = requireArguments().getString(ARG_VERSION_SPAN).orEmpty(),
            forceUpdate = requireArguments().getBoolean(ARG_FORCE_UPDATE),
            releaseNotes = requireArguments().getStringArrayList(ARG_RELEASE_NOTES).orEmpty(),
            publishedAt = requireArguments().getString(ARG_PUBLISHED_AT),
            fileSizeBytes = requireArguments().getLong(ARG_FILE_SIZE_BYTES).takeIf { it > 0L },
            riskTips = requireArguments().getStringArrayList(ARG_RISK_TIPS).orEmpty()
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
        renderMeta(view, info)
        renderNotes(view, info)
        setDownloadingProgress(null)

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
            text = if (downloading) "\u6b63\u5728\u4e0b\u8f7d..." else "\u7acb\u5373\u66f4\u65b0"
            alpha = if (downloading) 0.72f else 1f
        }
        if (!downloading) setDownloadingProgress(null)
    }

    fun setDownloadingProgress(progress: Int?) {
        val view = view ?: return
        val progressBar = view.findViewById<ProgressBar>(R.id.pbUpdateDownload)
        val progressText = view.findViewById<TextView>(R.id.tvUpdateProgress)
        if (progress == null) {
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
            return
        }
        val safeProgress = progress.coerceIn(0, 100)
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        progressBar.progress = safeProgress
        progressText.text = "\u4e0b\u8f7d\u8fdb\u5ea6\uff1a$safeProgress%"
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun renderMeta(view: View, info: DialogInfo) {
        val metaContainer = view.findViewById<LinearLayout>(R.id.llUpdateMeta)
        metaContainer.removeAllViews()
        metaContainer.addView(createMetaView("\u53d1\u5e03\u65f6\u95f4\uff1a${info.publishedAt ?: "--"}"))
        metaContainer.addView(createMetaView("\u5305\u5927\u5c0f\uff1a${formatBytes(info.fileSizeBytes)}"))
        metaContainer.addView(createMetaView("\u5b89\u5168\u6821\u9a8c\uff1a\u4e0b\u8f7d\u5b8c\u6210\u540e\u4f1a\u6821\u9a8c\u5b89\u88c5\u5305\u5b8c\u6574\u6027"))
    }

    private fun renderNotes(view: View, info: DialogInfo) {
        val noteContainer = view.findViewById<LinearLayout>(R.id.llUpdateNotes)
        noteContainer.removeAllViews()
        noteContainer.addView(createMetaView("\u66f4\u65b0\u5185\u5bb9", bold = true))
        info.releaseNotes.ifEmpty {
            listOf("\u672c\u6b21\u66f4\u65b0\u6682\u65e0\u8be6\u7ec6\u8bf4\u660e")
        }.take(5).forEachIndexed { index, note ->
            noteContainer.addView(createNoteView(index + 1, note))
        }
        noteContainer.addView(createMetaView("\u98ce\u9669\u63d0\u793a", bold = true))
        info.riskTips.ifEmpty {
            listOf("\u66f4\u65b0\u671f\u95f4\u8bf7\u4fdd\u6301\u7f51\u7edc\u7a33\u5b9a\uff1b\u5b89\u88c5\u540e\u5e94\u7528\u53ef\u80fd\u9700\u8981\u91cd\u542f\u3002")
        }.forEach { tip ->
            noteContainer.addView(createMetaView(tip))
        }
    }

    private fun createNoteView(index: Int, text: String): TextView {
        return TextView(requireContext()).apply {
            setTextSizeFromDimen(CoreUiR.dimen.core_ui_text_14)
            setTextColor(Color.parseColor("#334155"))
            this.text = "$index. $text"
            setPadding(0, dimen(CoreUiR.dimen.core_ui_dp_8), 0, 0)
            setLineSpacing(dimenFloat(CoreUiR.dimen.core_ui_dp_3), 1f)
        }
    }

    private fun createMetaView(text: String, bold: Boolean = false): TextView {
        return TextView(requireContext()).apply {
            setTextSizeFromDimen(
                if (bold) CoreUiR.dimen.core_ui_text_15 else CoreUiR.dimen.core_ui_text_13
            )
            setTextColor(Color.parseColor(if (bold) "#0f172a" else "#475569"))
            this.text = text
            setPadding(0, dimen(CoreUiR.dimen.core_ui_dp_6), 0, 0)
            setLineSpacing(dimenFloat(CoreUiR.dimen.core_ui_dp_3), 1f)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun formatBytes(bytes: Long?): String {
        val value = bytes ?: return "--"
        if (value <= 0L) return "--"
        val mb = value / 1024.0 / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            "${value / 1024} KB"
        }
    }

    private fun dimen(id: Int): Int {
        return requireContext().dimenPx(id)
    }

    private fun dimenFloat(id: Int): Float {
        return requireContext().dimenPxFloat(id)
    }

    companion object {
        const val TAG = "AppUpdateDialogFragment"
        private const val ARG_RELEASE_ID = "release_id"
        private const val ARG_VERSION_SPAN = "version_span"
        private const val ARG_FORCE_UPDATE = "force_update"
        private const val ARG_RELEASE_NOTES = "release_notes"
        private const val ARG_PUBLISHED_AT = "published_at"
        private const val ARG_FILE_SIZE_BYTES = "file_size_bytes"
        private const val ARG_RISK_TIPS = "risk_tips"

        fun newInstance(info: com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo): AppUpdateDialogFragment {
            return AppUpdateDialogFragment().apply {
                arguments = bundleOf(
                    ARG_RELEASE_ID to info.releaseId,
                    ARG_VERSION_SPAN to info.versionSpan,
                    ARG_FORCE_UPDATE to info.forceUpdate,
                    ARG_RELEASE_NOTES to ArrayList(info.releaseNotes),
                    ARG_PUBLISHED_AT to info.publishedAt,
                    ARG_FILE_SIZE_BYTES to (info.fileSizeBytes ?: 0L),
                    ARG_RISK_TIPS to ArrayList(info.riskTips)
                )
            }
        }
    }
}
