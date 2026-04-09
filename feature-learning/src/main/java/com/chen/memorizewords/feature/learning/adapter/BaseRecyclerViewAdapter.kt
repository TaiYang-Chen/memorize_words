package com.chen.memorizewords.feature.learning.adapter

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

abstract class BaseRecyclerViewAdapter<ITEM, DB : ViewDataBinding>(
    diffCallback: DiffUtil.ItemCallback<ITEM>
) : ListAdapter<ITEM, BaseRecyclerViewAdapter.ViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(onCreateDataBinding(parent, viewType))
    }

    abstract fun onCreateDataBinding(parent: ViewGroup, viewType: Int): DB
    abstract fun onBindViewHolderDataBinding(dB: DB, iTEM: ITEM)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        onBindViewHolderDataBinding(holder.binding as DB, getItem(position))
        holder.binding.executePendingBindings()
    }

    class ViewHolder(val binding: ViewDataBinding) :
        RecyclerView.ViewHolder(binding.root) {
    }
}
