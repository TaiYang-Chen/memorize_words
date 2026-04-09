package com.chen.memorizewords.feature.learning.ui.fragment

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.feature.learning.R
import kotlin.math.max
import kotlin.math.min

class WordQuickPopupController(
    private val activity: Activity,
    private val onSpeakUs: (word: String) -> Unit,
    private val onSpeakUk: (word: String) -> Unit,
    private val onViewFull: (word: Word) -> Unit,
    private val onDismissed: () -> Unit
) {
    private val maxPopupWidth = 360.dpToPx(activity)
    private val horizontalMargin = 16.dpToPx(activity)
    private val edgePadding = 8.dpToPx(activity)
    private val anchorGap = 0

    private var popupWindow: PopupWindow? = null
    private var popupView: View? = null
    private var currentWord: Word? = null
    private var suppressDismissCallback = false

    fun render(state: WordQuickPopupUiState) {
        ensurePopupWindow()
        val view = popupView ?: return
        bindContent(view, state)
        showOrUpdate(state.anchorRect)
    }

    fun dismiss() {
        val popup = popupWindow ?: return
        if (!popup.isShowing) return
        suppressDismissCallback = true
        popup.dismiss()
        suppressDismissCallback = false
    }

    private fun ensurePopupWindow() {
        if (popupWindow != null && popupView != null) return
        val contentView = LayoutInflater.from(activity).inflate(R.layout.popup_word_detail, null)
        popupView = contentView
        popupWindow = PopupWindow(
            contentView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener {
                if (!suppressDismissCallback) {
                    onDismissed.invoke()
                }
            }
        }
    }

    private fun bindContent(view: View, state: WordQuickPopupUiState) {
        val tvWord = view.findViewById<TextView>(R.id.tv_word)
        val tvPhoneticUs = view.findViewById<TextView>(R.id.tv_phonetic_us)
        val tvPhoneticUk = view.findViewById<TextView>(R.id.tv_phonetic_uk)
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)
        val tvMeaning = view.findViewById<TextView>(R.id.tv_meaning)
        val progress = view.findViewById<ProgressBar>(R.id.progress_loading)
        val btnSpeakUs = view.findViewById<TextView>(R.id.btn_speak_us)
        val btnSpeakUk = view.findViewById<TextView>(R.id.btn_speak_uk)
        val btnViewFull = view.findViewById<TextView>(R.id.btn_view_full)

        val lookup = state.result
        val word = lookup?.word
        currentWord = word
        tvWord.text = word?.word ?: state.token.rawWord
        tvPhoneticUs.text = "美 ${word?.phoneticUS?.takeIf { it.isNotBlank() } ?: "暂无音标"}"
        tvPhoneticUk.text = "英 ${word?.phoneticUK?.takeIf { it.isNotBlank() } ?: "暂无音标"}"
        btnSpeakUs.isEnabled = word != null
        btnSpeakUk.isEnabled = word != null
        btnViewFull.isEnabled = word != null && state.status == WordQuickPopupUiState.Status.SUCCESS
        btnViewFull.alpha = if (btnViewFull.isEnabled) 1f else 0.45f

        btnSpeakUs.setOnClickListener {
            currentWord?.word?.takeIf { text -> text.isNotBlank() }?.let(onSpeakUs)
        }
        btnSpeakUk.setOnClickListener {
            currentWord?.word?.takeIf { text -> text.isNotBlank() }?.let(onSpeakUk)
        }
        btnViewFull.setOnClickListener {
            currentWord?.let(onViewFull)
        }

        when (state.status) {
            WordQuickPopupUiState.Status.LOADING -> {
                progress.visibility = View.VISIBLE
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = "正在查词..."
                tvMeaning.text = "请稍候"
            }

            WordQuickPopupUiState.Status.SUCCESS -> {
                progress.visibility = View.GONE
                tvStatus.visibility = View.GONE
                tvMeaning.text = buildMeaningSummary(lookup)
            }

            WordQuickPopupUiState.Status.MISSING -> {
                progress.visibility = View.GONE
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = "未收录该单词"
                tvMeaning.text = "当前词库和服务端都未找到该词条。"
            }

            WordQuickPopupUiState.Status.ERROR -> {
                progress.visibility = View.GONE
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = state.errorMessage ?: "查询失败"
                tvMeaning.text = "请稍后重试。"
            }
        }
    }

    private fun buildMeaningSummary(result: com.chen.memorizewords.domain.model.words.word.WordQuickLookupResult?): String {
        val defs = result?.definitions.orEmpty()
        if (defs.isEmpty()) return "暂无释义"
        return defs.take(3).joinToString(separator = "\n") {
            "${it.partOfSpeech.abbr} ${it.meaningChinese}"
        }
    }

    private fun showOrUpdate(anchorRect: Rect) {
        val popup = popupWindow ?: return
        val contentView = popupView ?: return
        val decorView = activity.window.decorView

        val visibleRect = Rect(0, 0, decorView.width, decorView.height)
        val popupWidth = min(maxPopupWidth, visibleRect.width() - horizontalMargin * 2)

        contentView.measure(
            MeasureSpec.makeMeasureSpec(popupWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = contentView.measuredHeight

        val belowSpace = visibleRect.bottom - anchorRect.bottom
        val aboveSpace = anchorRect.top - visibleRect.top
        val showBelow = when {
            belowSpace >= popupHeight + anchorGap -> true
            aboveSpace >= popupHeight + anchorGap -> false
            else -> belowSpace >= aboveSpace
        }

        val rawX = anchorRect.centerX() - popupWidth / 2
        val x = rawX.coerceIn(
            visibleRect.left + horizontalMargin,
            visibleRect.right - popupWidth - horizontalMargin
        )

        val desiredY = if (showBelow) {
            anchorRect.bottom + anchorGap
        } else {
            anchorRect.top - popupHeight - anchorGap
        }
        val y = desiredY.coerceIn(
            visibleRect.top + edgePadding,
            visibleRect.bottom - popupHeight - edgePadding
        )

        if (popup.isShowing) {
            popup.update(x, y, popupWidth, popupHeight)
        } else {
            popup.width = popupWidth
            popup.height = popupHeight
            popup.showAtLocation(decorView, Gravity.NO_GRAVITY, x, y)
        }

        updateArrow(contentView, anchorRect, x, popupWidth, showBelow)
    }

    private fun updateArrow(
        contentView: View,
        anchorRect: Rect,
        popupX: Int,
        popupWidth: Int,
        showBelow: Boolean
    ) {
        val arrowTop = contentView.findViewById<View>(R.id.arrow_top)
        val arrowBottom = contentView.findViewById<View>(R.id.arrow_bottom)
        arrowTop.visibility = if (showBelow) View.VISIBLE else View.GONE
        arrowBottom.visibility = if (showBelow) View.GONE else View.VISIBLE

        val targetArrow = if (showBelow) arrowTop else arrowBottom
        val targetArrowWidth = if (targetArrow.width > 0) targetArrow.width else 12.dpToPx(activity)
        val leftLimit = horizontalMargin
        val rightLimit = popupWidth - horizontalMargin
        val centerInside = (anchorRect.centerX() - popupX).coerceIn(leftLimit, rightLimit)
        val lp = targetArrow.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        lp.leftMargin = (centerInside - targetArrowWidth / 2).coerceIn(
            edgePadding,
            popupWidth - targetArrowWidth - edgePadding
        )
        targetArrow.layoutParams = lp
    }
}
