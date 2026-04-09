package com.chen.memorizewords.feature.learning.ui.done

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.domain.model.words.WordListRow
import com.chen.memorizewords.domain.model.words.enums.PartOfSpeech
import com.chen.memorizewords.feature.learning.R

class WordPagingAdapter(
    private val onItemClick: (WordListRow) -> Unit
) : ListAdapter<WordListRow, WordVH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_item_word_list, parent, false)
        return WordVH(view)
    }

    override fun onBindViewHolder(holder: WordVH, position: Int) {
        getItem(position)?.let { item ->
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }
    }

    object Diff : DiffUtil.ItemCallback<WordListRow>() {
        override fun areItemsTheSame(a: WordListRow, b: WordListRow): Boolean = a.wordId == b.wordId

        override fun areContentsTheSame(a: WordListRow, b: WordListRow): Boolean = a == b
    }
}

class WordVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val tvWord: TextView = itemView.findViewById(R.id.tvWord)
    private val tvPhonetic: TextView = itemView.findViewById(R.id.tvPhonetic)
    private val tvMeaning: TextView = itemView.findViewById(R.id.tvDefinition)
    private val tvAction: TextView = itemView.findViewById(R.id.tvAction)

    fun bind(item: WordListRow) {
        tvWord.text = item.word
        tvPhonetic.text = item.phonetic ?: ""

        val meaning = item.meanings.trim().ifBlank { "-" }
        tvMeaning.text = if (item.partOfSpeech == PartOfSpeech.UNKNOWN || item.partOfSpeech == PartOfSpeech.OTHER) {
            meaning
        } else {
            "${item.partOfSpeech.abbr} $meaning"
        }

        tvAction.text = itemView.context.getString(R.string.learning_done_word_action)
    }
}
