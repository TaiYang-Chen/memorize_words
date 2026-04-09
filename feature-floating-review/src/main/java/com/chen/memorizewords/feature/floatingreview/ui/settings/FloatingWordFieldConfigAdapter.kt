package com.chen.memorizewords.feature.floatingreview.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldConfig
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldType
import com.chen.memorizewords.feature.floatingreview.R

class FloatingWordFieldConfigAdapter(
    items: List<FloatingWordFieldConfig>,
    private val labelProvider: (FloatingWordFieldType) -> String,
    private val onChanged: (List<FloatingWordFieldConfig>) -> Unit
) : ListAdapter<FloatingWordFieldConfig, FloatingWordFieldConfigAdapter.FieldConfigViewHolder>(DIFF) {

    init {
        submitList(items.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldConfigViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.module_floating_review_item_field_config, parent, false)
        return FieldConfigViewHolder(view)
    }

    override fun onBindViewHolder(holder: FieldConfigViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun replaceItems(updated: List<FloatingWordFieldConfig>) {
        submitList(updated.toList())
    }

    fun moveItem(from: Int, to: Int) {
        val current = currentList
        if (from == to || from !in current.indices || to !in current.indices) return
        val updated = current.toMutableList()
        val item = updated.removeAt(from)
        updated.add(to, item)
        submitList(updated)
        onChanged(updated)
    }

    inner class FieldConfigViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cbEnable: CheckBox = view.findViewById(R.id.cbEnable)
        private val tvLabel: TextView = view.findViewById(R.id.tvFieldLabel)
        private val tvSize: TextView = view.findViewById(R.id.tvSizeValue)
        private val btnMinus: TextView = view.findViewById(R.id.btnSizeMinus)
        private val btnPlus: TextView = view.findViewById(R.id.btnSizePlus)

        fun bind(item: FloatingWordFieldConfig) {
            tvLabel.text = labelProvider(item.type)
            tvSize.text = item.fontSizeSp.toString()
            cbEnable.setOnCheckedChangeListener(null)
            cbEnable.isChecked = item.enabled
            cbEnable.setOnCheckedChangeListener { _, isChecked ->
                updateItem(item.copy(enabled = isChecked))
            }
            btnMinus.setOnClickListener { updateFontSize(item, -sizeStep(item.type)) }
            btnPlus.setOnClickListener { updateFontSize(item, sizeStep(item.type)) }
        }

        private fun updateFontSize(item: FloatingWordFieldConfig, delta: Int) {
            val range = if (item.type == FloatingWordFieldType.IMAGE) 60..200 else 10..30
            val updated = item.copy(fontSizeSp = (item.fontSizeSp + delta).coerceIn(range))
            updateItem(updated)
        }

        private fun updateItem(updated: FloatingWordFieldConfig) {
            val index = bindingAdapterPosition
            if (index == RecyclerView.NO_POSITION) return
            val current = currentList
            if (index !in current.indices) return
            val next = current.toMutableList()
            next[index] = updated
            submitList(next)
            onChanged(next)
        }

        private fun sizeStep(type: FloatingWordFieldType): Int {
            return if (type == FloatingWordFieldType.IMAGE) 10 else 1
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<FloatingWordFieldConfig>() {
            override fun areItemsTheSame(
                oldItem: FloatingWordFieldConfig,
                newItem: FloatingWordFieldConfig
            ): Boolean = oldItem.type == newItem.type

            override fun areContentsTheSame(
                oldItem: FloatingWordFieldConfig,
                newItem: FloatingWordFieldConfig
            ): Boolean = oldItem == newItem
        }
    }
}
