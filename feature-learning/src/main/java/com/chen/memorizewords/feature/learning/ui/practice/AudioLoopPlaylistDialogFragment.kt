package com.chen.memorizewords.feature.learning.ui.practice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.ui.practice.audioLoop.AudioLoopPlaybackService
import com.chen.memorizewords.feature.learning.ui.practice.audioLoop.AudioLoopPlaybackStore
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class AudioLoopPlaylistDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "audio_loop_playlist_dialog"
    }

    private val adapter = AudioLoopPlaylistAdapter { index ->
        AudioLoopPlaybackService.select(requireContext(), index)
        dismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_practice_audio_loop_playlist, container, false)
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        bottomSheet.setBackgroundResource(android.R.color.transparent)
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * 0.82f).toInt()
        }
        BottomSheetBehavior.from(bottomSheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_playlist)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        view.findViewById<View>(R.id.btn_close).setOnClickListener { dismiss() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AudioLoopPlaybackStore.state.collect { state ->
                    view.findViewById<TextView>(R.id.tv_playlist_subtitle).text = getString(
                        R.string.feature_learning_audio_loop_playlist_subtitle,
                        state.entries.size
                    )
                    adapter.submitList(
                        state.entries.mapIndexed { index, entry ->
                            AudioLoopPlaylistItem(
                                index = index,
                                word = entry.word,
                                meaning = entry.meaning,
                                phonetic = entry.phonetic,
                                isCurrent = index == state.currentIndex,
                                isCompleted = state.completedIds.contains(entry.id),
                                isFailed = state.failedIds.contains(entry.id)
                            )
                        }
                    )
                    if (state.entries.isEmpty()) {
                        dismissAllowingStateLoss()
                    }
                }
            }
        }
    }
}

private data class AudioLoopPlaylistItem(
    val index: Int,
    val word: String,
    val meaning: String,
    val phonetic: String,
    val isCurrent: Boolean,
    val isCompleted: Boolean,
    val isFailed: Boolean
)

private class AudioLoopPlaylistAdapter(
    private val onClick: (Int) -> Unit
) : ListAdapter<AudioLoopPlaylistItem, AudioLoopPlaylistViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioLoopPlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_practice_audio_loop_playlist, parent, false)
        return AudioLoopPlaylistViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: AudioLoopPlaylistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AudioLoopPlaylistItem>() {
            override fun areItemsTheSame(
                oldItem: AudioLoopPlaylistItem,
                newItem: AudioLoopPlaylistItem
            ): Boolean = oldItem.index == newItem.index

            override fun areContentsTheSame(
                oldItem: AudioLoopPlaylistItem,
                newItem: AudioLoopPlaylistItem
            ): Boolean = oldItem == newItem
        }
    }
}

private class AudioLoopPlaylistViewHolder(
    itemView: View,
    private val onClick: (Int) -> Unit
) : RecyclerView.ViewHolder(itemView) {

    private val tvIndex: TextView = itemView.findViewById(R.id.tv_playlist_index)
    private val tvWord: TextView = itemView.findViewById(R.id.tv_playlist_word)
    private val tvPhonetic: TextView = itemView.findViewById(R.id.tv_playlist_phonetic)
    private val tvMeaning: TextView = itemView.findViewById(R.id.tv_playlist_meaning)
    private val tvStatus: TextView = itemView.findViewById(R.id.tv_playlist_status)

    fun bind(item: AudioLoopPlaylistItem) {
        val context = itemView.context
        tvIndex.text = (item.index + 1).toString()
        tvWord.text = item.word
        tvPhonetic.text = item.phonetic
        tvPhonetic.isVisible = item.phonetic.isNotBlank()
        tvMeaning.text = item.meaning
        tvMeaning.isVisible = item.meaning.isNotBlank()
        tvStatus.text = when {
            item.isFailed -> context.getString(R.string.feature_learning_audio_loop_playlist_failed)
            item.isCurrent -> context.getString(R.string.feature_learning_audio_loop_playlist_current)
            item.isCompleted -> context.getString(R.string.feature_learning_audio_loop_playlist_completed)
            else -> context.getString(R.string.feature_learning_audio_loop_playlist_waiting)
        }
        itemView.isSelected = item.isCurrent
        itemView.setOnClickListener { onClick(item.index) }
    }
}
