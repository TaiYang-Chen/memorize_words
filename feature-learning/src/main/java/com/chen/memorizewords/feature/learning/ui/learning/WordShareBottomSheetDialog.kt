package com.chen.memorizewords.feature.learning.ui.learning

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.bottomsheetdialogfragment.BaseBottomSheetDialogFragment
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FeatureLearningDialogWordShareBinding

class WordShareBottomSheetDialog(
    private val actions: List<LearningShareActionItem>,
    private val onActionClicked: (LearningShareAction) -> Unit
) : BaseBottomSheetDialogFragment<BaseViewModel, FeatureLearningDialogWordShareBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.tvTitle.text = getString(R.string.learning_share_sheet_title)
        databind.actionContainer.removeAllViews()
        actions.forEach { action ->
            databind.actionContainer.addView(createActionView(action))
        }
        databind.imgCancel.setOnClickListener { dismiss() }
    }

    private fun createActionView(actionItem: LearningShareActionItem): View {
        val actionView = LayoutInflater.from(requireContext()).inflate(
            R.layout.feature_learning_item_word_share_action,
            databind.actionContainer,
            false
        )
        actionView.findViewById<TextView>(R.id.tvAction).apply {
            text = actionItem.title
            setOnClickListener {
                dismiss()
                onActionClicked(actionItem.action)
            }
        }
        return actionView
    }

    companion object {
        const val TAG = "WordShareBottomSheetDialog"
    }
}
