package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import coil.load
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldConfig
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.feature.floatingreview.R

internal sealed interface CardRenderState {
    data class Status(val messageRes: Int) : CardRenderState

    data class WordContent(
        val word: Word,
        val definitions: List<WordDefinitions>,
        val examples: List<WordExample>,
        val settings: FloatingWordSettings
    ) : CardRenderState
}

internal class FloatingCardRenderer(
    private val context: Context
) {
    private companion object {
        const val EMPTY_PLACEHOLDER = "-"
    }

    private var measurementView: View? = null

    fun render(target: View, state: CardRenderState, loadImages: Boolean): Boolean {
        return when (state) {
            is CardRenderState.Status -> {
                renderStatus(target, state.messageRes)
                false
            }

            is CardRenderState.WordContent -> renderWord(
                target = target,
                word = state.word,
                definitions = state.definitions,
                examples = state.examples,
                settings = state.settings,
                loadImages = loadImages
            )
        }
    }

    fun measure(state: CardRenderState, cardWidth: Int): Int {
        val target = measurementView ?: LayoutInflater.from(context)
            .inflate(R.layout.module_floating_review_view_floating_card, null)
            .also { view ->
                val surface = view.findViewById<View>(R.id.module_floating_review_card_surface)
                (surface.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.gravity = Gravity.TOP or Gravity.START
                    surface.layoutParams = params
                }
                val panel = view.findViewById<View>(R.id.module_floating_review_card_panel)
                (panel.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    panel.layoutParams = params
                }
                val scroll = view.findViewById<ScrollView>(
                    R.id.module_floating_review_content_scroll
                )
                (scroll.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.weight = 0f
                    scroll.layoutParams = params
                }
                measurementView = view
            }
        render(target, state, loadImages = false)
        target.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return target.measuredHeight.coerceAtLeast(1)
    }

    fun clearMeasurementView() {
        measurementView = null
    }

    fun applyActionState(target: View, state: FloatingCardActionState) {
        target.findViewById<View>(R.id.module_floating_review_btn_favorite)?.apply {
            isEnabled = state.favoriteEnabled
            alpha = if (state.favoriteEnabled) 1f else 0.38f
        }
        target.findViewById<View>(R.id.module_floating_review_btn_refresh)?.apply {
            isEnabled = state.refreshEnabled
            alpha = if (state.refreshEnabled) 1f else 0.38f
        }
        target.findViewById<View>(R.id.module_floating_review_btn_copy)?.apply {
            isEnabled = state.copyEnabled
            alpha = if (state.copyEnabled) 1f else 0.38f
        }
    }

    fun buildCopyText(word: Word, definitions: List<WordDefinitions>): String {
        return buildList {
            add(word.word)
            buildPhoneticText(word).takeIf { it.isNotBlank() }?.let(::add)
            buildDefinitionLines(
                definitions = definitions,
                showPartOfSpeech = true,
                showMeaning = true
            ).takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString("\n")
    }

    private fun renderStatus(target: View, messageRes: Int) {
        target.findViewById<TextView>(R.id.module_floating_review_tv_word)?.apply {
            text = context.getString(messageRes)
            visibility = View.VISIBLE
        }
        target.findViewById<View>(R.id.module_floating_review_phonetic_row)?.visibility = View.GONE
        target.findViewById<View>(R.id.module_floating_review_phonetic_divider)?.visibility = View.GONE
        target.findViewById<LinearLayout>(R.id.module_floating_review_floating_fields_container)
            ?.removeAllViews()
        applyActionState(target, resolveCardActionState(hasWord = false))
    }

    private fun renderWord(
        target: View,
        word: Word,
        definitions: List<WordDefinitions>,
        examples: List<WordExample>,
        settings: FloatingWordSettings,
        loadImages: Boolean
    ): Boolean {
        val container = target.findViewById<LinearLayout>(
            R.id.module_floating_review_floating_fields_container
        ) ?: return false
        container.removeAllViews()
        val configs = settings.fieldConfigs.filter { it.enabled }
        if (configs.isEmpty()) {
            renderStatus(target, R.string.module_floating_review_empty)
            return false
        }
        val enabledTypes = configs.map { it.type }.toSet()
        renderHeader(target, word, enabledTypes)
        renderPhonetics(target, word, enabledTypes)
        renderDefinitions(container, definitions, enabledTypes, configs)
        renderExtraFields(container, word, examples, configs, loadImages)
        applyActionState(target, resolveCardActionState(hasWord = true))
        return true
    }

    private fun renderHeader(
        target: View,
        word: Word,
        enabledTypes: Set<FloatingWordFieldType>
    ) {
        target.findViewById<TextView>(R.id.module_floating_review_tv_word)?.apply {
            text = word.word
            visibility = if (FloatingWordFieldType.WORD in enabledTypes) View.VISIBLE else View.GONE
        }
    }

    private fun renderPhonetics(
        target: View,
        word: Word,
        enabledTypes: Set<FloatingWordFieldType>
    ) {
        val row = target.findViewById<View>(R.id.module_floating_review_phonetic_row) ?: return
        val divider = target.findViewById<View>(R.id.module_floating_review_phonetic_divider)
        val uk = word.phoneticUK?.takeIf { it.isNotBlank() }
        val us = word.phoneticUS?.takeIf { it.isNotBlank() }
        val showRow = FloatingWordFieldType.PHONETIC in enabledTypes && (uk != null || us != null)
        row.visibility = if (showRow) View.VISIBLE else View.GONE
        divider?.visibility = if (showRow) View.VISIBLE else View.GONE
        if (!showRow) return

        bindPhoneticGroup(
            target = target,
            groupId = R.id.module_floating_review_phonetic_uk_group,
            textId = R.id.module_floating_review_tv_phonetic_uk,
            value = uk
        )
        bindPhoneticGroup(
            target = target,
            groupId = R.id.module_floating_review_phonetic_us_group,
            textId = R.id.module_floating_review_tv_phonetic_us,
            value = us
        )
    }

    private fun bindPhoneticGroup(target: View, groupId: Int, textId: Int, value: String?) {
        val group = target.findViewById<View>(groupId) ?: return
        group.visibility = if (value == null) View.GONE else View.VISIBLE
        target.findViewById<TextView>(textId)?.text = value.orEmpty()
    }

    private fun renderDefinitions(
        container: LinearLayout,
        definitions: List<WordDefinitions>,
        enabledTypes: Set<FloatingWordFieldType>,
        configs: List<FloatingWordFieldConfig>
    ) {
        val showMeaning = FloatingWordFieldType.MEANING in enabledTypes
        val showPartOfSpeech = FloatingWordFieldType.PART_OF_SPEECH in enabledTypes
        if (!showMeaning && !showPartOfSpeech) return

        val text = buildDefinitionLines(
            definitions = definitions,
            showPartOfSpeech = showPartOfSpeech || showMeaning,
            showMeaning = showMeaning
        )
        if (text.isBlank()) return
        val textSize = resolveFontSize(configs, FloatingWordFieldType.MEANING, 16)
            .coerceAtLeast(16)
        container.addView(
            buildTextView(text, textSize.toFloat(), 0xFF111827.toInt()).apply {
                setLineSpacing(10.dpToPx(context).toFloat(), 1f)
            }
        )
    }

    private fun renderExtraFields(
        container: LinearLayout,
        word: Word,
        examples: List<WordExample>,
        configs: List<FloatingWordFieldConfig>,
        loadImages: Boolean
    ) {
        configs
            .filter {
                it.type == FloatingWordFieldType.EXAMPLE ||
                    it.type == FloatingWordFieldType.NOTE ||
                    it.type == FloatingWordFieldType.IMAGE
            }
            .forEach { config ->
                val view = when (config.type) {
                    FloatingWordFieldType.EXAMPLE -> buildTextView(
                        buildExampleText(examples),
                        config.fontSizeSp.toFloat(),
                        0xFF334155.toInt()
                    )

                    FloatingWordFieldType.NOTE -> buildTextView(
                        word.notes.orEmpty(),
                        config.fontSizeSp.toFloat(),
                        0xFF334155.toInt()
                    )

                    FloatingWordFieldType.IMAGE -> buildImageView(
                        url = word.mnemonicImageUrl,
                        sizeDp = config.fontSizeSp,
                        loadImage = loadImages
                    )

                    else -> null
                }
                view?.takeIf(::hasRenderableContent)?.let {
                    val params = (it.layoutParams as? LinearLayout.LayoutParams)
                        ?: LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    params.topMargin = if (container.childCount > 0) 8.dpToPx(context) else 0
                    it.layoutParams = params
                    container.addView(it)
                }
            }
    }

    private fun buildTextView(text: String, textSizeSp: Float, color: Int): TextView {
        val content = text.ifBlank { EMPTY_PLACEHOLDER }
        val placeholder = content == EMPTY_PLACEHOLDER
        return TextView(context).apply {
            this.text = content
            setTextColor(if (placeholder) 0xFF94A3B8.toInt() else color)
            this.textSize = textSizeSp
            includeFontPadding = false
        }
    }

    private fun buildImageView(url: String?, sizeDp: Int, loadImage: Boolean): View {
        if (url.isNullOrBlank()) {
            return buildTextView(EMPTY_PLACEHOLDER, 12f, 0xFF64748B.toInt())
        }
        val height = sizeDp.coerceAtLeast(80).dpToPx(context)
        return ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (loadImage) load(url)
        }
    }

    private fun hasRenderableContent(view: View): Boolean {
        return (view as? TextView)?.text?.toString() != EMPTY_PLACEHOLDER
    }

    private fun resolveFontSize(
        configs: List<FloatingWordFieldConfig>,
        type: FloatingWordFieldType,
        fallback: Int
    ): Int = configs.firstOrNull { it.type == type }?.fontSizeSp ?: fallback

    private fun buildDefinitionLines(
        definitions: List<WordDefinitions>,
        showPartOfSpeech: Boolean,
        showMeaning: Boolean
    ): String {
        if (definitions.isEmpty()) return ""
        return definitions.take(2).joinToString("\n") { definition ->
            when {
                showPartOfSpeech && showMeaning ->
                    "${formatPartOfSpeech(definition.partOfSpeech.abbr)} ${definition.meaningChinese}"
                showPartOfSpeech -> formatPartOfSpeech(definition.partOfSpeech.abbr)
                showMeaning -> definition.meaningChinese
                else -> ""
            }
        }
    }

    private fun formatPartOfSpeech(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return trimmed
        return if (trimmed.endsWith(".")) trimmed else "$trimmed."
    }

    private fun buildExampleText(examples: List<WordExample>): String {
        val example = examples.firstOrNull() ?: return ""
        val zh = example.chineseTranslation?.takeIf { it.isNotBlank() }
        return if (zh != null) "${example.englishSentence}\n$zh" else example.englishSentence
    }

    private fun buildPhoneticText(word: Word): String {
        val us = word.phoneticUS?.takeIf { it.isNotBlank() }
        val uk = word.phoneticUK?.takeIf { it.isNotBlank() }
        return when {
            us != null && uk != null -> context.getString(
                R.string.module_floating_review_phonetic_both,
                us,
                uk
            )
            us != null -> context.getString(R.string.module_floating_review_phonetic_us_only, us)
            uk != null -> context.getString(R.string.module_floating_review_phonetic_uk_only, uk)
            else -> ""
        }
    }
}
