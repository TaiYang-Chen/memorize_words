package com.chen.memorizewords.feature.feedback.ui.agreement

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.R as CoreUiR
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.feedback.R
import com.chen.memorizewords.feature.feedback.databinding.FeatureFeedbackFragmentAgreementBinding

class AgreementFragment : BaseFragment<BaseViewModel, FeatureFeedbackFragmentAgreementBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    private val sectionAnchors = mutableMapOf<String, View>()

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        val document = AgreementDocuments.documentFor(
            AgreementTypes.normalize(arguments?.getString(ARG_AGREEMENT_TYPE))
        )
        databind.tvAgreementTitle.text = document.title
        renderDocument(document)
    }

    private fun renderDocument(document: AgreementDocument) {
        sectionAnchors.clear()
        databind.content.removeAllViews()
        databind.content.addView(createMetaBlock(document))
        databind.content.addView(createSummaryBlock(document.summary))
        databind.content.addView(createCatalogBlock(document.sections))
        document.sections.forEachIndexed { index, section ->
            val sectionView = createSectionBlock(index + 1, section)
            sectionAnchors[section.title] = sectionView
            databind.content.addView(sectionView)
        }
        databind.content.addView(createVersionText(document.version))
    }

    private fun createMetaBlock(document: AgreementDocument): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(textView(document.title, R.dimen.feature_feedback_agreement_display_size, R.color.module_feedback_about_text_primary, Typeface.BOLD))
            addView(textView(getString(R.string.feature_feedback_agreement_updated_label, document.updatedAt), R.dimen.feature_feedback_agreement_meta_size, R.color.module_feedback_about_text_secondary))
            addView(textView(getString(R.string.feature_feedback_agreement_effective_label, document.effectiveAt), R.dimen.feature_feedback_agreement_meta_size, R.color.module_feedback_about_text_secondary))
            layoutParams = verticalParams(top = R.dimen.feature_feedback_agreement_content_top)
        }
    }

    private fun createSummaryBlock(summary: List<String>): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.feature_feedback_bg_agreement_summary)
            setPadding(dimen(CoreUiR.dimen.core_ui_space_14), dimen(CoreUiR.dimen.core_ui_space_14), dimen(CoreUiR.dimen.core_ui_space_14), dimen(CoreUiR.dimen.core_ui_space_14))
            addView(textView(getString(R.string.feature_feedback_agreement_summary_title), R.dimen.feature_feedback_agreement_section_size, R.color.module_feedback_about_text_primary, Typeface.BOLD))
            summary.forEachIndexed { index, item ->
                addView(textView("${index + 1}. $item", R.dimen.feature_feedback_agreement_body_size, R.color.module_feedback_about_text_secondary))
            }
            layoutParams = verticalParams(top = CoreUiR.dimen.core_ui_space_18)
        }
    }

    private fun createCatalogBlock(sections: List<AgreementSection>): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(textView(getString(R.string.feature_feedback_agreement_catalog_title), R.dimen.feature_feedback_agreement_section_size, R.color.module_feedback_about_text_primary, Typeface.BOLD))
            sections.forEachIndexed { index, section ->
                val catalogTitle = section.title.substringAfter("、", section.title)
                val row = textView("${index + 1}. $catalogTitle", R.dimen.feature_feedback_agreement_body_size, R.color.module_feedback_primary)
                row.setBackgroundResource(R.drawable.feature_feedback_bg_agreement_catalog_item)
                row.setPadding(dimen(CoreUiR.dimen.core_ui_space_12), dimen(CoreUiR.dimen.core_ui_space_10), dimen(CoreUiR.dimen.core_ui_space_12), dimen(CoreUiR.dimen.core_ui_space_10))
                row.setOnClickListener {
                    sectionAnchors[section.title]?.let { target ->
                        databind.scroll.smoothScrollTo(0, target.top)
                    }
                }
                addView(row, verticalParams(top = CoreUiR.dimen.core_ui_space_8))
            }
            layoutParams = verticalParams(top = CoreUiR.dimen.core_ui_space_20)
        }
    }

    private fun createSectionBlock(number: Int, section: AgreementSection): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(textView(section.title, R.dimen.feature_feedback_agreement_section_size, R.color.module_feedback_about_text_primary, Typeface.BOLD))
            section.paragraphs.forEach { paragraph ->
                addView(textView(paragraph, R.dimen.feature_feedback_agreement_body_size, R.color.module_feedback_about_text_secondary))
            }
            section.items.forEach { item ->
                addView(createInfoItem(item))
            }
            contentDescription = "$number ${section.title}"
            layoutParams = verticalParams(top = CoreUiR.dimen.core_ui_space_24)
        }
    }

    private fun createInfoItem(item: AgreementItem): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.feature_feedback_bg_agreement_info_item)
            setPadding(dimen(CoreUiR.dimen.core_ui_space_12), dimen(CoreUiR.dimen.core_ui_space_12), dimen(CoreUiR.dimen.core_ui_space_12), dimen(CoreUiR.dimen.core_ui_space_12))
            addView(textView(item.title, R.dimen.feature_feedback_agreement_item_title_size, R.color.module_feedback_about_text_primary, Typeface.BOLD))
            addView(textView(item.description, R.dimen.feature_feedback_agreement_body_size, R.color.module_feedback_about_text_secondary))
            layoutParams = verticalParams(top = CoreUiR.dimen.core_ui_space_10)
        }
    }

    private fun createVersionText(version: String): View {
        return textView(getString(R.string.feature_feedback_agreement_version_label, version), R.dimen.feature_feedback_agreement_meta_size, R.color.module_feedback_about_text_muted).apply {
            gravity = android.view.Gravity.CENTER
            layoutParams = verticalParams(top = CoreUiR.dimen.core_ui_space_28)
        }
    }

    private fun textView(text: String, sizeRes: Int, colorRes: Int, style: Int = Typeface.NORMAL): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(sizeRes))
            setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            setTypeface(typeface, style)
            includeFontPadding = true
            setLineSpacing(resources.getDimension(R.dimen.feature_feedback_agreement_body_line_spacing), 1f)
            layoutParams = verticalParams(top = CoreUiR.dimen.core_ui_space_8)
        }
    }

    private fun verticalParams(top: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            if (top != 0) topMargin = dimen(top)
        }
    }

    private fun dimen(resId: Int): Int {
        return resources.getDimensionPixelSize(resId)
    }

    companion object {
        const val ARG_AGREEMENT_TYPE = "agreementType"
    }
}
