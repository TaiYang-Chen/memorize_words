package com.chen.memorizewords.feature.home.ui.home

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.chen.memorizewords.feature.home.R

class BoostNewWordsDialogFragment : DialogFragment(R.layout.dialog_home_boost_new_words) {

    companion object {
        const val TAG = "BoostNewWordsDialogFragment"
        const val REQUEST_KEY = "boost_new_words_request"
        const val RESULT_KEY_AMOUNT = "result_amount"

        private const val ARG_DEFAULT_AMOUNT = "arg_default_amount"
        private const val STATE_SELECTED_AMOUNT = "state_selected_amount"
        private val ALLOWED_AMOUNTS = setOf(5, 10, 20)

        fun newInstance(defaultAmount: Int): BoostNewWordsDialogFragment {
            return BoostNewWordsDialogFragment().apply {
                arguments = bundleOf(ARG_DEFAULT_AMOUNT to sanitizeAmount(defaultAmount))
            }
        }

        private fun sanitizeAmount(amount: Int): Int {
            return if (ALLOWED_AMOUNTS.contains(amount)) amount else 5
        }
    }

    private var selectedAmount: Int = 5

    private lateinit var optionFive: TextView
    private lateinit var optionTen: TextView
    private lateinit var optionTwenty: TextView
    private lateinit var confirmButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val defaultAmount = arguments?.getInt(ARG_DEFAULT_AMOUNT, 5) ?: 5
        selectedAmount = sanitizeAmount(savedInstanceState?.getInt(STATE_SELECTED_AMOUNT, defaultAmount) ?: defaultAmount)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        optionFive = view.findViewById(R.id.tvOptionFive)
        optionTen = view.findViewById(R.id.tvOptionTen)
        optionTwenty = view.findViewById(R.id.tvOptionTwenty)
        confirmButton = view.findViewById(R.id.tvConfirmBoost)

        optionFive.setOnClickListener { updateSelection(5) }
        optionTen.setOnClickListener { updateSelection(10) }
        optionTwenty.setOnClickListener { updateSelection(20) }

        view.findViewById<TextView>(R.id.tvCancel).setOnClickListener { dismiss() }
        confirmButton.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_KEY_AMOUNT to selectedAmount)
            )
            dismiss()
        }

        updateSelection(selectedAmount)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_AMOUNT, selectedAmount)
        super.onSaveInstanceState(outState)
    }

    private fun updateSelection(amount: Int) {
        selectedAmount = sanitizeAmount(amount)
        renderOptionState(optionFive, selectedAmount == 5)
        renderOptionState(optionTen, selectedAmount == 10)
        renderOptionState(optionTwenty, selectedAmount == 20)
        confirmButton.text = "\u52A0\u91CF\u65B0\u5B66 (+$selectedAmount)"
    }

    private fun renderOptionState(optionView: TextView, selected: Boolean) {
        val context = optionView.context
        if (selected) {
            optionView.setBackgroundResource(R.drawable.feature_home_select_radius_black)
            optionView.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        } else {
            optionView.setBackgroundResource(R.drawable.feature_home_select_border_gray)
            optionView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
    }
}
