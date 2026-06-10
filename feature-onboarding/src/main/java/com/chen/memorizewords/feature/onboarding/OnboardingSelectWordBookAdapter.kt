package com.chen.memorizewords.feature.onboarding

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.feature.onboarding.databinding.ItemOnboardingWordBookBinding
import java.text.NumberFormat
import kotlin.math.min
import kotlin.math.roundToInt

class OnboardingSelectWordBookAdapter(
    private val onSelectClick: (WordBook) -> Unit
) : PagingDataAdapter<WordBook, OnboardingSelectWordBookAdapter.WordBookViewHolder>(DiffCallback) {

    private var selectedBookId: Long? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordBookViewHolder {
        val binding = ItemOnboardingWordBookBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WordBookViewHolder(binding, onSelectClick)
    }

    override fun onBindViewHolder(holder: WordBookViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position)?.id == selectedBookId)
    }

    fun updateSelectedBookId(value: Long?) {
        if (selectedBookId == value) return
        selectedBookId = value
        notifyDataSetChanged()
    }

    class WordBookViewHolder(
        private val binding: ItemOnboardingWordBookBinding,
        private val onSelectClick: (WordBook) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val numberFormat = NumberFormat.getIntegerInstance()
        private val cardDesignWidthPx by lazy(LazyThreadSafetyMode.NONE) {
            binding.root.resources.getDimensionPixelSize(
                R.dimen.feature_onboarding_book_card_design_width
            )
        }
        private val cardDesignHeightPx by lazy(LazyThreadSafetyMode.NONE) {
            binding.root.resources.getDimensionPixelSize(
                R.dimen.feature_onboarding_book_card_design_height
            )
        }
        private val selectedStrokeWidthPx by lazy(LazyThreadSafetyMode.NONE) {
            binding.root.resources.getDimensionPixelSize(
                R.dimen.feature_onboarding_book_card_selected_stroke_width
            )
        }
        private val unselectedStrokeWidthPx by lazy(LazyThreadSafetyMode.NONE) {
            binding.root.resources.getDimensionPixelSize(
                R.dimen.feature_onboarding_book_card_unselected_stroke_width
            )
        }
        private val unselectedElevationPx by lazy(LazyThreadSafetyMode.NONE) {
            binding.root.resources.getDimension(
                R.dimen.feature_onboarding_book_card_unselected_elevation
            )
        }
        private val selectedElevationPx by lazy(LazyThreadSafetyMode.NONE) {
            binding.root.resources.getDimension(
                R.dimen.feature_onboarding_book_card_selected_elevation
            )
        }
        private val selectedStrokeColor by lazy(LazyThreadSafetyMode.NONE) {
            binding.root.context.getColor(R.color.feature_onboarding_book_card_selected_stroke)
        }
        private val unselectedStrokeColor by lazy(LazyThreadSafetyMode.NONE) {
            binding.root.context.getColor(R.color.feature_onboarding_book_card_unselected_stroke)
        }

        fun bind(item: WordBook?, isSelected: Boolean) {
            val wordBook = item ?: return
            binding.tvTitle.text = wordBook.title
            binding.tvDescription.text = wordBook.description.ifBlank { DEFAULT_BOOK_DESCRIPTION }
            binding.tvCount.text = "${numberFormat.format(wordBook.totalWords)} \u8bcd"
            binding.tvLearnerCount.text = formatPlaceholderLearnerCount(wordBook)
            bindSelection(isSelected)
            bindCover(wordBook)
            bindScale()
            binding.root.setOnClickListener { onSelectClick(wordBook) }
            binding.cardCanvas.setOnClickListener { onSelectClick(wordBook) }
        }

        private fun bindSelection(isSelected: Boolean) {
            binding.ivSelected.isVisible = isSelected
            styleTitle(binding.tvTitle, isSelected)
            binding.cardCanvas.apply {
                strokeColor = if (isSelected) selectedStrokeColor else unselectedStrokeColor
                strokeWidth = if (isSelected) selectedStrokeWidthPx else unselectedStrokeWidthPx
                cardElevation = if (isSelected) selectedElevationPx else unselectedElevationPx
            }
            ViewCompat.setStateDescription(
                binding.root,
                if (isSelected) "\u5df2\u9009\u4e2d" else "\u672a\u9009\u4e2d"
            )
        }

        private fun styleTitle(view: android.widget.TextView, isSelected: Boolean) {
            view.typeface = Typeface.create(
                if (isSelected) "sans-serif-black" else "sans-serif-bold",
                Typeface.NORMAL
            )
        }

        private fun bindCover(wordBook: WordBook) {
            OnboardingWordBookImageLoader.load(
                imageView = binding.ivCover,
                fallbackView = binding.ivCoverFallback,
                rawUrl = wordBook.imgUrl
            )
        }

        private fun bindScale() {
            binding.root.doOnLayout { root ->
                val availableWidth = root.width - root.paddingStart - root.paddingEnd
                if (availableWidth <= 0) return@doOnLayout

                val scale = min(1f, availableWidth / cardDesignWidthPx.toFloat())
                binding.cardCanvas.pivotX = 0f
                binding.cardCanvas.pivotY = 0f
                binding.cardCanvas.scaleX = scale
                binding.cardCanvas.scaleY = scale
                binding.cardCanvas.translationX = if (scale == 1f) {
                    ((availableWidth - cardDesignWidthPx) / 2f).coerceAtLeast(0f)
                } else {
                    0f
                }
                binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    height = (cardDesignHeightPx * scale).roundToInt()
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_BOOK_DESCRIPTION = "\u6682\u65e0\u7b80\u4ecb"

        private val DiffCallback = object : DiffUtil.ItemCallback<WordBook>() {
            override fun areItemsTheSame(oldItem: WordBook, newItem: WordBook): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: WordBook, newItem: WordBook): Boolean {
                return oldItem == newItem
            }
        }
    }
}
