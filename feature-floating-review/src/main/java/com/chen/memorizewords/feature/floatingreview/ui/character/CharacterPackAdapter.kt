package com.chen.memorizewords.feature.floatingreview.ui.character

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.feature.floatingreview.R
import java.util.Locale

class CharacterPackAdapter(
    private val onPrimary: (CharacterPackUiItem) -> Unit,
    private val onCancel: (CharacterPackUiItem) -> Unit,
    private val onDelete: (CharacterPackUiItem) -> Unit
) : RecyclerView.Adapter<CharacterPackAdapter.Holder>() {
    private var items: List<CharacterPackUiItem> = emptyList()

    fun submitItems(updated: List<CharacterPackUiItem>) {
        items = updated
        notifyDataSetChanged()
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
            if (item.builtIn) {
                preview.setImageResource(R.drawable.module_floating_review_character_default_preview)
            } else {
                preview.load(item.previewUrl) {
                    crossfade(true)
                    placeholder(R.drawable.module_floating_review_bg_floating_ball)
                    error(R.drawable.module_floating_review_bg_floating_ball)
                }
            }
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
            progress.visibility = if (active) View.VISIBLE else View.GONE
            progress.progress = state?.progress ?: 0
            progressText.visibility = if (active || state?.status == CharacterPackDownloadStatus.FAILED) {
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
                !(item.selected && item.installed && !item.updateAvailable) &&
                (item.installed || item.catalogItem != null)
            primary.text = when {
                state?.status == CharacterPackDownloadStatus.FAILED -> itemView.context.getString(R.string.module_floating_review_character_retry)
                item.updateAvailable -> itemView.context.getString(R.string.module_floating_review_character_update)
                !item.installed && item.catalogItem == null -> itemView.context.getString(
                    R.string.module_floating_review_character_unavailable
                )
                !item.installed -> itemView.context.getString(
                    R.string.module_floating_review_character_download_use_with_size,
                    formatBytes(item.packageSizeBytes)
                )
                item.selected -> itemView.context.getString(R.string.module_floating_review_character_in_use)
                else -> itemView.context.getString(R.string.module_floating_review_character_use)
            }
            delete.visibility = if (!item.builtIn && item.installed) View.VISIBLE else View.GONE
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
