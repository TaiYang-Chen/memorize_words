package com.chen.memorizewords.feature.wordbook.wordlist

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.core.navigation.LearningEntry
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.domain.word.model.enums.WordFilter
import com.chen.memorizewords.domain.word.model.enums.WordSortType
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.FragmentWordListBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WordListFragment : BaseFragment<WordListViewModel, FragmentWordListBinding>() {

    override val viewModel: WordListViewModel by lazy {
        ViewModelProvider(this)[WordListViewModel::class.java]
    }

    @Inject lateinit var learningEntry: LearningEntry

    private val adapter: WordPagingAdapter by lazy {
        WordPagingAdapter(
            onWordClick = viewModel::openWord,
            onFavoriteClick = viewModel::toggleFavorite,
            onSpeakClick = viewModel::playPronunciation
        )
    }

    private val args: WordListFragmentArgs by navArgs()
    private var applyingSearchText = false

    override fun initView(savedInstanceState: Bundle?) {
        databind.vm = viewModel
        databind.lifecycleOwner = viewLifecycleOwner

        initRecyclerView()
        bindSearch()
        bindFilters()
        bindActions()
        observePagingData()
        observeUiState()
        bindFastIndex()

        viewModel.loadData(args.bookId)
    }

    override fun createObserver() = Unit

    override fun onUiEffect(effect: UiEffect) {
        when (effect) {
            is WordListEffect.OpenWord -> {
                startActivity(learningEntry.createOpenWordIntent(requireContext(), effect.wordId, false))
            }
            WordListEffect.RefreshList -> adapter.refresh()
            else -> super.onUiEffect(effect)
        }
    }

    private fun initRecyclerView() {
        databind.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        databind.recyclerView.adapter = adapter
        databind.recyclerView.addItemDecoration(
            GroupCardDecoration(requireContext(), adapter)
        )
        databind.recyclerView.addItemDecoration(
            StickyGroupHeaderDecoration(requireContext(), adapter)
        )
        adapter.addLoadStateListener { states ->
            val empty = states.refresh is LoadState.NotLoading && adapter.itemCount == 0
            databind.layoutEmpty.isVisible = empty
            databind.recyclerView.isVisible = !empty
            if (empty) renderEmptyState(viewModel.uiState.value)
        }
    }

    private fun bindSearch() {
        databind.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (applyingSearchText) return
                viewModel.setKeyword(s?.toString().orEmpty())
            }
        })
        databind.btnClearSearch.setOnClickListener {
            viewModel.clearKeyword()
        }
    }

    private fun bindFilters() {
        filterViews().forEach { (view, filter) ->
            view.setOnClickListener { viewModel.setFilter(filter) }
        }
    }

    private fun bindActions() {
        databind.btnSort.setOnClickListener { showSortSheet() }
    }

    private fun observePagingData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pagingData.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderUiState(state)
            }
        }
    }

    private fun renderUiState(state: WordListUiState) {
        databind.tvTitle.text = state.bookName
        databind.tvSummary.text = state.summaryText
        databind.tvSortLabel.text = state.selectedSortType.displayName()
        databind.fastIndexView.isVisible = state.showFastIndex
        databind.btnClearSearch.isVisible = state.keyword.isNotBlank()
        renderFilters(state.selectedFilter)
        renderEmptyState(state)

        val currentSearch = databind.editSearch.text?.toString().orEmpty()
        if (currentSearch != state.keyword) {
            applyingSearchText = true
            databind.editSearch.setText(state.keyword)
            databind.editSearch.setSelection(state.keyword.length)
            applyingSearchText = false
        }
    }

    private fun renderFilters(selected: WordFilter) {
        filterViews().forEach { (view, filter) ->
            val isSelected = filter == selected
            view.isSelected = isSelected
            view.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) {
                        R.color.feature_wordbook_word_list_primary
                    } else {
                        R.color.feature_wordbook_word_text_secondary
                    }
                )
            )
        }
    }

    private fun renderEmptyState(state: WordListUiState) {
        val (title, subtitle) = when {
            state.keyword.isNotBlank() -> "未找到相关单词" to "换个单词或释义关键词试试"
            state.selectedFilter == WordFilter.FAVORITE -> "暂无重点词" to "点击词条右侧星标即可加入重点词"
            state.selectedFilter == WordFilter.REVIEW_DUE -> "暂无待复习" to "完成新词学习后，系统会按记忆曲线安排复习"
            state.summary.totalCount == 0 -> "词书为空" to "当前词书还没有可浏览的单词"
            else -> "暂无单词" to "换个筛选条件试试"
        }
        databind.tvEmptyTitle.text = title
        databind.tvEmptySubtitle.text = subtitle
    }

    private fun bindFastIndex() {
        databind.fastIndexView.onLetterChanged = { letter ->
            if (adapter.itemCount != 0) {
                val lm = databind.recyclerView.layoutManager as LinearLayoutManager
                for (i in 0 until adapter.itemCount) {
                    val item = adapter.peek(i) ?: continue
                    if (item.groupChar == letter) {
                        lm.scrollToPositionWithOffset(i, 0)
                        break
                    }
                }
            }
        }

        databind.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val firstPos = (rv.layoutManager as LinearLayoutManager)
                    .findFirstVisibleItemPosition()
                if (firstPos == RecyclerView.NO_POSITION || adapter.itemCount == 0) return
                val item = adapter.peek(firstPos) ?: return
                databind.fastIndexView.setCurrentLetter(item.groupChar)
            }
        })
    }

    private fun showSortSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }
        container.addView(
            TextView(requireContext()).apply {
                text = "排序方式"
                textSize = 18f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.feature_wordbook_word_text_primary))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, dp(8))
            }
        )
        WordSortType.entries.forEach { sortType ->
            container.addView(sortRow(sortType, sortType == viewModel.uiState.value.selectedSortType) {
                viewModel.setSortType(sortType)
                dialog.dismiss()
            })
        }
        dialog.setContentView(container)
        dialog.show()
    }

    private fun sortRow(sortType: WordSortType, selected: Boolean, onClick: () -> Unit): View {
        return TextView(requireContext()).apply {
            text = if (selected) "${sortType.displayName()}  ✓" else sortType.displayName()
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(48)
            textSize = 15f
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.feature_wordbook_word_list_primary else R.color.feature_wordbook_word_text_primary
                )
            )
            setOnClickListener { onClick() }
        }
    }

    private fun filterViews(): List<Pair<TextView, WordFilter>> {
        return listOf(
            databind.chipAll to WordFilter.ALL,
            databind.chipToLearn to WordFilter.TO_LEARN,
            databind.chipLearned to WordFilter.LEARNED,
            databind.chipMastered to WordFilter.MASTERED,
            databind.chipReview to WordFilter.REVIEW_DUE,
            databind.chipFavorite to WordFilter.FAVORITE
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

private fun WordSortType.displayName(): String {
    return when (this) {
        WordSortType.BOOK_ORDER -> "默认词书顺序"
        WordSortType.ALPHABETIC_ASC -> "A-Z"
        WordSortType.ALPHABETIC_DESC -> "Z-A"
        WordSortType.RECENT_LEARNED -> "最近学习优先"
        WordSortType.REVIEW_DUE_FIRST -> "待复习优先"
    }
}
