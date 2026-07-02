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
    fun spellingPracticePhone() {
        val view = inflate(R.layout.fragment_practice_spelling)
        view.find<TextView>(R.id.tv_progress).text = "今日已练: 3/20"
        view.find<TextView>(R.id.tv_word_length).text = "目标长度: 8"
        view.find<TextView>(R.id.tv_meaning).text = "释义：v. 记住，熟记"
        view.find<TextView>(R.id.tv_attempt_status).text = "尝试 1 次 · 提示 0 次"
        view.find<TextView>(R.id.tv_result).apply {
            text = "还有 2 处需要调整，请再试一次"
            visibility = View.VISIBLE
        }

        val slots = view.find<LinearLayout>(R.id.layout_slots)
        listOf("M", "E", "M", "O", "", "", "", "").forEachIndexed { index, value ->
            slots.addView(spellingPracticeSlot(value, isWrong = index == 2))
        }

        val letters = view.find<android.widget.GridLayout>(R.id.grid_letters)
        letters.columnCount = 5
        listOf("R", "I", "Z", "E", "A", "T", "O", "N", "S", "").forEach { value ->
            letters.addView(spellingPracticeLetterButton(value))
        }

        paparazzi.snapshot(view, "spelling_practice_phone")
    }

    @Test
    fun spellingPracticeHandwritingExpandedPhone() {
        val view = inflate(R.layout.fragment_practice_spelling)
        view.find<TextView>(R.id.tv_progress).text = "今日已练: 3/20"
        view.find<TextView>(R.id.tv_word_length).text = "目标长度: 8"
        view.find<TextView>(R.id.tv_meaning).text = "释义：v. 记住，熟记"
        view.find<View>(R.id.handwriting_drawer).layoutParams.height = 210
        view.find<View>(R.id.handwriting_container).visibility = View.VISIBLE
        view.find<TextView>(R.id.tv_handwriting_toggle).text = "收起"

        val letters = view.find<android.widget.GridLayout>(R.id.grid_letters)
        letters.columnCount = 5
        listOf("R", "I", "Z", "E", "A").forEach { value ->
            letters.addView(spellingPracticeLetterButton(value))
        }

        paparazzi.snapshot(view, "spelling_practice_handwriting_expanded_phone")
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

    private fun spellingPracticeSlot(text: String, isWrong: Boolean = false): TextView {
        return TextView(paparazzi.context).apply {
            this.text = text
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(if (isWrong) 0xFFDC2626.toInt() else 0xFF0F172A.toInt())
            layoutParams = LinearLayout.LayoutParams(30, 36).apply {
                marginStart = 4
                marginEnd = 4
            }
            setBackgroundColor(if (isWrong) 0xFFFEF2F2.toInt() else 0xFFF8FAFC.toInt())
        }
    }

    private fun spellingPracticeLetterButton(text: String): MaterialButton {
        return MaterialButton(paparazzi.context).apply {
            this.text = text
            textSize = 16f
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = 56
                height = 46
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
