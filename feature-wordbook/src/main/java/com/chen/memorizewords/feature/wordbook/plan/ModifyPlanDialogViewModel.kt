package com.chen.memorizewords.feature.wordbook.plan

import androidx.databinding.ObservableField
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.usecase.wordbook.SaveStudyCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel
class ModifyPlanDialogViewModel @Inject constructor(
    private val saveStudyCountUseCase: SaveStudyCountUseCase
) : BaseViewModel() {

    val dailyNewCount = ObservableField("15")
    val dailyReviewCount = ObservableField("30")

    fun decrementDailyNewCount() {
        val current = dailyNewCount.get()?.toIntOrNull() ?: 15
        if (current > 1) {
            dailyNewCount.set((current - 1).toString())
        }
    }

    fun incrementDailyNewCount() {
        val current = dailyNewCount.get()?.toIntOrNull() ?: 15
        if (current < 999) {
            dailyNewCount.set((current + 1).toString())
        }
    }


    fun decrementDailyReviewCount() {
        val current = dailyReviewCount.get()?.toIntOrNull() ?: 15
        if (current > 1) {
            dailyReviewCount.set((current - 1).toString())
        }
    }

    fun incrementDailyReviewCount() {
        val current = dailyReviewCount.get()?.toIntOrNull() ?: 15
        if (current < 999) {
            dailyReviewCount.set((current + 1).toString())
        }
    }


    fun confirmModify() {
        val newCountStr = dailyNewCount.get().orEmpty()
        val newCount = newCountStr.toIntOrNull()

        when {
            newCountStr.isBlank() -> {
                showToast("Please enter daily new-word count.")
                return
            }

            newCount == null -> {
                showToast("Please enter a valid number.")
                return
            }

            newCount < 1 -> {
                showToast("Daily new-word count must be >= 1.")
                return
            }

            newCount > 999 -> {
                showToast("Daily new-word count must be <= 999.")
                return
            }
        }

        val reviewCountStr = dailyReviewCount.get().orEmpty()
        val reviewCount = reviewCountStr.toIntOrNull()

        when {
            reviewCountStr.isBlank() -> {
                showToast("Please enter daily review-word count.")
                return
            }

            reviewCount == null -> {
                showToast("Please enter a valid number.")
                return
            }

            reviewCount < 1 -> {
                showToast("Daily review-word count must be >= 1.")
                return
            }

            reviewCount > 999 -> {
                showToast("Daily review-word count must be <= 999.")
                return
            }
        }

        saveSettings(newCount, reviewCount)
    }

    private fun saveSettings(
        newCount: Int,
        reviewCount: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            saveStudyCountUseCase(newCount, reviewCount)
            emitEvent(UiEvent.Toast("Study plan updated."))
            emitEvent(UiEvent.Navigation.Back)
        }
    }

    fun loadPlan(
        newCount: Int,
        reviewCount: Int
    ) {
        dailyNewCount.set(newCount.toString())
        dailyReviewCount.set(reviewCount.toString())
    }
}
