package com.chen.memorizewords.feature.learning.ui.practice

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class AppendOnlyEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val end = text?.length ?: 0
        if (selStart != end || selEnd != end) {
            setSelection(end)
        }
    }
}
