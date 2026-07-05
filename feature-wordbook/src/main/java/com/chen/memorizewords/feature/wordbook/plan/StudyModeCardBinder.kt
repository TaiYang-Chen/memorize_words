package com.chen.memorizewords.feature.wordbook.plan

import android.content.Context
import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.FeatureWordbookItemStudyModeCardBinding

data class StudyModeCardSpec(
    val minHeightDp: Int,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val cornerDecorSizeDp: Int,
    val iconContainerSizeDp: Int,
    val iconTopMarginDp: Int,
    val titleTopMarginDp: Int,
    val descriptionTopMarginDp: Int,
    val badgeTopMarginDp: Int
) {
    companion object {
        val MainCompact = StudyModeCardSpec(
            minHeightDp = 156,
            horizontalPaddingDp = 18,
            verticalPaddingDp = 16,
            cornerDecorSizeDp = 92,
            iconContainerSizeDp = 50,
            iconTopMarginDp = 4,
            titleTopMarginDp = 16,
            descriptionTopMarginDp = 8,
            badgeTopMarginDp = 12
        )

        val BottomSheet = StudyModeCardSpec(
            minHeightDp = 138,
            horizontalPaddingDp = 20,
            verticalPaddingDp = 20,
            cornerDecorSizeDp = 108,
            iconContainerSizeDp = 56,
            iconTopMarginDp = 8,
            titleTopMarginDp = 20,
            descriptionTopMarginDp = 10,
            badgeTopMarginDp = 16
        )
    }
}

fun bindStudyModeCard(
    binding: FeatureWordbookItemStudyModeCardBinding,
    uiModel: StudyModeUiModel,
    isSelected: Boolean,
    showDefaultBadge: Boolean,
    spec: StudyModeCardSpec,
    onClick: (() -> Unit)? = null
) {
    val context = binding.root.context
    binding.root.background = AppCompatResources.getDrawable(
        context,
        if (isSelected) {
            R.drawable.module_wordbook_bg_study_mode_card_selected
        } else {
            R.drawable.module_wordbook_bg_study_mode_card_normal
        }
    )
    binding.iconContainer.background = AppCompatResources.getDrawable(
        context,
        if (isSelected) {
            R.drawable.module_wordbook_bg_study_mode_icon_selected
        } else {
            R.drawable.module_wordbook_bg_study_mode_icon_normal
        }
    )
    applyCardSpec(binding, spec, context)
    binding.ivModeIcon.setImageResource(uiModel.iconRes)
    ImageViewCompat.setImageTintList(
        binding.ivModeIcon,
        ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (isSelected) android.R.color.white else R.color.module_wordbook_study_mode_icon_tint
            )
        )
    )
    binding.tvModeTitle.setText(uiModel.titleRes)
    binding.tvModeDescription.setText(uiModel.descriptionRes)
    binding.tvModeBadge.isVisible = showDefaultBadge
    if (onClick != null) {
        binding.root.setOnClickListener { onClick() }
    } else {
        binding.root.setOnClickListener(null)
    }
    binding.root.isClickable = onClick != null
    binding.root.isFocusable = onClick != null
}

private fun applyCardSpec(
    binding: FeatureWordbookItemStudyModeCardBinding,
    spec: StudyModeCardSpec,
    context: Context
) {
    binding.root.minimumHeight = spec.minHeightDp.dpToPx(context)
    binding.root.setPadding(
        spec.horizontalPaddingDp.dpToPx(context),
        spec.verticalPaddingDp.dpToPx(context),
        spec.horizontalPaddingDp.dpToPx(context),
        spec.verticalPaddingDp.dpToPx(context)
    )
    binding.vCornerDecor.updateSize(spec.cornerDecorSizeDp, context)
    binding.iconContainer.updateSize(spec.iconContainerSizeDp, context)
    binding.iconContainer.updateTopMargin(spec.iconTopMarginDp, context)
    binding.tvModeTitle.updateTopMargin(spec.titleTopMarginDp, context)
    binding.tvModeDescription.updateTopMargin(spec.descriptionTopMarginDp, context)
    binding.tvModeBadge.updateTopMargin(spec.badgeTopMarginDp, context)
}

private fun android.view.View.updateSize(sizeDp: Int, context: Context) {
    layoutParams = layoutParams.apply {
        width = sizeDp.dpToPx(context)
        height = sizeDp.dpToPx(context)
    }
}

private fun android.view.View.updateTopMargin(topMarginDp: Int, context: Context) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.topMargin = topMarginDp.dpToPx(context)
    layoutParams = params
}
