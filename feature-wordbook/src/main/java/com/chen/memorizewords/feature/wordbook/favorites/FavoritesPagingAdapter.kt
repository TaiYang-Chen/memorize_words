package com.chen.memorizewords.feature.wordbook.favorites

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookFragmentFavoritesItemBinding

class FavoritesPagingAdapter :
    PagingDataAdapter<FavoritesWord, FavoritesPagingAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<FavoritesWord>() {
            override fun areItemsTheSame(old: FavoritesWord, new: FavoritesWord) =
                old.word == new.word && old.date == new.date

            override fun areContentsTheSame(old: FavoritesWord, new: FavoritesWord) =
                old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ModuleWordbookFragmentFavoritesItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    fun getDateSafely(position: Int): String? {
        if (position < 0 || position >= itemCount) return null
        return peek(position)?.date
    }

    class VH(private val binding: ModuleWordbookFragmentFavoritesItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FavoritesWord) {
            binding.tvWord.text = item.word
            binding.tvMeaning.text = "${item.partOfSpeech} ${item.meaning}"
        }
    }
}