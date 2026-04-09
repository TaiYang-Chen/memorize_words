package com.chen.memorizewords.feature.learning.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.HomeRvItemSynonymsBinding

data class Synonyms(
    val type: Boolean,
    val str: String
)

class SynonymsAdapter() :
    BaseRecyclerViewAdapter<Synonyms, HomeRvItemSynonymsBinding>(DIFF) {

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Synonyms>() {
            override fun areItemsTheSame(oldItem: Synonyms, newItem: Synonyms): Boolean {
                return oldItem.type == newItem.type && oldItem.str == newItem.str
            }

            override fun areContentsTheSame(oldItem: Synonyms, newItem: Synonyms): Boolean {
                return oldItem == newItem
            }
        }
    }

    fun submitList(list1: List<String>, list2: List<String>) {
        val items = buildList {
            list1.forEach { add(Synonyms(true, it)) }
            list2.forEach { add(Synonyms(false, it)) }
        }
        submitList(items)
    }

    override fun onCreateDataBinding(
        parent: ViewGroup,
        viewType: Int
    ): HomeRvItemSynonymsBinding {
        return HomeRvItemSynonymsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    }

    override fun onBindViewHolderDataBinding(
        dB: HomeRvItemSynonymsBinding,
        iTEM: Synonyms
    ) {
        dB.text.text = iTEM.str
        val colorRes = if (iTEM.type) R.color.purple_primary else R.color.gray_secondary
        dB.text.setTextColor(ContextCompat.getColor(dB.root.context, colorRes))
        dB.text.setBackgroundResource(
            if (iTEM.type) {
                R.drawable.module_learning_bg_radius_faf5ff
            } else {
                R.drawable.module_learning_bg_radius_f1f5f9
            }
        )
    }
}
