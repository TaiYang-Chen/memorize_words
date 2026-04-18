package com.chen.memorizewords.feature.onboarding

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.onboarding.databinding.FragmentOnboardingSelectWordBookBinding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OnboardingSelectWordBookFragment :
    BaseFragment<OnboardingSelectWordBookViewModel, FragmentOnboardingSelectWordBookBinding>() {

    override val viewModel: OnboardingSelectWordBookViewModel by lazy {
        ViewModelProvider(this)[OnboardingSelectWordBookViewModel::class.java]
    }

    private val activityViewModel: OnboardingViewModel by lazy {
        ViewModelProvider(requireActivity())[OnboardingViewModel::class.java]
    }

    private val adapter by lazy(LazyThreadSafetyMode.NONE) {
        OnboardingSelectWordBookAdapter(
            onSelectClick = activityViewModel::selectWordBook
        )
    }

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            cancelSearchFromUi()
        }
    }

    private val searchModeInterpolator = PathInterpolator(0f, 0f, 0.2f, 1f)
    private val cancelButtonOffsetPx by lazy(LazyThreadSafetyMode.NONE) {
        8f * resources.displayMetrics.density
    }
    private val normalSearchTopMarginPx by lazy(LazyThreadSafetyMode.NONE) {
        resources.getDimensionPixelSize(R.dimen.feature_onboarding_body_margin_top)
    }
    private val searchModeSearchTopMarginPx by lazy(LazyThreadSafetyMode.NONE) {
        normalSearchTopMarginPx
    }

    private var currentSearchMode: Boolean? = null

    override fun initView(savedInstanceState: Bundle?) {
        databind.lifecycleOwner = viewLifecycleOwner
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
        setupRecyclerView()
        setupTabs()
        setupSearch()
        setupSelectionActions()
        databind.btnRetryList.setOnClickListener { adapter.retry() }
        observeSearchMode()
        observePendingSelection()
        observePaging()
        observeLoadState()
    }

    private fun setupRecyclerView() {
        databind.rvBooks.layoutManager = LinearLayoutManager(requireContext())
        databind.rvBooks.adapter = adapter
    }

    private fun setupTabs() {
        ONBOARDING_WORD_BOOK_CATEGORIES.forEachIndexed { index, category ->
            val tab = databind.tabLayout.newTab()
                .setTag(category)
                .setCustomView(createTabView(category))
            databind.tabLayout.addTab(tab, index == 0)
            updateTabStyle(tab, index == 0)
        }

        databind.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateTabStyle(tab, true)
                viewModel.setCategory(
                    tab.tag as? String ?: DEFAULT_ONBOARDING_WORD_BOOK_CATEGORY
                )
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                updateTabStyle(tab, false)
            }

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun createTabView(category: String): TextView {
        val tabView = LayoutInflater.from(databind.tabLayout.context)
            .inflate(R.layout.feature_onboarding_item_tab_text, databind.tabLayout, false) as TextView
        tabView.text = category
        return tabView
    }

    private fun updateTabStyle(tab: TabLayout.Tab, isSelected: Boolean) {
        (tab.customView as? TextView)?.apply {
            setTextColor(
                requireContext().getColor(
                    if (isSelected) {
                        R.color.feature_onboarding_tab_text_selected
                    } else {
                        R.color.feature_onboarding_tab_text_unselected
                    }
                )
            )
            typeface = Typeface.create(
                if (isSelected) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL
            )
        }
    }

    private fun setupSearch() {
        databind.searchLayout.setOnClickListener {
            enterSearchModeFromUi(requestFocus = true)
        }
        databind.etSearch.setOnClickListener {
            enterSearchModeFromUi()
        }
        databind.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.enterSearchMode()
            }
        }
        databind.etSearch.doAfterTextChanged { value ->
            viewModel.setKeyword(value?.toString().orEmpty())
        }
        databind.btnCancel.setOnClickListener {
            cancelSearchFromUi()
        }
        databind.swipeRefresh.setOnRefreshListener {
            adapter.refresh()
        }
    }

    private fun setupSelectionActions() {
        databind.btnNextStep.setOnClickListener {
            activityViewModel.confirmSelectedWordBook()
        }
    }

    private fun observePendingSelection() {
        viewLifecycleOwner.lifecycleScope.launch {
            activityViewModel.pendingSelectedWordBook.collectLatest { wordBook ->
                adapter.updateSelectedBookId(wordBook?.id)
                databind.btnNextStep.isEnabled = wordBook != null
            }
        }
    }

    private fun observeSearchMode() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentSearchState.collectLatest { searchState ->
                backPressedCallback.isEnabled = searchState.isSearchMode
                renderSearchMode(searchState.isSearchMode)
            }
        }
    }

    private fun observePaging() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pagingData.collectLatest(adapter::submitData)
        }
    }

    private fun observeLoadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(adapter.loadStateFlow, viewModel.currentSearchState) { loadStates, searchState ->
                loadStates to searchState
            }.collectLatest { (loadStates, searchState) ->
                val trimmedKeyword = searchState.keyword.trim()
                val isBlankSearchPrompt = searchState.isSearchMode && trimmedKeyword.isBlank()
                val isLoading =
                    loadStates.refresh is androidx.paging.LoadState.Loading && !isBlankSearchPrompt
                val isError =
                    loadStates.refresh is androidx.paging.LoadState.Error && !isBlankSearchPrompt
                val isEmpty =
                    loadStates.refresh is androidx.paging.LoadState.NotLoading && adapter.itemCount == 0
                val showSearchNoResults =
                    searchState.isSearchMode && trimmedKeyword.isNotBlank() && isEmpty && !isError
                val showDefaultEmpty = !searchState.isSearchMode && isEmpty && !isError
                val showStateOverlay =
                    isError || isBlankSearchPrompt || showSearchNoResults || showDefaultEmpty
                val showList =
                    !showStateOverlay && (adapter.itemCount > 0 || isLoading)

                databind.swipeRefresh.isRefreshing = isLoading
                databind.swipeRefresh.isEnabled = !isBlankSearchPrompt
                databind.rvBooks.isVisible = showList
                databind.progressBar.isVisible = isLoading && adapter.itemCount == 0
                databind.emptyStateGroup.isVisible = showStateOverlay
                databind.btnRetryList.isVisible = isError
                databind.tvStateMessage.text = when {
                    isBlankSearchPrompt -> {
                        getString(R.string.feature_onboarding_search_empty_keyword)
                    }

                    isError -> {
                        (loadStates.refresh as? androidx.paging.LoadState.Error)?.error?.message
                            ?: getString(R.string.feature_onboarding_load_books_failed)
                    }

                    showSearchNoResults -> {
                        getString(R.string.feature_onboarding_search_no_results)
                    }

                    showDefaultEmpty -> getString(R.string.feature_onboarding_empty_books)
                    else -> ""
                }
            }
        }
    }

    private fun enterSearchModeFromUi(requestFocus: Boolean = false) {
        viewModel.enterSearchMode()
        if (requestFocus) {
            databind.etSearch.requestFocus()
            showKeyboard(databind.etSearch)
        }
    }

    private fun cancelSearchFromUi() {
        databind.etSearch.setText("")
        databind.etSearch.clearFocus()
        hideKeyboard(databind.etSearch)
        viewModel.cancelSearch()
    }

    private fun renderSearchMode(isSearchMode: Boolean) {
        val previousState = currentSearchMode
        if (previousState == isSearchMode) return

        databind.btnCancel.animate().cancel()
        if (previousState == null) {
            applySearchModeLayout(isSearchMode, animateAppBar = false)
            databind.btnCancel.isVisible = isSearchMode
            databind.btnCancel.alpha = if (isSearchMode) 1f else 0f
            databind.btnCancel.translationX = if (isSearchMode) 0f else cancelButtonOffsetPx
            currentSearchMode = isSearchMode
            return
        }

        if (isSearchMode) {
            databind.btnCancel.isVisible = true
            databind.btnCancel.alpha = 0f
            databind.btnCancel.translationX = cancelButtonOffsetPx
        } else {
            databind.btnCancel.isVisible = true
            databind.btnCancel.alpha = 1f
            databind.btnCancel.translationX = 0f
        }

        TransitionManager.beginDelayedTransition(
            databind.contentCoordinator,
            createSearchModeTransition()
        )
        applySearchModeLayout(isSearchMode, animateAppBar = true)
        animateCancelButton(isSearchMode)
        currentSearchMode = isSearchMode
    }

    private fun applySearchModeLayout(isSearchMode: Boolean, animateAppBar: Boolean) {
        databind.heroContentLayout.root.isVisible = !isSearchMode
        databind.tabLayout.isVisible = !isSearchMode
        databind.searchHeaderContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = if (isSearchMode) {
                searchModeSearchTopMarginPx
            } else {
                normalSearchTopMarginPx
            }
        }
        updateAppBarScrollFlags(isSearchMode)
        databind.onboardingAppBar.setExpanded(true, animateAppBar)
    }

    private fun updateAppBarScrollFlags(isSearchMode: Boolean) {
        val layoutParams =
            databind.collapsibleHeaderContent.layoutParams as AppBarLayout.LayoutParams
        val expectedFlags = if (isSearchMode) {
            0
        } else {
            APP_BAR_SCROLL_FLAGS
        }
        if (layoutParams.scrollFlags == expectedFlags) return
        layoutParams.scrollFlags = expectedFlags
        databind.collapsibleHeaderContent.layoutParams = layoutParams
    }

    private fun animateCancelButton(isSearchMode: Boolean) {
        databind.btnCancel.animate()
            .alpha(if (isSearchMode) 1f else 0f)
            .translationX(if (isSearchMode) 0f else cancelButtonOffsetPx)
            .setDuration(SEARCH_MODE_ANIMATION_DURATION_MS)
            .setInterpolator(searchModeInterpolator)
            .withEndAction {
                if (!isSearchMode) {
                    databind.btnCancel.isVisible = false
                }
            }
            .start()
    }

    private fun createSearchModeTransition(): TransitionSet {
        return TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            duration = SEARCH_MODE_ANIMATION_DURATION_MS
            interpolator = searchModeInterpolator
            addTransition(ChangeBounds())
            addTransition(
                Fade().apply {
                    excludeTarget(databind.btnCancel, true)
                }
            )
        }
    }

    private fun showKeyboard(view: View) {
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        view.post {
            inputMethodManager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

private const val SEARCH_MODE_ANIMATION_DURATION_MS = 240L
private const val APP_BAR_SCROLL_FLAGS =
    AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
