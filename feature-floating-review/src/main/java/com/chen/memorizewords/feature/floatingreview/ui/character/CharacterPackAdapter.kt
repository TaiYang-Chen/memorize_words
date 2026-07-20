package com.chen.memorizewords.feature.floatingreview.ui.character

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.google.android.material.button.MaterialButton
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.feature.floatingreview.R
import java.util.Locale

class CharacterPackAdapter(
    private val activationMode: Boolean,
    private val onPrimary: (CharacterPackUiItem) -> Unit,
    private val onCancel: (CharacterPackUiItem) -> Unit,
    private val onDelete: (CharacterPackUiItem) -> Unit
) : RecyclerView.Adapter<CharacterPackAdapter.Holder>() {
    private var items: List<CharacterPackUiItem> = emptyList()

    fun submitItems(updated: List<CharacterPackUiItem>) {
        val previous = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = previous.size
            override fun getNewListSize(): Int = updated.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                previous[oldItemPosition].packId == updated[newItemPosition].packId

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                previous[oldItemPosition] == updated[newItemPosition]
        })
        items = updated
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.module_floating_review_item_character_pack,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val preview = view.findViewById<ImageView>(R.id.ivCharacterPreview)
        private val name = view.findViewById<TextView>(R.id.tvCharacterName)
        private val description = view.findViewById<TextView>(R.id.tvCharacterDescription)
        private val defaultBadge = view.findViewById<TextView>(R.id.tvCharacterDefault)
        private val badge = view.findViewById<TextView>(R.id.tvCharacterBadge)
        private val progress = view.findViewById<ProgressBar>(R.id.progressCharacterDownload)
        private val progressText = view.findViewById<TextView>(R.id.tvCharacterProgress)
        private val primary = view.findViewById<MaterialButton>(R.id.btnCharacterPrimary)
        private val cancel = view.findViewById<MaterialButton>(R.id.btnCharacterCancel)
        private val delete = view.findViewById<MaterialButton>(R.id.btnCharacterDelete)

        fun bind(item: CharacterPackUiItem) {
            name.text = item.displayName
            description.text = item.description.orEmpty()
            description.visibility = if (item.description.isNullOrBlank()) View.GONE else View.VISIBLE
            val previewFallback = R.drawable.module_floating_review_bg_floating_ball
            if (!item.previewUrl.isNullOrBlank()) {
                preview.load(item.previewUrl) {
                    crossfade(true)
                    placeholder(previewFallback)
                    error(previewFallback)
                    fallback(previewFallback)
                }
            } else {
                preview.dispose()
                preview.setImageResource(previewFallback)
            }
            defaultBadge.visibility = if (item.defaultPack) View.VISIBLE else View.GONE
            badge.visibility = if (item.selected || item.accountSelectedMissing) View.VISIBLE else View.GONE
            badge.text = if (item.accountSelectedMissing) {
                itemView.context.getString(R.string.module_floating_review_character_waiting_download)
            } else {
                itemView.context.getString(R.string.module_floating_review_character_selected)
            }

            val state = item.download
            val active = state?.status == CharacterPackDownloadStatus.QUEUED ||
                state?.status == CharacterPackDownloadStatus.DOWNLOADING ||
                state?.status == CharacterPackDownloadStatus.INSTALLING
            val requiresDownload = !item.usable || (!activationMode && item.updateAvailable)
            val showFailure = state?.status == CharacterPackDownloadStatus.FAILED && requiresDownload
            progress.visibility = if (active) View.VISIBLE else View.GONE
            progress.progress = state?.progress ?: 0
            progressText.visibility = if (active || showFailure) {
                View.VISIBLE
            } else View.GONE
            progressText.text = when (state?.status) {
                CharacterPackDownloadStatus.QUEUED -> itemView.context.getString(R.string.module_floating_review_character_queued)
                CharacterPackDownloadStatus.DOWNLOADING -> "${state.progress}%"
                CharacterPackDownloadStatus.INSTALLING -> itemView.context.getString(R.string.module_floating_review_character_installing)
                CharacterPackDownloadStatus.FAILED -> state.errorMessage
                    ?: itemView.context.getString(R.string.module_floating_review_character_download_failed)
                else -> ""
            }
            cancel.visibility = if (active) View.VISIBLE else View.GONE
            primary.isEnabled = !active &&
                (activationMode || !(item.selected && item.usable && !item.updateAvailable)) &&
                (item.usable || item.catalogItem != null)
            primary.text = when {
                activationMode && item.usable -> itemView.context.getString(
                    R.string.module_floating_review_character_use_enable
                )
                showFailure ->
                    itemView.context.getString(R.string.module_floating_review_character_retry)
                !item.usable && item.catalogItem == null -> itemView.context.getString(
                    R.string.module_floating_review_character_unavailable
                )
                !item.usable -> itemView.context.getString(
                    if (activationMode) {
                        R.string.module_floating_review_character_download_enable_with_size
                    } else {
                        R.string.module_floating_review_character_download_with_size
                    },
                    formatBytes(item.packageSizeBytes)
                )
                item.updateAvailable -> itemView.context.getString(R.string.module_floating_review_character_update)
                item.selected -> itemView.context.getString(R.string.module_floating_review_character_in_use)
                else -> itemView.context.getString(
                    if (activationMode) {
                        R.string.module_floating_review_character_use_enable
                    } else {
                        R.string.module_floating_review_character_use
                    }
                )
            }
            delete.visibility = if (!activationMode && item.installed) View.VISIBLE else View.GONE
            delete.isEnabled = !active
            primary.setOnClickListener { onPrimary(item) }
            cancel.setOnClickListener { onCancel(item) }
            delete.setOnClickListener { onDelete(item) }
        }

        private fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "--"
            val mib = bytes.toDouble() / (1024.0 * 1024.0)
            return String.format(Locale.getDefault(), "%.1f MB", mib)
        }
    }
}
