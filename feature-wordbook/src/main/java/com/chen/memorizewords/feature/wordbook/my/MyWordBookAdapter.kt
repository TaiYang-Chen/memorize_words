package com.chen.memorizewords.feature.wordbook.my

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.chen.memorizewords.domain.model.wordbook.WordBookInfo
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookItemWordbookBinding

class MyWordBookAdapter :
    ListAdapter<WordBookInfo, MyWordBookAdapter.WordBookViewHolder>(DiffCallback) {

    private var onItemClickListener: ((WordBookInfo) -> Unit)? = null

    // 设置点击监听器
    fun setOnItemClickListener(listener: (WordBookInfo) -> Unit) {
        this.onItemClickListener = listener
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
            binding.apply {
                info = item
                linearLayout.load(item.imgUrl.toDisplayUrl()) {
                    crossfade(true)
                    placeholder(R.drawable.module_wordbook_ic_book)
                    error(R.drawable.module_wordbook_ic_book)
                    fallback(R.drawable.module_wordbook_ic_book)
                }
                root.setOnClickListener { listener?.invoke(item) }
                executePendingBindings()
            }
        }

        private fun String?.toDisplayUrl(): String? {
            val raw = this?.trim().orEmpty()
            if (raw.isEmpty()) return null
            if (raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true)
            ) {
                return raw
                    .replace("http://localhost", "http://10.0.2.2")
                    .replace("https://localhost", "https://10.0.2.2")
            }
            return if (raw.startsWith("/")) {
                "http://10.0.2.2:8080$raw"
            } else {
                "http://10.0.2.2:8080/$raw"
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
