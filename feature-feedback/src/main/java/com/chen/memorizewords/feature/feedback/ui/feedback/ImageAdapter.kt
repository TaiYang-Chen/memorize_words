package com.chen.memorizewords.feature.feedback.ui.feedback

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chen.memorizewords.feature.feedback.R

class ImageAdapter(
    private val onDeleteClick: (position: Int) -> Unit
) : ListAdapter<Uri, ImageAdapter.VH>(DiffCallback) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.iv_item_image_thumb)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_item_image_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.module_feedback_item_feedback_image, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = getItem(position)
        Glide.with(holder.ivThumb.context)
            .load(uri)
            .centerCrop()
            .into(holder.ivThumb)

        holder.btnDelete.setOnClickListener {
            onDeleteClick(holder.bindingAdapterPosition)
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<Uri>() {
            override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem

            override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem
        }
    }
}
