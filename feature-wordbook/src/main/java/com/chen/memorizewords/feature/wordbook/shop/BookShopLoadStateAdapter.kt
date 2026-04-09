package com.chen.memorizewords.feature.wordbook.shop

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookItemLoadStateBinding

class BookShopLoadStateAdapter(
    private val onRetry: () -> Unit
) : LoadStateAdapter<BookShopLoadStateAdapter.LoadStateViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): LoadStateViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ModuleWordbookItemLoadStateBinding.inflate(inflater, parent, false)
        return LoadStateViewHolder(binding, onRetry)
    }

    override fun onBindViewHolder(holder: LoadStateViewHolder, loadState: LoadState) {
        holder.bind(loadState)
    }

    class LoadStateViewHolder(
        private val binding: ModuleWordbookItemLoadStateBinding,
        onRetry: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnRetry.setOnClickListener { onRetry() }
        }

        fun bind(loadState: LoadState) {
            binding.progressBar.isVisible = loadState is LoadState.Loading
            binding.btnRetry.isVisible = loadState is LoadState.Error
            binding.tvError.isVisible = loadState is LoadState.Error
            if (loadState is LoadState.Error) {
                binding.tvError.text = loadState.error.message ?: "加载失败"
            }
        }
    }
}
