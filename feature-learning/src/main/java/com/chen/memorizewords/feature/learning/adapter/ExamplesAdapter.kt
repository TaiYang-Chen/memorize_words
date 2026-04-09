package com.chen.memorizewords.feature.learning.adapter

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.feature.learning.databinding.HomeRvItemExamplesBinding

class ExamplesAdapter(
    val onclickWord: (token: ClickableWordToken, anchorRect: Rect) -> Unit,
    val onSpeakSentence: (sentence: String) -> Unit
) : ListAdapter<WordExample, ExamplesAdapter.ExampleViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WordExample>() {
            override fun areItemsTheSame(oldItem: WordExample, newItem: WordExample): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: WordExample, newItem: WordExample): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class ExampleViewHolder(val binding: HomeRvItemExamplesBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WordExample) {
            // 点击逻辑由 binding adapter（clickableWords）处理并回调到这个 adapter 的 listener（见后文）
            val listener = object : OnWordClickListener {
                override fun onWordClick(token: ClickableWordToken, rect: Rect) {
                    onclickWord(token, rect)
                }
            }
            binding.data = item
            binding.click = listener
            binding.speakerIcon.setOnClickListener {
                item.englishSentence
                    .takeIf { sentence -> sentence.isNotBlank() }
                    ?.let(onSpeakSentence)
            }
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExampleViewHolder {
        val binding = HomeRvItemExamplesBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ExampleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExampleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
