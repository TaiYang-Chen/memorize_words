package com.chen.memorizewords.feature.home.ui.practice

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.home.databinding.ItemPracticeRecordWordBinding

class PracticeRecordWordAdapter :
    ListAdapter<PracticeRecordWordItemUi, PracticeRecordWordAdapter.WordViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemPracticeRecordWordBinding.inflate(inflater, parent, false)
        return WordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class WordViewHolder(
        private val binding: ItemPracticeRecordWordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PracticeRecordWordItemUi) {
            binding.tvWord.text = item.word
            binding.tvPhonetic.text = item.phonetic
            binding.tvDefinition.text = item.definition
            binding.tvPhonetic.isVisible = item.phonetic.isNotBlank()
            binding.tvDefinition.isVisible = item.definition.isNotBlank()
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PracticeRecordWordItemUi>() {
            override fun areItemsTheSame(
                oldItem: PracticeRecordWordItemUi,
                newItem: PracticeRecordWordItemUi
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: PracticeRecordWordItemUi,
                newItem: PracticeRecordWordItemUi
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
