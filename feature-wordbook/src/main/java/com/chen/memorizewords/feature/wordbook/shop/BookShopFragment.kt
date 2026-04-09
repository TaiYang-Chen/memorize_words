package com.chen.memorizewords.feature.wordbook.shop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.chen.memorizewords.domain.model.wordbook.shop.DownloadState
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookFragmentBookShopBinding
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BookShopFragment :
    BaseFragment<BookShopViewModel, ModuleWordbookFragmentBookShopBinding>() {

    override val viewModel: BookShopViewModel by lazy {
        ViewModelProvider(this)[BookShopViewModel::class.java]
    }

    private var pendingDownloadActionItem: BookShopUi? = null

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
                    is DownloadState.UpdateAvailable,
                    is DownloadState.Failed,
                    is DownloadState.Paused -> {
                        requestNotificationPermissionIfNeeded(item)
                    }

                    is DownloadState.Downloading -> {
                        viewModel.onCancelDownload(item.book.id)
                    }

                    is DownloadState.Downloaded -> {
                        viewModel.showToast(getString(R.string.module_wordbook_downloaded))
                    }
                }
            }
        )
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        setupRecyclerView()
        setupTabs()
        setupSearch()
        setupSwipeRefresh()
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
        val categories = listOf(
            "全部",
            "大学",
            "高中",
            "初中"
        )
        categories.forEach { category ->
            databind.tabLayout.addTab(
                databind.tabLayout.newTab().setText(category).setTag(category)
            )
        }

        databind.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setCategory(tab.tag as String)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupSearch() {
        databind.etSearch.doAfterTextChanged { text ->
            viewModel.setKeyword(text?.toString().orEmpty())
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

    private fun observePaging() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pagingData.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }

    private fun observeLoadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                databind.swipeRefresh.isRefreshing = loadStates.refresh is androidx.paging.LoadState.Loading
                val isError = loadStates.refresh is androidx.paging.LoadState.Error
                val isEmpty =
                    loadStates.refresh is androidx.paging.LoadState.NotLoading && adapter.itemCount == 0

                databind.stateGroup.isVisible = isError || isEmpty
                databind.btnStateAction.isVisible = isError

                if (isError) {
                    val error = (loadStates.refresh as? androidx.paging.LoadState.Error)?.error
                    databind.tvStateMessage.text =
                        error?.message ?: getString(R.string.module_wordbook_network_error)
                } else if (isEmpty) {
                    databind.tvStateMessage.text = getString(R.string.module_wordbook_empty_books)
                }
            }
        }
    }
}
