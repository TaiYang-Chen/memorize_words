package com.chen.memorizewords.feature.home.ui.practice

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.chen.memorizewords.feature.home.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PracticeEntrySelectBottomSheet(
    private val onRandomSelected: (Int) -> Unit,
    private val onSelfSelected: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_practice_entry_select, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val countInput = view.findViewById<EditText>(R.id.et_random_count)
        view.findViewById<View>(R.id.btn_random).setOnClickListener {
            val count = countInput.text?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 20
            onRandomSelected(count)
            dismiss()
        }
        view.findViewById<View>(R.id.btn_self_select).setOnClickListener {
            onSelfSelected()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
    }
}
