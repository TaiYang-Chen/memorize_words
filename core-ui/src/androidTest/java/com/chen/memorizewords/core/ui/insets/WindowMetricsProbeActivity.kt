package com.chen.memorizewords.core.ui.insets

import android.os.Bundle
import android.widget.FrameLayout
import com.chen.memorizewords.core.ui.activity.PhoneEdgeToEdgeActivity

class WindowMetricsProbeActivity : PhoneEdgeToEdgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))
    }
}
