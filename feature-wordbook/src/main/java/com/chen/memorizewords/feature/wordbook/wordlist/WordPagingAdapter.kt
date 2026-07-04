package com.chen.memorizewords.feature.wordbook.wordlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.word.model.enums.WordLearningStatus
import com.chen.memorizewords.feature.wordbook.R

class WordPagingAdapter(
    private val onWordClick: (WordListRow) -> Unit,
    private val onFavoriteClick: (WordListRow) -> Unit,
    private val onSpeakClick: (WordListRow) -> Unit
) : PagingDataAdapter<WordListRow, WordVH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.module_wordbook_item_word, parent, false)
        return WordVH(view, onWordClick, onFavoriteClick, onSpeakClick)
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

class WordVH(
    itemView: View,
    private val onWordClick: (WordListRow) -> Unit,
    private val onFavoriteClick: (WordListRow) -> Unit,
    private val onSpeakClick: (WordListRow) -> Unit
) : RecyclerView.ViewHolder(itemView) {

    val root: View = itemView

    private val tvWord: TextView = itemView.findViewById(R.id.tvWord)
    private val tvPhonetic: TextView = itemView.findViewById(R.id.tvPhonetic)
    private val tvMeaning: TextView = itemView.findViewById(R.id.tvDefinition)
    private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
    private val tvMastery: TextView = itemView.findViewById(R.id.tvMastery)
    private val btnSpeak: ImageButton = itemView.findViewById(R.id.btnSpeak)
    private val btnFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)
    private val ivChevron: ImageView = itemView.findViewById(R.id.ivChevron)

    fun bind(item: WordListRow) {
        tvWord.text = item.word
        tvPhonetic.text = item.phonetic?.takeIf { it.isNotBlank() } ?: "--"
        tvMeaning.text = "${item.partOfSpeech.abbr}. ${item.meanings}"
        tvStatus.text = item.learningStatus.displayName()
        tvMastery.text = "掌握度 ${item.masteryLevel}/5"

        val context = itemView.context
        val (statusBg, statusText) = when (item.learningStatus) {
            WordLearningStatus.TO_LEARN -> R.drawable.feature_wordbook_bg_word_status_neutral to R.color.feature_wordbook_word_status_neutral_text
            WordLearningStatus.LEARNED -> R.drawable.feature_wordbook_bg_word_status_learning to R.color.feature_wordbook_word_status_learning_text
            WordLearningStatus.MASTERED -> R.drawable.feature_wordbook_bg_word_status_mastered to R.color.feature_wordbook_word_status_mastered_text
            WordLearningStatus.REVIEW_DUE -> R.drawable.feature_wordbook_bg_word_status_review to R.color.feature_wordbook_word_status_review_text
        }
        tvStatus.setBackgroundResource(statusBg)
        tvStatus.setTextColor(ContextCompat.getColor(context, statusText))

        btnFavorite.setImageResource(
            if (item.isFavorite) {
                R.drawable.feature_wordbook_ic_favorite_star_filled
            } else {
                R.drawable.feature_wordbook_ic_favorite_star_outline
            }
        )
        btnFavorite.contentDescription = if (item.isFavorite) "取消重点词" else "加入重点词"

        itemView.setOnClickListener { onWordClick(item) }
        ivChevron.setOnClickListener { onWordClick(item) }
        btnFavorite.setOnClickListener { onFavoriteClick(item) }
        btnSpeak.setOnClickListener { onSpeakClick(item) }
    }
}
