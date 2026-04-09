package com.chen.memorizewords.feature.learning.adapter

import android.graphics.Color
import android.graphics.Rect
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.databinding.BindingAdapter
import com.chen.memorizewords.feature.learning.R
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class ClickableWordToken(
    val rawWord: String,
    val normalizedWord: String
)

interface OnWordClickListener {
    fun onWordClick(token: ClickableWordToken, rect: Rect)
}

private val WORD_REGEX = Regex("[A-Za-z]+(?:[-'’][A-Za-z]+)*")
private val activeHighlightTextColor = "#2563EB".toColorInt()

private class ActiveWordTextColorSpan(color: Int) : ForegroundColorSpan(color)

private var activeTextViewRef: WeakReference<TextView>? = null

@BindingAdapter("clickableWords", "onWordClickListener")
fun TextView.setClickableWords(sentence: String?, listener: OnWordClickListener?) {
    clearActiveHighlight(this)
    if (sentence.isNullOrBlank()) {
        text = sentence.orEmpty()
        return
    }

    val spannable = SpannableString(sentence)
    WORD_REGEX.findAll(sentence).forEach { match ->
        val start = match.range.first
        val endExclusive = match.range.last + 1
        val rawWord = match.value
        val normalizedWord = normalizeLookupWord(rawWord)
        if (normalizedWord.isBlank()) return@forEach

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val anchorRect = calculateWordRectOnScreen(this@setClickableWords, start, endExclusive)
                applyPersistentHighlight(this@setClickableWords, start, endExclusive)
                listener?.onWordClick(
                    token = ClickableWordToken(rawWord = rawWord, normalizedWord = normalizedWord),
                    rect = anchorRect
                )
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = "#414141".toColorInt()
                ds.isUnderlineText = false
            }
        }

        spannable.setSpan(clickableSpan, start, endExclusive, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    text = spannable
    movementMethod = LinkMovementMethod.getInstance()
    highlightColor = Color.TRANSPARENT
}

fun normalizeLookupWord(rawWord: String): String {
    if (rawWord.isBlank()) return ""
    val noEdgePunctuation = rawWord.trim { ch ->
        !ch.isLetter() && ch != '\'' && ch != '’' && ch != '-'
    }
    val apostropheNormalized = noEdgePunctuation.replace('’', '\'')
    val noEdgeHyphen = apostropheNormalized.trim('-')
    return noEdgeHyphen.lowercase(Locale.US)
}

private fun toMutableSpannable(text: CharSequence): Spannable {
    return when (text) {
        is Spannable -> text
        is Spanned -> SpannableString(text)
        else -> SpannableString(text)
    }
}

private fun clearActiveHighlight(textView: TextView) {
    val working = toMutableSpannable(textView.text)
    val spans = working.getSpans(0, working.length, ActiveWordTextColorSpan::class.java)
    if (spans.isNotEmpty()) {
        spans.forEach { working.removeSpan(it) }
        textView.text = SpannableString(working)
    }
    if (activeTextViewRef?.get() === textView) {
        activeTextViewRef = null
    }
}

fun clearGlobalClickableWordHighlight() {
    val active = activeTextViewRef?.get() ?: return
    clearActiveHighlight(active)
}

private fun applyPersistentHighlight(textView: TextView, start: Int, endExclusive: Int) {
    clearGlobalClickableWordHighlight()

    val working = toMutableSpannable(textView.text)
    working.getSpans(0, working.length, ActiveWordTextColorSpan::class.java).forEach {
        working.removeSpan(it)
    }

    val safeStart = start.coerceIn(0, working.length)
    val safeEnd = endExclusive.coerceIn(safeStart, working.length)
    val span = ActiveWordTextColorSpan(activeHighlightTextColor)
    working.setSpan(span, safeStart, safeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

    textView.text = SpannableString(working)
    activeTextViewRef = WeakReference(textView)
}

private fun calculateWordRectOnScreen(textView: TextView, start: Int, endExclusive: Int): Rect {
    val fallback = Rect()
    val fallbackLocation = IntArray(2)
    textView.getLocationInWindow(fallbackLocation)
    fallback.set(
        fallbackLocation[0],
        fallbackLocation[1],
        fallbackLocation[0] + textView.width,
        fallbackLocation[1] + textView.height
    )
    val layout = textView.layout ?: return fallback
    if (start < 0 || endExclusive <= start || endExclusive > textView.text.length) return fallback

    val location = IntArray(2)
    textView.getLocationInWindow(location)

    val startLine = layout.getLineForOffset(start)
    val endLine = layout.getLineForOffset(endExclusive - 1)

    var left = Int.MAX_VALUE
    var top = Int.MAX_VALUE
    var right = Int.MIN_VALUE
    var bottom = Int.MIN_VALUE

    val fontMetrics = textView.paint.fontMetricsInt
    for (line in startLine..endLine) {
        val lineStartOffset = max(start, layout.getLineStart(line))
        val lineEndOffset = min(endExclusive, layout.getLineEnd(line))
        val lineLeft = min(
            layout.getPrimaryHorizontal(lineStartOffset),
            layout.getPrimaryHorizontal(lineEndOffset)
        )
        val lineRight = max(
            layout.getPrimaryHorizontal(lineStartOffset),
            layout.getPrimaryHorizontal(lineEndOffset)
        )
        val baseline = layout.getLineBaseline(line)

        val absLeft =
            location[0] + textView.totalPaddingLeft - textView.scrollX + floor(lineLeft).toInt()
        val absRight =
            location[0] + textView.totalPaddingLeft - textView.scrollX + ceil(lineRight).toInt()
        val absTop =
            location[1] + textView.totalPaddingTop - textView.scrollY + baseline + fontMetrics.ascent
        val absBottom =
            location[1] + textView.totalPaddingTop - textView.scrollY + baseline + fontMetrics.descent

        left = min(left, absLeft)
        right = max(right, absRight)
        top = min(top, absTop)
        bottom = max(bottom, absBottom)
    }

    if (left == Int.MAX_VALUE || right == Int.MIN_VALUE || top == Int.MAX_VALUE || bottom == Int.MIN_VALUE) {
        return fallback
    }

    if (right <= left) {
        right =
            left + max(1, textView.paint.measureText(textView.text.substring(start, endExclusive)).toInt())
    }
    if (bottom <= top) {
        bottom = top + textView.lineHeight
    }
    return Rect(left, top, right, bottom)
}
