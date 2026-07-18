package com.chen.memorizewords.feature.floatingreview.ui.character

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.floatingreview.R
import com.chen.memorizewords.feature.floatingreview.databinding.ModuleFloatingReviewFragmentCharacterPacksBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CharacterPackFragment :
    BaseVmDbFragment<CharacterPackViewModel, ModuleFloatingReviewFragmentCharacterPacksBinding>() {

    override val viewModel: CharacterPackViewModel by viewModels()

    @Inject
    lateinit var floatingWordEntry: FloatingWordEntry

    private lateinit var adapter: CharacterPackAdapter

    override fun setLayout(): Int = R.layout.module_floating_review_fragment_character_packs

    override fun initView(savedInstanceState: Bundle?) {
        adapter = CharacterPackAdapter(
            onPrimary = viewModel::onPrimary,
            onCancel = viewModel::onCancel,
            onDelete = viewModel::onDelete
        )
        databind.rvCharacterPacks.layoutManager = LinearLayoutManager(requireContext())
        databind.rvCharacterPacks.adapter = adapter
        databind.btnCharacterBack.setOnClickListener { findNavController().navigateUp() }
        databind.btnCharacterRefresh.setOnClickListener { viewModel.refresh() }
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { items ->
                    adapter.submitItems(items)
                    databind.tvCharacterEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        if (event.target == CharacterPackViewModel.Route.ApplyCharacterPack) {
            floatingWordEntry.dispatchServiceAction(
                requireContext(),
                FloatingWordActions.ACTION_APPLY_CHARACTER_PACK
            )
        }
    }
}
