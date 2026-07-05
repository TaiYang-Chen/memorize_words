package com.chen.memorizewords.feature.wordbook.create

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.FeatureWordbookFragmentCreateWordBookBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateWordBookFragment :
    BaseFragment<CreateWordBookViewModel, FeatureWordbookFragmentCreateWordBookBinding>() {

    override val viewModel: CreateWordBookViewModel by lazy {
        ViewModelProvider(this)[CreateWordBookViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner

        databind.editTitle.afterTextChanged(viewModel::onTitleChanged)
        databind.editCategory.afterTextChanged(viewModel::onCategoryChanged)
        databind.editDescription.afterTextChanged(viewModel::onDescriptionChanged)
        databind.editWords.afterTextChanged(viewModel::onWordsChanged)
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (event.target) {
            CreateWordBookViewModel.Route.Created -> {
                findNavController().popBackStack()
            }
        }
    }

    private fun render(state: CreateWordBookUiState) {
        if (databind.editWords.text?.toString() != state.wordsText) {
            databind.editWords.setText(state.wordsText)
            databind.editWords.setSelection(state.wordsText.length)
        }
        databind.textStats.text = getString(
            R.string.feature_wordbook_create_words_stats,
            state.wordStats.validWordCount,
            state.wordStats.duplicateLineCount,
            state.wordStats.blankLineCount
        )
        databind.textError.text = state.errorMessage.orEmpty()
        databind.textError.isVisible = !state.errorMessage.isNullOrBlank()
        databind.btnSubmit.isEnabled = !state.isSubmitting
        databind.btnNormalize.isEnabled = !state.isSubmitting
        databind.btnClear.isEnabled = !state.isSubmitting
        databind.btnSubmit.text = getString(
            if (state.isSubmitting) {
                R.string.feature_wordbook_create_submitting
            } else {
                R.string.feature_wordbook_create_submit
            }
        )
    }
}

private fun android.widget.TextView.afterTextChanged(onChanged: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            onChanged(s?.toString().orEmpty())
        }
    })
}
