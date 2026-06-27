package com.chen.memorizewords.feature.wordbook.favorites

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookFragmentFavoritesItemBinding

class FavoritesPagingAdapter(
    private val onItemClick: (FavoritesWord) -> Unit,
    private val onItemLongClick: (FavoritesWord) -> Boolean
) :
    PagingDataAdapter<FavoritesWord, FavoritesPagingAdapter.VH>(DIFF) {

    private var selectedIds: Set<Long> = emptySet()
    private var selectionMode: Boolean = false

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<FavoritesWord>() {
            override fun areItemsTheSame(old: FavoritesWord, new: FavoritesWord) =
                old.wordId == new.wordId

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
        getItem(position)?.let { item ->
            holder.bind(
                item = item,
                selectionMode = selectionMode,
                selected = item.wordId in selectedIds,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick
            )
        }
    }

    fun getDateSafely(position: Int): String? {
        if (position < 0 || position >= itemCount) return null
        return peek(position)?.date
    }

    fun getItemSafely(position: Int): FavoritesWord? {
        if (position < 0 || position >= itemCount) return null
        return peek(position)
    }

    fun setSelectionState(state: FavoritesSelectionUiState) {
        selectedIds = state.selectedIds
        selectionMode = state.isSelectionMode
        notifyDataSetChanged()
    }

    class VH(private val binding: ModuleWordbookFragmentFavoritesItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: FavoritesWord,
            selectionMode: Boolean,
            selected: Boolean,
            onItemClick: (FavoritesWord) -> Unit,
            onItemLongClick: (FavoritesWord) -> Boolean
        ) {
            binding.tvWord.text = item.word
            binding.tvMeaning.text = item.meaning
            binding.ivSelection.isVisible = selectionMode
            binding.ivSelection.setImageResource(
                if (selected) {
                    com.chen.memorizewords.feature.wordbook.R.drawable.module_wordbook_ic_check_circle
                } else {
                    0
                }
            )
            binding.ivSelection.setBackgroundResource(
                if (selected) {
                    0
                } else {
                    com.chen.memorizewords.feature.wordbook.R.drawable.module_wordbook_white_radio_16dp_not_border
                }
            )
            binding.rootContainer.setBackgroundResource(
                if (selected) {
                    com.chen.memorizewords.feature.wordbook.R.drawable.feature_wordbook_bg_favorites_card_selected
                } else {
                    com.chen.memorizewords.feature.wordbook.R.drawable.feature_wordbook_bg_favorites_card
                }
            )
            binding.rootContainer.setOnClickListener { onItemClick(item) }
            binding.rootContainer.setOnLongClickListener { onItemLongClick(item) }
        }
    }
}
