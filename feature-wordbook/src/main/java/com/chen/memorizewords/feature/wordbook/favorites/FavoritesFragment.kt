package com.chen.memorizewords.feature.wordbook.favorites

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.navigation.RouteNavigator
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.wordbook.R
import com.chen.memorizewords.feature.wordbook.databinding.ModuleWordbookFragmentFavoritesBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FavoritesFragment :
    BaseFragment<FavoritesViewModel, ModuleWordbookFragmentFavoritesBinding>() {

    override val viewModel: FavoritesViewModel by lazy {
        ViewModelProvider(this)[FavoritesViewModel::class.java]
    }

    @Inject
    lateinit var routeNavigator: RouteNavigator

    private val adapter: FavoritesPagingAdapter by lazy {
        FavoritesPagingAdapter(
            onItemClick = viewModel::onFavoriteClicked,
            onItemLongClick = viewModel::onFavoriteLongClicked
        )
    }
    private var pendingSwipePosition: Int? = null

    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.clearSelection()
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            selectionBackCallback
        )
        databind.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        databind.recyclerView.adapter = adapter
        databind.recyclerView.addItemDecoration(DateGroupDecoration(adapter))
        attachSwipeToDelete()
        databind.btnSelectionCancel.setOnClickListener {
            viewModel.clearSelection()
        }
        databind.btnSelectionDelete.setOnClickListener {
            viewModel.requestRemoveSelectedFavorites()
        }
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagingData.collect {
                        adapter.submitData(it)
                    }
                }
                launch {
                    viewModel.selectionState.collect { state ->
                        renderSelectionState(state)
                        adapter.setSelectionState(state)
                    }
                }
                launch {
                    adapter.loadStateFlow.collect { loadStates ->
                        val isEmpty = loadStates.refresh is LoadState.NotLoading &&
                            adapter.itemCount == 0
                        databind.emptyView.isVisible = isEmpty
                    }
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is AppRoute -> routeNavigator.navigate(target)
            else -> super.onNavigationRoute(event)
        }
    }

    override fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
        val action = event.action.orEmpty()
        when {
            action.startsWith("${FavoritesViewModel.ACTION_REMOVE_FAVORITE}:") -> {
                restorePendingSwipe()
                val wordId = action.substringAfter(":").toLongOrNull() ?: return
                viewModel.onRemoveFavoriteConfirmed(wordId)
            }

            action == FavoritesViewModel.ACTION_REMOVE_SELECTED_FAVORITES -> {
                viewModel.onRemoveSelectedFavoritesConfirmed()
            }

            else -> super.onConfirmDialog(event)
        }
    }

    override fun onCancelDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action.orEmpty().startsWith("${FavoritesViewModel.ACTION_REMOVE_FAVORITE}:")) {
            restorePendingSwipe()
            return
        }
        super.onCancelDialog(event)
    }

    override fun onUiEffect(effect: UiEffect) {
        when (effect) {
            FavoritesEffect.RefreshList -> adapter.refresh()
            else -> super.onUiEffect(effect)
        }
    }

    private fun renderSelectionState(state: FavoritesSelectionUiState) {
        val inSelectionMode = state.isSelectionMode
        selectionBackCallback.isEnabled = inSelectionMode
        databind.btnBack.isVisible = !inSelectionMode
        databind.tvToolbarTitle.isVisible = !inSelectionMode
        databind.btnSelectionCancel.isVisible = inSelectionMode
        databind.tvSelectionTitle.isVisible = inSelectionMode
        databind.btnSelectionDelete.isVisible = inSelectionMode
        databind.tvSelectionTitle.text = getString(
            R.string.feature_wordbook_favorites_selected_count,
            state.selectedCount
        )
    }

    private fun attachSwipeToDelete() {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT
        ) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return if (viewModel.selectionState.value.isSelectionMode) {
                    0
                } else {
                    super.getSwipeDirs(recyclerView, viewHolder)
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val item = adapter.getItemSafely(position)
                if (item == null) {
                    adapter.notifyItemChanged(position)
                    return
                }
                pendingSwipePosition = position
                viewModel.requestRemoveFavorite(item)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                drawDeleteBackground(c, viewHolder.itemView, dX)
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        })
        helper.attachToRecyclerView(databind.recyclerView)
    }

    private fun drawDeleteBackground(canvas: Canvas, itemView: View, dX: Float) {
        if (dX >= 0f) return
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(
                requireContext(),
                R.color.feature_wordbook_favorites_delete_background
            )
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(requireContext(), android.R.color.white)
            textSize = resources.getDimension(R.dimen.feature_wordbook_favorites_date_text_size)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val bounds = RectF(
            itemView.right + dX,
            itemView.top.toFloat(),
            itemView.right.toFloat(),
            itemView.bottom.toFloat()
        )
        canvas.drawRoundRect(
            bounds,
            resources.getDimension(R.dimen.feature_wordbook_favorites_card_corner_radius),
            resources.getDimension(R.dimen.feature_wordbook_favorites_card_corner_radius),
            backgroundPaint
        )
        val label = getString(R.string.feature_wordbook_favorites_delete)
        val baseline = bounds.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, bounds.centerX(), baseline, textPaint)
    }

    private fun restorePendingSwipe() {
        val position = pendingSwipePosition
        pendingSwipePosition = null
        if (position != null && position != RecyclerView.NO_POSITION) {
            adapter.notifyItemChanged(position)
        }
    }
}
