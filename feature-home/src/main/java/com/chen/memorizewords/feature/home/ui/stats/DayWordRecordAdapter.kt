package com.chen.memorizewords.feature.home.ui.stats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.home.R

class DayWordRecordAdapter : RecyclerView.Adapter<DayWordRecordAdapter.WordViewHolder>() {

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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvWord: TextView = itemView.findViewById(R.id.tvWord)
        private val tvDefinition: TextView = itemView.findViewById(R.id.tvDefinition)

        fun bind(item: DayStudyWordItemUi) {
            tvWord.text = item.word
            tvDefinition.text = item.definition
        }
    }
}
