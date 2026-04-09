package com.chen.memorizewords.feature.wordbook.my

import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateCandidate
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateJobState
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateUiState
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.custom.LinearSpacingItemDecoration
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookFragmentMyWordBooksBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyWordBooksFragment :
    BaseFragment<MyWordBooksViewModel, ModuleWordbookFragmentMyWordBooksBinding>() {

    override val viewModel: MyWordBooksViewModel by lazy {
        ViewModelProvider(this)[MyWordBooksViewModel::class.java]
    }

    private val adapter: MyWordBookAdapter = MyWordBookAdapter()
    private var suppressSwitchCallbacks = false

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@MyWordBooksFragment.adapter
            addItemDecoration(LinearSpacingItemDecoration(16.dpToPx(requireContext())).apply {
                lastRect.bottom = 100.dpToPx(requireContext())
                firstRect.top = 10.dpToPx(requireContext())
            })
            setHasFixedSize(true)
        }

        adapter.setOnItemClickListener {
            viewModel.onSetCurrentWordBook(it.bookId)
        }

        databind.radioGroupFilter.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.btnAll -> viewModel.setFilter("All")
                R.id.btnStudying -> viewModel.setFilter("Studying")
                R.id.btnCompleted -> viewModel.setFilter("Completed")
            }
        }

        databind.switchForegroundAlerts.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressSwitchCallbacks) viewModel.onForegroundAlertsChanged(isChecked)
        }
        databind.switchSilentUpdate.setOnCheckedChangeListener { _, isChecked ->
            if (!suppressSwitchCallbacks) viewModel.onSilentUpdateChanged(isChecked)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onPageStarted()
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.wordBookCardState.collect { list ->
                        adapter.submitList(list)
                    }
                }
                launch {
                    viewModel.updateUiState.collect { state ->
                        renderUpdateState(state)
                    }
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        val navController = findNavController()
        when (event.target) {
            MyWordBooksViewModel.Route.ToMyWordBooks -> {
                val actionId = R.id.action_studyPlan_to_myWordBooks
                if (navController.currentDestination?.getAction(actionId) != null) {
                    navController.navigate(actionId)
                }
            }

            MyWordBooksViewModel.Route.ToShop -> {
                navController.navigate(R.id.action_myWordBooks_to_shop)
            }
        }
    }

    private fun renderUpdateState(state: WordBookUpdateUiState) {
        val candidate = state.candidate
        databind.updateBannerCard.isVisible = candidate != null
        databind.updateDetailsPanel.isVisible = candidate != null && state.detailsVisible
        databind.updateSettingsPanel.isVisible = candidate != null && state.settingsVisible
        databind.btnToggleDetails.text = if (state.detailsVisible) "收起详情" else "查看更新详情"
        suppressSwitchCallbacks = true
        databind.switchForegroundAlerts.isChecked = state.settings.foregroundAlertsEnabled
        databind.switchSilentUpdate.isChecked = state.settings.silentUpdateEnabled
        suppressSwitchCallbacks = false
        if (candidate == null) {
            databind.updateStatusText.text = ""
            return
        }
        databind.updateBannerTitle.text = "${candidate.bookName} 有新版本"
        databind.updateBannerSummary.text =
            "新增 ${candidate.summary.addedCount} 个单词，修改 ${candidate.summary.modifiedCount} 个单词，删除 ${candidate.summary.removedCount} 个单词"
        databind.updateBannerMeta.text = buildMeta(candidate)
        databind.updateDetailsText.text = buildDetails(candidate)
        databind.updateStatusText.text = when (val job = state.jobState) {
            WordBookUpdateJobState.Idle -> buildDeferredText(state.deferredUntil)
            is WordBookUpdateJobState.Running -> "更新中 ${job.progress}%"
            is WordBookUpdateJobState.Succeeded -> "已更新到版本 ${job.targetVersion}"
            is WordBookUpdateJobState.Failed -> "更新失败：${job.message}"
        }
    }

    private fun buildMeta(candidate: WordBookUpdateCandidate): String {
        val sizeText = if (candidate.estimatedDownloadBytes > 0L) {
            Formatter.formatShortFileSize(requireContext(), candidate.estimatedDownloadBytes)
        } else {
            "未知大小"
        }
        val dateText = if (candidate.publishedAt > 0L) {
            DateFormat.format("yyyy-MM-dd HH:mm", candidate.publishedAt).toString()
        } else {
            "未知时间"
        }
        return "版本 ${candidate.currentVersion} → ${candidate.targetVersion} · $dateText · $sizeText"
    }

    private fun buildDetails(candidate: WordBookUpdateCandidate): String {
        val samples = candidate.summary.sampleWords.take(5).joinToString("、").ifBlank { "暂无示例单词" }
        return buildString {
            appendLine("新增 ${candidate.summary.addedCount} 个单词")
            appendLine("修改 ${candidate.summary.modifiedCount} 个单词")
            appendLine("删除 ${candidate.summary.removedCount} 个单词")
            append("示例单词：$samples")
        }
    }

    private fun buildDeferredText(deferredUntil: Long): String {
        if (deferredUntil <= System.currentTimeMillis()) return ""
        val dateText = DateFormat.format("MM-dd HH:mm", deferredUntil).toString()
        return "稍后提醒至 $dateText"
    }
}
