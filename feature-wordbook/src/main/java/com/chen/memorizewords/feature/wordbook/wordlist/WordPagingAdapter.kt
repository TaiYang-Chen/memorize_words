package com.chen.memorizewords.feature.wordbook.wordlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.domain.model.words.WordListRow
import com.chen.memorizewords.feature.wordbook.R

class WordPagingAdapter :
    PagingDataAdapter<WordListRow, WordVH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.module_wordbook_item_word, parent, false)
        return WordVH(view)
    }

    override fun onBindViewHolder(holder: WordVH, position: Int) {
        getItem(position)?.let(holder::bind)
    }

    object Diff : DiffUtil.ItemCallback<WordListRow>() {
        override fun areItemsTheSame(a: WordListRow, b: WordListRow) =
            a.wordId == b.wordId

        override fun areContentsTheSame(a: WordListRow, b: WordListRow) =
            a == b
    }
}


class WordVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    /** 供 Decoration 计算白卡区域 */
    val root: View = itemView

    private val tvWord: TextView = itemView.findViewById(R.id.tvWord)
    private val tvPhonetic: TextView = itemView.findViewById(R.id.tvPhonetic)
    private val tvMeaning: TextView = itemView.findViewById(R.id.tvDefinition)

    fun bind(item: WordListRow) {
        tvWord.text = item.word
        tvPhonetic.text = item.phonetic ?: ""
        tvMeaning.text = "${item.partOfSpeech.abbr}. ${item.meanings}"
    }
}


