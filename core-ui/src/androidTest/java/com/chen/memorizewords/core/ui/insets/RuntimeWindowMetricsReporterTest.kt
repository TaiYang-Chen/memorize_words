package com.chen.memorizewords.core.ui.insets

import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeWindowMetricsReporterTest {

    @Test
    fun emitsActualDeviceWindowMetricsAndAppliesTheDefaultSafeAreaPolicy() {
        ActivityScenario.launch(WindowMetricsProbeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val report = RuntimeWindowMetricsReporter.capture(activity)
                val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)

                assertEquals(
                    maxOf(report.statusBars.left, report.navigationBars.left, report.displayCutout.left, report.ime.left),
                    root.paddingLeft
                )
                assertEquals(
                    maxOf(report.statusBars.top, report.navigationBars.top, report.displayCutout.top, report.ime.top),
                    root.paddingTop
                )
                assertEquals(
                    maxOf(report.statusBars.right, report.navigationBars.right, report.displayCutout.right, report.ime.right),
                    root.paddingRight
                )
                assertEquals(
                    maxOf(report.statusBars.bottom, report.navigationBars.bottom, report.displayCutout.bottom, report.ime.bottom),
                    root.paddingBottom
                )

                RuntimeWindowMetricsReporter.emit(activity, label = "runtime-window-probe")
            }
        }
    }
}
