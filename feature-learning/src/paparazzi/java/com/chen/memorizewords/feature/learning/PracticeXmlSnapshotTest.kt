package com.chen.memorizewords.feature.learning

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.gridlayout.widget.GridLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.junit.Rule
import org.junit.Test

class PracticeXmlSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
        theme = "Theme.MaterialComponents.Light.NoActionBar"
    )

    @Test
    fun listeningMeaningPhone() {
        val view = inflate(R.layout.feature_learning_fragment_practice_listening)
        view.find<TextView>(R.id.tv_screen_title).text = "Listening"
        view.find<TextView>(R.id.tv_mode_badge).text = "Meaning"
        view.find<TextView>(R.id.tv_phonetic).text = "[əbˈzɔːpʃn]"
        view.find<TextView>(R.id.tv_prompt).text = "Choose the correct meaning"
        view.find<TextView>(R.id.btn_option_1).text = "n. absorption; taking in"
        view.find<TextView>(R.id.btn_option_2).text = "v. to reduce sharply"
        view.find<TextView>(R.id.btn_option_3).text = "adj. already prepared"
        view.find<TextView>(R.id.btn_option_4).text = "n. a short reply"
        view.find<View>(R.id.layout_practice_actions).visibility = View.VISIBLE
        view.find<TextView>(R.id.btn_primary_action).apply {
            text = "Check"
            visibility = View.VISIBLE
        }

        paparazzi.snapshot(view, "listening_meaning_phone")
    }

    @Test
    fun listeningSpellingPhone() {
        val view = inflate(R.layout.feature_learning_fragment_practice_listening)
        view.find<TextView>(R.id.tv_screen_title).text = "Listening"
        view.find<TextView>(R.id.tv_mode_badge).text = "Spelling"
        view.find<TextView>(R.id.tv_phonetic).text = "[ˈmeməraɪz]"
        view.find<TextView>(R.id.tv_prompt).text = "Spell the word you hear"
        view.find<View>(R.id.layout_meaning_options).visibility = View.GONE
        view.find<View>(R.id.layout_spelling_question).visibility = View.VISIBLE
        view.find<View>(R.id.layout_practice_actions).visibility = View.VISIBLE
        view.find<TextView>(R.id.btn_primary_action).apply {
            text = "Next"
            visibility = View.VISIBLE
        }

        val slots = view.find<LinearLayout>(R.id.layout_spelling_slots)
        listOf("m", "e", "m", "", "", "", "", "").forEach { value ->
            slots.addView(spellingSlot(value))
        }

        val letters = view.find<GridLayout>(R.id.grid_spelling_letters)
        listOf("o", "r", "i", "z", "e", "a").forEach { value ->
            letters.addView(letterButton(value))
        }

        paparazzi.snapshot(view, "listening_spelling_phone")
    }

    @Test
    fun listeningReportPhone() {
        val view = inflate(R.layout.feature_learning_fragment_practice_listening)
        view.find<TextView>(R.id.tv_screen_title).text = "Listening"
        view.find<View>(R.id.layout_practice_root).visibility = View.GONE
        view.find<View>(R.id.layout_report_root).visibility = View.VISIBLE
        view.find<TextView>(R.id.tv_report_title).text = "Practice report"
        view.find<TextView>(R.id.tv_report_hint).text = "12 words reviewed in this session"
        view.find<CircularProgressIndicator>(R.id.progress_report_accuracy).progress = 84
        view.find<TextView>(R.id.tv_report_accuracy).text = "84%"
        view.find<TextView>(R.id.tv_report_summary_primary).text = "Completed 12 words"
        view.find<TextView>(R.id.tv_report_summary_secondary).text = "19 attempts, 16 correct"
        view.find<LinearLayout>(R.id.layout_report_reviewed_words).apply {
            addView(reportWord("absorption", "correct"))
            addView(reportWord("memorize", "review again"))
        }
        view.find<LinearLayout>(R.id.layout_report_unfinished_words).apply {
            addView(reportWord("resilient", "not started"))
        }
        view.find<TextView>(R.id.btn_primary_action).apply {
            text = "Finish"
            visibility = View.VISIBLE
        }

        paparazzi.snapshot(view, "listening_report_phone")
    }

    @Test
    fun examHostShellPhone() {
        val view = inflate(R.layout.activity_practice)
        view.find<com.google.android.material.appbar.MaterialToolbar>(R.id.top_app_bar).title =
            "Exam Practice"

        paparazzi.snapshot(view, "exam_host_shell_phone")
    }

    private fun inflate(layoutId: Int): View {
        return LayoutInflater.from(paparazzi.context).inflate(layoutId, null, false)
    }

    private fun spellingSlot(text: String): TextView {
        return TextView(paparazzi.context).apply {
            this.text = text
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF0F172A.toInt())
            layoutParams = LinearLayout.LayoutParams(34, 42).apply {
                marginEnd = 6
            }
            setBackgroundColor(0xFFF1F5F9.toInt())
        }
    }

    private fun letterButton(text: String): MaterialButton {
        return MaterialButton(paparazzi.context).apply {
            this.text = text
            textSize = 15f
            isAllCaps = false
            minWidth = 42
            minHeight = 42
            insetTop = 0
            insetBottom = 0
            layoutParams = GridLayout.LayoutParams().apply {
                width = 48
                height = 44
                setMargins(5, 5, 5, 5)
            }
        }
    }

    private fun reportWord(word: String, status: String): TextView {
        return TextView(paparazzi.context).apply {
            text = "$word    $status"
            textSize = 14f
            setTextColor(0xFF334155.toInt())
            setPadding(0, 8, 0, 8)
        }
    }

    private inline fun <reified T : View> View.find(@IdRes id: Int): T {
        return requireNotNull(findViewById(id)) { "Missing view id=$id" }
    }
}
