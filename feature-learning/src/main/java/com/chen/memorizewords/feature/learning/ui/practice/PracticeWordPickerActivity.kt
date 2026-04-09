package com.chen.memorizewords.feature.learning

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.ActivityPracticeWordPickerBinding
import com.chen.memorizewords.feature.learning.ui.practice.PracticeWordPickerViewModel
import com.chen.memorizewords.core.navigation.PracticeEntryExtras
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PracticeWordPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_WORD_IDS = PracticeEntryExtras.EXTRA_SELECTED_WORD_IDS
        const val EXTRA_INITIAL_SELECTED_WORD_IDS =
            PracticeEntryExtras.EXTRA_INITIAL_SELECTED_WORD_IDS
    }

    private lateinit var binding: ActivityPracticeWordPickerBinding
    private val viewModel: PracticeWordPickerViewModel by viewModels()
    private val adapter = WordPickerAdapter { wordId -> viewModel.toggleSelection(wordId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPracticeWordPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.recyclerWords.layoutManager = LinearLayoutManager(this)
        binding.recyclerWords.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateQuery(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnSelectAll.setOnClickListener { viewModel.selectAllVisible() }
        binding.btnClear.setOnClickListener { viewModel.clearSelection() }
        binding.btnConfirm.setOnClickListener {
            val selected = viewModel.uiState.value.selectedIds
            if (selected.isEmpty()) {
                Toast.makeText(
                    this,
                    R.string.practice_word_picker_confirm_empty,
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            setResult(
                Activity.RESULT_OK,
                intent.apply { putExtra(EXTRA_SELECTED_WORD_IDS, selected.toLongArray()) }
            )
            finish()
        }

        viewModel.uiState.observeWith(this) { state ->
            val showEmptyState = state.isEmpty || state.isSearchEmpty
            adapter.submit(state.filteredWords, state.selectedIds)
            binding.tvSelectedCount.text = getString(
                R.string.practice_word_picker_selected_count,
                state.selectedIds.size
            )
            binding.tvEmpty.text = getString(
                if (state.isEmpty) {
                    R.string.practice_word_picker_empty
                } else {
                    R.string.practice_word_picker_search_empty
                }
            )
            binding.tvEmpty.isVisible = showEmptyState
            binding.recyclerWords.isVisible = !showEmptyState
            binding.btnSelectAll.isEnabled = state.filteredWords.isNotEmpty()
            binding.btnClear.isEnabled = state.selectedIds.isNotEmpty()
            binding.btnConfirm.isEnabled = !state.isEmpty
        }

        viewModel.loadWords(intent.getLongArrayExtra(EXTRA_INITIAL_SELECTED_WORD_IDS))
    }
}

private class WordPickerAdapter(
    private val onToggle: (Long) -> Unit
) : ListAdapter<WordPickerItem, WordPickerViewHolder>(DIFF) {

    fun submit(newItems: List<Word>, selectedIds: Set<Long>) {
        submitList(
            newItems.map { word ->
                WordPickerItem(
                    word = word,
                    selected = selectedIds.contains(word.id)
                )
            }
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordPickerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_practice_word_select, parent, false)
        return WordPickerViewHolder(view, onToggle)
    }

    override fun onBindViewHolder(holder: WordPickerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<WordPickerItem>() {
            override fun areItemsTheSame(
                oldItem: WordPickerItem,
                newItem: WordPickerItem
            ): Boolean = oldItem.word.id == newItem.word.id

            override fun areContentsTheSame(
                oldItem: WordPickerItem,
                newItem: WordPickerItem
            ): Boolean = oldItem == newItem
        }
    }
}

private data class WordPickerItem(
    val word: Word,
    val selected: Boolean
)

private class WordPickerViewHolder(
    itemView: android.view.View,
    private val onToggle: (Long) -> Unit
) : RecyclerView.ViewHolder(itemView) {

    private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
    private val tvWord: TextView = itemView.findViewById(R.id.tv_word)
    private val tvPhonetic: TextView = itemView.findViewById(R.id.tv_phonetic)

    fun bind(item: WordPickerItem) {
        val word = item.word
        tvWord.text = word.word
        tvPhonetic.text = word.phoneticUS ?: word.phoneticUK ?: ""
        checkbox.isChecked = item.selected
        itemView.setOnClickListener { onToggle(word.id) }
        checkbox.setOnClickListener { onToggle(word.id) }
    }
}

private fun <T> StateFlow<T>.observeWith(
    owner: LifecycleOwner,
    observer: (T) -> Unit
) {
    owner.lifecycleScope.launch {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect { observer(it) }
        }
    }
}
