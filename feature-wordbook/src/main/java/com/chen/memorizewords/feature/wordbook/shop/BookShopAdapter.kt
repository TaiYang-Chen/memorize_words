package com.chen.memorizewords.feature.wordbook.shop

import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.domain.model.wordbook.shop.DownloadState
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookItemBookShopBinding

class BookShopAdapter(
    private val onActionClick: (BookShopUi) -> Unit
) : PagingDataAdapter<BookShopUi, BookShopAdapter.ShopViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ModuleWordbookItemBookShopBinding.inflate(inflater, parent, false)
        return ShopViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        getItem(position)?.let { holder.bindFull(it, onActionClick) }
    }

    override fun onBindViewHolder(
        holder: ShopViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val item = getItem(position) ?: return
        if (payloads.contains(PAYLOAD_DOWNLOAD_STATE)) {
            holder.bindAction(item, onActionClick)
            return
        }
        holder.bindFull(item, onActionClick)
    }

    class ShopViewHolder(
        private val binding: ModuleWordbookItemBookShopBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val progressDrawable: LayerDrawable? by lazy {
            AppCompatResources.getDrawable(
                binding.root.context,
                R.drawable.module_wordbook_bg_download_btn_progress
            )?.mutate() as? LayerDrawable
        }

        fun bindFull(
            item: BookShopUi,
            onActionClick: (BookShopUi) -> Unit
        ) {
            bindBookInfo(item.book)
            bindAction(item, onActionClick)
        }

        private fun bindBookInfo(book: WordBook) {
            binding.tvTitle.text = book.title
            binding.tvCategory.text = book.category
            binding.tvCount.text = binding.root.context.getString(
                R.string.module_wordbook_shop_word_count,
                book.totalWords
            )
            binding.tvUsers.text =
                if (book.isPublic) binding.root.context.getString(R.string.module_wordbook_public)
                else binding.root.context.getString(R.string.module_wordbook_private)
            binding.tvDesc.text = book.description

            binding.ivCover.load(book.imgUrl) {
                placeholder(R.drawable.module_wordbook_ic_book)
                error(R.drawable.module_wordbook_ic_book)
            }
        }

        fun bindAction(
            item: BookShopUi,
            onActionClick: (BookShopUi) -> Unit
        ) {
            val state = item.downloadState
            binding.btnAction.backgroundTintList = null
            binding.btnAction.isEnabled = item.actionEnabled
            binding.btnAction.text = item.actionText

            when (state) {
                is DownloadState.Downloading,
                is DownloadState.Paused -> {
                    binding.btnAction.setTextColor(0xFFFFFFFF.toInt())
                    applyProgressBackground(item.actionProgressPercent)
                }

                is DownloadState.Downloaded -> {
                    binding.btnAction.setTextColor(0xFF888888.toInt())
                    binding.btnAction.setBackgroundResource(R.drawable.module_wordbook_selector_fbfaf6_radius)
                }

                is DownloadState.UpdateAvailable,
                is DownloadState.NotDownloaded,
                is DownloadState.Failed -> {
                    binding.btnAction.setTextColor(0xFFFFFFFF.toInt())
                    binding.btnAction.setBackgroundResource(R.drawable.module_wordbook_selector_ff8000_radius)
                }
            }

            binding.btnAction.setOnClickListener { onActionClick(item) }
        }

        private fun applyProgressBackground(progress: Int) {
            val drawable = progressDrawable
            if (drawable == null) {
                binding.btnAction.setBackgroundResource(R.drawable.module_wordbook_selector_ff8000_radius)
                return
            }
            drawable.findDrawableByLayerId(R.id.progress_layer)?.level =
                (progress.coerceIn(0, 100) * 100)
            binding.btnAction.backgroundTintList = null
            if (binding.btnAction.background !== drawable) {
                binding.btnAction.background = drawable
            }
            binding.btnAction.invalidate()
        }
    }

    companion object {
        private const val PAYLOAD_DOWNLOAD_STATE = "payload_download_state"

        private val DiffCallback = object : DiffUtil.ItemCallback<BookShopUi>() {
            override fun areItemsTheSame(oldItem: BookShopUi, newItem: BookShopUi): Boolean {
                return oldItem.book.id == newItem.book.id
            }

            override fun areContentsTheSame(oldItem: BookShopUi, newItem: BookShopUi): Boolean {
                return oldItem.book == newItem.book &&
                        hasSameDownloadState(oldItem.downloadState, newItem.downloadState)
            }

            override fun getChangePayload(oldItem: BookShopUi, newItem: BookShopUi): Any? {
                return if (oldItem.book == newItem.book &&
                    !hasSameDownloadState(oldItem.downloadState, newItem.downloadState)
                ) {
                    PAYLOAD_DOWNLOAD_STATE
                } else {
                    null
                }
            }

            private fun hasSameDownloadState(oldState: DownloadState, newState: DownloadState): Boolean {
                return when {
                    oldState is DownloadState.Downloading && newState is DownloadState.Downloading -> {
                        oldState.progress == newState.progress
                    }

                    oldState is DownloadState.Paused && newState is DownloadState.Paused -> {
                        oldState.progress == newState.progress
                    }

                    oldState is DownloadState.Failed && newState is DownloadState.Failed -> {
                        oldState.message == newState.message
                    }

                    else -> oldState::class == newState::class
                }
            }
        }
    }
}
