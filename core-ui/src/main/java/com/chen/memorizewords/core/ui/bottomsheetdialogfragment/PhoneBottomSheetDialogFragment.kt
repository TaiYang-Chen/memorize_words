package com.chen.memorizewords.core.ui.bottomsheetdialogfragment

import android.view.View
import com.chen.memorizewords.core.ui.insets.configurePhoneDialogForIme
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.roundToInt

/**
 * Base for bottom sheets that do not need a ViewModel-backed core-ui base class.
 */
open class PhoneBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private val sheetHeightPolicies = mutableMapOf<View, SheetHeightPolicy>()

    override fun onStart() {
        super.onStart()
        configurePhoneDialogForIme()
    }

    override fun onDestroyView() {
        sheetHeightPolicies.forEach { (sheet, policy) ->
            policy.host.removeOnLayoutChangeListener(policy.listener)
            sheet.removeOnLayoutChangeListener(policy.sheetListener)
            policy.restore()
        }
        sheetHeightPolicies.clear()
        super.onDestroyView()
    }

    /**
     * Sizes a sheet against its current dialog container rather than the physical display.
     * The policy is reapplied when IME resizing or system insets change the container height.
     */
    protected fun setPhoneBottomSheetHeightFraction(bottomSheet: View, fraction: Float) {
        installHeightPolicy(bottomSheet, fraction, capContentHeight = false)
    }

    /**
     * Keeps wrap-content sheets from exceeding a fraction of their current dialog container.
     */
    protected fun capPhoneBottomSheetHeight(bottomSheet: View, fraction: Float) {
        installHeightPolicy(bottomSheet, fraction, capContentHeight = true)
    }

    private fun installHeightPolicy(
        bottomSheet: View,
        fraction: Float,
        capContentHeight: Boolean
    ) {
        require(fraction > 0f && fraction <= 1f) {
            "Bottom-sheet height fraction must be in (0, 1]."
        }
        sheetHeightPolicies.remove(bottomSheet)?.let { previous ->
            previous.host.removeOnLayoutChangeListener(previous.listener)
            bottomSheet.removeOnLayoutChangeListener(previous.sheetListener)
            previous.restore()
        }

        val host = (bottomSheet.parent as? View) ?: bottomSheet.rootView
        val behavior = if (capContentHeight) BottomSheetBehavior.from(bottomSheet) else null
        val originalHeight = bottomSheet.layoutParams.height
        val originalMaxHeight = behavior?.maxHeight
        fun applyHeight() {
            val availableHeight = host.height
                .takeIf { it > 0 }
                ?: bottomSheet.rootView.height.takeIf { it > 0 }
                ?: return
            val targetHeight = (availableHeight * fraction).roundToInt()
            if (targetHeight <= 0) {
                return
            }
            if (capContentHeight) {
                // Let Material preserve wrap-content sizing and only enforce the current container cap.
                if (behavior?.maxHeight != targetHeight) {
                    behavior?.maxHeight = targetHeight
                    bottomSheet.requestLayout()
                }
            } else if (bottomSheet.layoutParams.height != targetHeight) {
                bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                    height = targetHeight
                }
            }
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyHeight()
        }
        val sheetListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyHeight()
        }
        val restore = {
            if (capContentHeight) {
                val cappedBehavior = checkNotNull(behavior)
                val restoredMaxHeight = checkNotNull(originalMaxHeight)
                if (cappedBehavior.maxHeight != restoredMaxHeight) {
                    cappedBehavior.maxHeight = restoredMaxHeight
                    bottomSheet.requestLayout()
                }
            } else if (bottomSheet.layoutParams.height != originalHeight) {
                bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                    height = originalHeight
                }
            }
        }
        sheetHeightPolicies[bottomSheet] = SheetHeightPolicy(host, listener, sheetListener, restore)
        host.addOnLayoutChangeListener(listener)
        bottomSheet.addOnLayoutChangeListener(sheetListener)
        bottomSheet.post { applyHeight() }
    }

    private data class SheetHeightPolicy(
        val host: View,
        val listener: View.OnLayoutChangeListener,
        val sheetListener: View.OnLayoutChangeListener,
        val restore: () -> Unit
    )
}
