package com.chen.memorizewords.feature.wordbook.wordlist

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.domain.model.words.enums.WordFilter
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.FragmentWordListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@AndroidEntryPoint
class WordListFragment : BaseFragment<WordListViewModel, FragmentWordListBinding>() {

    override val viewModel: WordListViewModel by lazy {
        ViewModelProvider(this)[WordListViewModel::class.java]
    }

    private val adapter: WordPagingAdapter by lazy { WordPagingAdapter() }

    private val args: WordListFragmentArgs by navArgs()

    override fun initView(savedInstanceState: Bundle?) {
        databind.vm = viewModel

        initRecyclerView()
        observePagingData()

        viewModel.loadData(args.bookId)

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

        // 在 RecyclerView 滚动监听中
        databind.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val firstPos = (rv.layoutManager as LinearLayoutManager)
                    .findFirstVisibleItemPosition()
                if (firstPos == RecyclerView.NO_POSITION) return
                if (adapter.itemCount == 0) return

                val item = adapter.peek(firstPos) ?: return

                databind.fastIndexView.setCurrentLetter(item.groupChar)
            }
        })
    }

    override fun createObserver() {
        databind.radioGroupFilter.setOnCheckedChangeListener { _, checkedId ->
            val filter = when (checkedId) {
                R.id.btnAll -> WordFilter.ALL
                R.id.btnMastered -> WordFilter.MASTERED
                R.id.btnLearned -> WordFilter.LEARNED
                R.id.btnNotLearned -> WordFilter.TO_LEARN
                else -> WordFilter.ALL
            }
            viewModel.setFilter(filter)
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
    }

    private fun observePagingData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pagingData.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }
}
