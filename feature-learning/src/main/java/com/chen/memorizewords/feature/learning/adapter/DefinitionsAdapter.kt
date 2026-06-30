package com.chen.memorizewords.feature.learning.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.feature.learning.databinding.HomeRvItemDefinitionsBinding

class DefinitionsAdapter :
    BaseRecyclerViewAdapter<WordDefinitions, HomeRvItemDefinitionsBinding>(DIFF) {

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<WordDefinitions>() {
            override fun areItemsTheSame(
                oldItem: WordDefinitions,
                newItem: WordDefinitions
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: WordDefinitions,
                newItem: WordDefinitions
            ): Boolean = oldItem == newItem
        }
    }

    override fun onCreateDataBinding(
        parent: ViewGroup,
        viewType: Int
    ): HomeRvItemDefinitionsBinding {
        return HomeRvItemDefinitionsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    }

    override fun onBindViewHolderDataBinding(
        dB: HomeRvItemDefinitionsBinding,
        iTEM: WordDefinitions
    ) {
        dB.data = iTEM
    }

    fun submitGroupedDefinitions(definitions: List<WordDefinitions>) {
        submitList(definitions.groupByPartOfSpeech())
    }

    private fun List<WordDefinitions>.groupByPartOfSpeech(): List<WordDefinitions> {
        return groupBy { it.partOfSpeech }
            .values
            .mapNotNull { group ->
                val first = group.firstOrNull() ?: return@mapNotNull null
                val meanings = group
                    .map { it.meaningChinese.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .joinToString(separator = "，")

                if (meanings.isBlank()) {
                    null
                } else {
                    first.copy(meaningChinese = meanings)
                }
            }
    }
}
