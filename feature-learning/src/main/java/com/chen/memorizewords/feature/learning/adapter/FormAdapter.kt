package com.chen.memorizewords.feature.learning.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.chen.memorizewords.domain.model.words.word.WordForm
import com.chen.memorizewords.feature.learning.databinding.HomeRvItemInflectionBinding

class FormAdapter :
    BaseRecyclerViewAdapter<WordForm, HomeRvItemInflectionBinding>(DIFF) {

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<WordForm>() {
            override fun areItemsTheSame(oldItem: WordForm, newItem: WordForm): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: WordForm, newItem: WordForm): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateDataBinding(
        parent: ViewGroup,
        viewType: Int
    ): HomeRvItemInflectionBinding {
        return HomeRvItemInflectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    }

    override fun onBindViewHolderDataBinding(
        dB: HomeRvItemInflectionBinding,
        iTEM: WordForm
    ) {
        dB.data = iTEM
    }
}
