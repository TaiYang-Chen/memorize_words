package com.chen.memorizewords.feature.home.ui.practice

import android.view.LayoutInflater
import android.view.ViewGroup
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.home.databinding.ItemPracticeRecordBinding

class PracticeRecordAdapter(
    private val onItemClick: (PracticeSessionRecordUi) -> Unit = {}
) :
    ListAdapter<PracticeSessionRecordUi, PracticeRecordAdapter.RecordViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemPracticeRecordBinding.inflate(inflater, parent, false)
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class RecordViewHolder(
        private val binding: ItemPracticeRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PracticeSessionRecordUi, onItemClick: (PracticeSessionRecordUi) -> Unit) {
            binding.tvRecordTitle.text = item.titleText
            binding.tvRecordSubtitle.text = item.subtitleText
            binding.ivRecordIcon.setImageResource(item.iconRes)
            val tintColor = ContextCompat.getColor(binding.root.context, item.iconTintRes)
            binding.ivRecordIcon.imageTintList = ColorStateList.valueOf(tintColor)
            binding.layoutRecordItem.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PracticeSessionRecordUi>() {
            override fun areItemsTheSame(
                oldItem: PracticeSessionRecordUi,
                newItem: PracticeSessionRecordUi
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: PracticeSessionRecordUi,
                newItem: PracticeSessionRecordUi
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
