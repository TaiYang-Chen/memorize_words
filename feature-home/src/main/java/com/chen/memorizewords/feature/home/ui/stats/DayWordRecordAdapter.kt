package com.chen.memorizewords.feature.home.ui.stats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.home.R

class DayWordRecordAdapter(
    private val style: WordRecordStyle
) : RecyclerView.Adapter<DayWordRecordAdapter.WordViewHolder>() {

    private val items = mutableListOf<DayStudyWordItemUi>()

    fun submitList(newItems: List<DayStudyWordItemUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stats_day_word, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        holder.bind(items[position], style)
    }

    override fun getItemCount(): Int = items.size

    class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBadge: TextView = itemView.findViewById(R.id.tvBadge)
        private val tvWord: TextView = itemView.findViewById(R.id.tvWord)
        private val tvDefinition: TextView = itemView.findViewById(R.id.tvDefinition)

        fun bind(item: DayStudyWordItemUi, style: WordRecordStyle) {
            val context = itemView.context
            tvBadge.text = item.badgeText
            tvBadge.setTextColor(ContextCompat.getColor(context, style.badgeTextColorRes))
            tvBadge.setBackgroundResource(style.badgeBackgroundRes)
            tvWord.text = item.word
            tvDefinition.text = item.definition
        }
    }
}

enum class WordRecordStyle(
    val badgeBackgroundRes: Int,
    val badgeTextColorRes: Int
) {
    NEW(
        R.drawable.feature_home_stats_day_word_badge_new,
        R.color.feature_home_stats_day_word_badge_new_text
    ),
    REVIEW(
        R.drawable.feature_home_stats_day_word_badge_review,
        R.color.feature_home_stats_day_word_badge_review_text
    )
}
