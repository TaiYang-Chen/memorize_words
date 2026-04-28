package com.chen.memorizewords.feature.home.ui.sync

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.home.databinding.FeatureHomeItemPendingSyncRecordBinding

class PendingSyncAdapter(
    private val onItemClick: (Long) -> Unit
) : ListAdapter<PendingSyncItemUi, PendingSyncAdapter.PendingSyncViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingSyncViewHolder {
        val binding = FeatureHomeItemPendingSyncRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PendingSyncViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PendingSyncViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class PendingSyncViewHolder(
        private val binding: FeatureHomeItemPendingSyncRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PendingSyncItemUi, onItemClick: (Long) -> Unit) {
            binding.tvPendingItemTitle.text = item.bizTypeLabel
            binding.tvPendingItemMeta.text = "${item.stateLabel} · ${item.operationLabel}"
            binding.tvPendingItemBizKey.text = item.bizKeyText
            binding.tvPendingItemUpdatedAt.text = item.updatedAtText
            binding.tvPendingItemRetry.isVisible = item.retryText.isNotBlank()
            binding.tvPendingItemRetry.text = item.retryText
            binding.tvPendingItemFailure.isVisible = item.failureText.isNotBlank()
            binding.tvPendingItemFailure.text = item.failureText
            binding.tvPendingItemError.isVisible = item.lastErrorText.isNotBlank()
            binding.tvPendingItemError.text = item.lastErrorText
            binding.tvPendingItemNextRetry.isVisible = item.nextRetryAtText.isNotBlank()
            binding.tvPendingItemNextRetry.text = item.nextRetryAtText
            binding.tvPendingItemExpandHint.text = item.expandHintText

            binding.detailContainer.isVisible = item.isExpanded
            binding.tvPendingItemDetailHint.isVisible = item.detailHintText.isNotBlank()
            binding.tvPendingItemDetailHint.text = item.detailHintText
            binding.tvPendingItemDetailFields.isVisible = item.detailFieldsText.isNotBlank()
            binding.tvPendingItemDetailFields.text = item.detailFieldsText
            binding.tvPendingItemRawPayload.text = item.rawPayloadText

            binding.root.setOnClickListener { onItemClick(item.id) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PendingSyncItemUi>() {
            override fun areItemsTheSame(
                oldItem: PendingSyncItemUi,
                newItem: PendingSyncItemUi
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: PendingSyncItemUi,
                newItem: PendingSyncItemUi
            ): Boolean = oldItem == newItem
        }
    }
}
