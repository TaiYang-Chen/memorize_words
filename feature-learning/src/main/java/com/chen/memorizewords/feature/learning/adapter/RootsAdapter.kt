package com.chen.memorizewords.feature.learning.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.chen.memorizewords.domain.model.words.word.WordRoot
import com.chen.memorizewords.feature.learning.databinding.HomeRvItemRootsBinding

class RootsAdapter :
    BaseRecyclerViewAdapter<WordRoot, HomeRvItemRootsBinding>(DIFF) {

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<WordRoot>() {
            override fun areItemsTheSame(oldItem: WordRoot, newItem: WordRoot): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: WordRoot, newItem: WordRoot): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateDataBinding(
        parent: ViewGroup,
        viewType: Int
    ): HomeRvItemRootsBinding {
        return HomeRvItemRootsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    }

    override fun onBindViewHolderDataBinding(
        dB: HomeRvItemRootsBinding,
        iTEM: WordRoot
    ) {
        dB.data = iTEM
    }
}
