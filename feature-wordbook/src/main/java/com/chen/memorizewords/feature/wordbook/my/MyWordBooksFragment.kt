package com.chen.memorizewords.feature.wordbook.my

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
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

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MyWordBooksFragment.adapter
            val resources = requireContext().resources
            addItemDecoration(
                LinearSpacingItemDecoration(
                    resources.getDimensionPixelSize(R.dimen.feature_wordbook_my_books_list_item_spacing)
                ).apply {
                    lastRect.bottom =
                        resources.getDimensionPixelSize(R.dimen.feature_wordbook_my_books_list_bottom_spacing)
                    firstRect.top =
                        resources.getDimensionPixelSize(R.dimen.feature_wordbook_my_books_list_top_spacing)
                }
            )
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
}
