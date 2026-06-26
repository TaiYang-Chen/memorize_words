package com.chen.memorizewords.feature.wordbook.shop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookFragmentBookShopBinding
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BookShopFragment :
    BaseFragment<BookShopViewModel, ModuleWordbookFragmentBookShopBinding>() {

    override val viewModel: BookShopViewModel by lazy {
        ViewModelProvider(this)[BookShopViewModel::class.java]
    }

    private var pendingDownloadActionItem: BookShopUi? = null

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            cancelSearchFromUi()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingItem = pendingDownloadActionItem
        pendingDownloadActionItem = null
        if (!granted) {
            viewModel.showToast(getString(R.string.module_wordbook_notify_permission_denied))
        }
        pendingItem?.let(viewModel::onDownload)
    }

    private val adapter: BookShopAdapter by lazy {
        BookShopAdapter(
            onActionClick = { item ->
                when (item.downloadState) {
                    is DownloadState.NotDownloaded,
                    is DownloadState.Failed,
                    is DownloadState.Paused -> {
                        requestNotificationPermissionIfNeeded(item)
                    }

                    is DownloadState.Downloading -> {
                        if (item.downloadState.progress <= 0) {
                            requestNotificationPermissionIfNeeded(item)
                        } else {
                            viewModel.onCancelDownload(item.book.id)
                        }
                    }

                    is DownloadState.Downloaded -> {
                        viewModel.showToast(getString(R.string.module_wordbook_downloaded))
                    }

                    is DownloadState.UpdateAvailable -> {
                        viewModel.showToast(getString(R.string.module_wordbook_downloaded))
                    }
                }
            }
        )
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
        setupRecyclerView()
        setupTabs()
        setupSearch()
        setupSwipeRefresh()
        observeSearchMode()
        observePaging()
        observeLoadState()
    }

    private fun requestNotificationPermissionIfNeeded(item: BookShopUi) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            viewModel.onDownload(item)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onDownload(item)
            return
        }
        pendingDownloadActionItem = item
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun setupRecyclerView() {
        databind.rvBooks.layoutManager = LinearLayoutManager(requireContext())
        databind.rvBooks.adapter = adapter.withLoadStateFooter(
            footer = BookShopLoadStateAdapter { adapter.retry() }
        )
        (databind.rvBooks.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        databind.rvBooks.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    databind.etSearch.clearFocus()
                }
            }
        })
    }

    private fun setupTabs() {
        BOOK_SHOP_CATEGORIES.forEachIndexed { index, category ->
            val tab = databind.tabLayout.newTab()
                .setTag(category)
                .setCustomView(createTabView(category))
            databind.tabLayout.addTab(tab, index == 0)
            updateTabStyle(tab, index == 0)
        }

        databind.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateTabStyle(tab, true)
                viewModel.setCategory(tab.tag as? String ?: DEFAULT_CATEGORY)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                updateTabStyle(tab, false)
            }

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun createTabView(category: String): TextView {
        val tabView = LayoutInflater.from(databind.tabLayout.context)
            .inflate(R.layout.feature_wordbook_item_tab_text, databind.tabLayout, false) as TextView
        tabView.text = category
        return tabView
    }

    private fun updateTabStyle(tab: TabLayout.Tab, isSelected: Boolean) {
        (tab.customView as? TextView)?.apply {
            setTextColor(
                requireContext().getColor(
                    if (isSelected) {
                        R.color.feature_wordbook_shop_tab_text_selected
                    } else {
                        R.color.feature_wordbook_shop_tab_text_unselected
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
        databind.etSearch.doAfterTextChanged { text ->
            viewModel.setKeyword(text?.toString().orEmpty())
        }
        databind.btnCancel.setOnClickListener {
            cancelSearchFromUi()
        }
    }

    private fun setupSwipeRefresh() {
        databind.swipeRefresh.setOnRefreshListener {
            adapter.refresh()
        }
        databind.btnStateAction.setOnClickListener {
            adapter.retry()
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
            viewModel.pagingData.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
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
                val showList = !showStateOverlay && (adapter.itemCount > 0 || isLoading)

                databind.swipeRefresh.isRefreshing = isLoading
                databind.swipeRefresh.isEnabled = !isBlankSearchPrompt
                databind.rvBooks.isVisible = showList
                databind.stateGroup.isVisible = showStateOverlay
                databind.btnStateAction.isVisible = isError
                databind.tvStateMessage.text = when {
                    isBlankSearchPrompt -> {
                        getString(R.string.module_wordbook_shop_search_empty_keyword)
                    }

                    isError -> {
                        val error = (loadStates.refresh as? androidx.paging.LoadState.Error)?.error
                        error?.message ?: getString(R.string.module_wordbook_network_error)
                    }

                    showSearchNoResults -> {
                        getString(R.string.module_wordbook_shop_search_no_results)
                    }

                    showDefaultEmpty -> {
                        getString(R.string.module_wordbook_empty_books)
                    }

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
        databind.btnCancel.isVisible = isSearchMode
        databind.tabLayout.isVisible = !isSearchMode
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

private const val DEFAULT_CATEGORY = "\u5168\u90E8"

private val BOOK_SHOP_CATEGORIES = listOf(
    DEFAULT_CATEGORY,
    "\u5927\u5B66",
    "\u9AD8\u4E2D",
    "\u521D\u4E2D"
)
