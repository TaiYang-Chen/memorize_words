package com.chen.memorizewords.feature.wordbook.favorites

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookFragmentFavoritesBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FavoritesFragment : BaseFragment<FavoritesViewModel, ModuleWordbookFragmentFavoritesBinding>() {

    override val viewModel: FavoritesViewModel by lazy {
        ViewModelProvider(this)[FavoritesViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        databind.recyclerView.adapter = adapter
        databind.recyclerView.addItemDecoration(
            DateGroupDecoration(adapter)
        )

        lifecycleScope.launch {
            viewModel.pagingData.collect {
                adapter.submitData(it)
            }
        }
    }

    private val adapter = FavoritesPagingAdapter()
}
