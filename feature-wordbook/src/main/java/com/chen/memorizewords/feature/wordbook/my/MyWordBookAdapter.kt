package com.chen.memorizewords.feature.wordbook.my

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookItemWordbookBinding
import com.chen.memorizewords.feature.wordbook.shop.BookShopWordBookImageLoader

class MyWordBookAdapter :
    ListAdapter<WordBookInfo, MyWordBookAdapter.WordBookViewHolder>(DiffCallback) {

    private var onItemClickListener: ((WordBookInfo) -> Unit)? = null

    fun setOnItemClickListener(listener: (WordBookInfo) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordBookViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ModuleWordbookItemWordbookBinding.inflate(inflater, parent, false)
        return WordBookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WordBookViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClickListener)
    }

    class WordBookViewHolder(
        private val binding: ModuleWordbookItemWordbookBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WordBookInfo, listener: ((WordBookInfo) -> Unit)?) {
            val context = binding.root.context
            val resources = context.resources
            val subtitle = item.description.trim().ifEmpty { item.category.trim() }
            val safeMax = item.totalWords.coerceAtLeast(1)
            val safeProgress = item.learningWords.coerceIn(0, safeMax)
            val safeMastered = item.masteredWords.coerceIn(0, safeProgress)
            val isCompleted = item.totalWords > 0 && item.masteredWords >= item.totalWords && !item.isSelected

            binding.apply {
                info = item
                tvTitle.text = item.title
                tvSubtitle.text = subtitle
                tvSubtitle.isVisible = subtitle.isNotEmpty()
                tvProgressLabel.text = context.getString(
                    R.string.feature_wordbook_my_book_progress_label,
                    item.learningWords,
                    item.totalWords
                )
                tvMasteredLabel.text = context.getString(
                    R.string.feature_wordbook_my_book_mastered_label,
                    item.masteredWords
                )

                tvStatus.isVisible = item.isSelected || isCompleted
                when {
                    item.isSelected -> {
                        tvStatus.text = "\u6b63\u5728\u5b66\u4e60"
                        tvStatus.setBackgroundResource(R.drawable.feature_wordbook_bg_my_book_status)
                        tvStatus.setTextColor(
                            ContextCompat.getColor(context, R.color.feature_wordbook_my_book_status_text)
                        )
                    }

                    isCompleted -> {
                        tvStatus.text = "\u5df2\u5b8c\u6210"
                        tvStatus.setBackgroundResource(R.drawable.feature_wordbook_bg_my_book_status_completed)
                        tvStatus.setTextColor(
                            ContextCompat.getColor(
                                context,
                                R.color.feature_wordbook_my_book_status_completed_text
                            )
                        )
                    }
                }

                rootCard.strokeWidth = if (item.isSelected) {
                    resources.getDimensionPixelSize(R.dimen.feature_wordbook_my_book_card_stroke_width)
                } else {
                    0
                }
                rootCard.strokeColor = ContextCompat.getColor(
                    context,
                    if (item.isSelected) {
                        R.color.feature_wordbook_my_book_card_stroke_selected
                    } else {
                        android.R.color.transparent
                    }
                )
                rootCard.cardElevation =
                    if (item.isSelected) 0f else resources.getDimension(R.dimen.feature_wordbook_my_book_card_elevation)

                progressStudy.setMax(safeMax)
                progressStudy.setProgress1(safeProgress)
                progressStudy.setProgress2(safeMastered)

                BookShopWordBookImageLoader.load(ivCover, ivCoverFallback, item.imgUrl)
                rootCard.setOnClickListener { listener?.invoke(item) }
                executePendingBindings()
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<WordBookInfo>() {
            override fun areItemsTheSame(oldItem: WordBookInfo, newItem: WordBookInfo): Boolean {
                return oldItem.bookId == newItem.bookId
            }

            override fun areContentsTheSame(oldItem: WordBookInfo, newItem: WordBookInfo): Boolean {
                return oldItem == newItem
            }
        }
    }
}
