package com.chen.memorizewords.feature.floatingreview.ui.settings

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.core.navigation.PracticeEntry
import com.chen.memorizewords.core.ui.fragment.BaseVmDbFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldType
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
import com.chen.memorizewords.feature.floatingreview.R
import com.chen.memorizewords.feature.floatingreview.databinding.ModuleFloatingReviewFragmentSettingsBinding
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FloatingReviewSettingsFragment :
    BaseVmDbFragment<FloatingReviewSettingsViewModel, ModuleFloatingReviewFragmentSettingsBinding>() {

    private data class SourceRowBinding(
        val containerId: Int,
        val radioId: Int,
        val labelId: Int
    )

    private data class OrderRowBinding(
        val containerId: Int,
        val labelId: Int,
        val checkId: Int
    )

    override val viewModel: FloatingReviewSettingsViewModel by lazy {
        ViewModelProvider(this)[FloatingReviewSettingsViewModel::class.java]
    }

    @Inject
    lateinit var practiceEntry: PracticeEntry

    @Inject
    lateinit var floatingWordEntry: FloatingWordEntry

    private var settings: FloatingWordSettings = FloatingWordSettings()
    private lateinit var adapter: FloatingWordFieldConfigAdapter
    private var ignoreViewUpdates: Boolean = false
    private var initialFloatingEnabledCaptured: Boolean = false
    private var wasFloatingEnabledOnEntry: Boolean = false
    private var previewServiceStartedTemporarily: Boolean = false
    private var hasShownPreviewPermissionToast: Boolean = false

    private val pickWordsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val ids = practiceEntry.extractSelectedWordIds(result.data)?.toList() ?: emptyList()
        viewModel.onSelectedWordIdsChanged(ids)
    }

    override fun setLayout(): Int = R.layout.module_floating_review_fragment_settings

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        bindView(databind.root)
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect(::renderSettings)
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        val target =
            event.target as? FloatingReviewSettingsViewModel.Route.DispatchFloatingAction ?: return
        if (target.action == FloatingWordActions.ACTION_PREVIEW_CARD) {
            ensureFloatingPreview()
        } else {
            floatingWordEntry.dispatchServiceAction(requireContext(), target.action)
        }
    }

    override fun onStop() {
        if (previewServiceStartedTemporarily && !requireActivity().isChangingConfigurations) {
            floatingWordEntry.dispatchServiceAction(
                requireContext(),
                FloatingWordActions.ACTION_STOP
            )
            previewServiceStartedTemporarily = false
        }
        super.onStop()
    }

    private fun bindView(view: View) {
        val switchAutoStart = view.findViewById<SwitchMaterial>(R.id.switchFloatingAutoStart)
        val seekBallOpacity = view.findViewById<SeekBar>(R.id.seekBallOpacity)
        val seekCardOpacity = view.findViewById<SeekBar>(R.id.seekCardOpacity)

        view.findViewById<View>(R.id.layoutSourceCurrent).setOnClickListener {
            viewModel.onSourceTypeChanged(FloatingWordSourceType.CURRENT_BOOK)
        }
        view.findViewById<View>(R.id.layoutSourceSelf).setOnClickListener {
            viewModel.onSourceTypeChanged(FloatingWordSourceType.SELF_SELECT)
        }
        bindOrderClick(view, R.id.layoutOrderRandom, FloatingWordOrderType.RANDOM)
        bindOrderClick(view, R.id.layoutOrderMemory, FloatingWordOrderType.MEMORY_CURVE)
        bindOrderClick(view, R.id.layoutOrderAlphaAsc, FloatingWordOrderType.ALPHABETIC_ASC)
        bindOrderClick(view, R.id.layoutOrderAlphaDesc, FloatingWordOrderType.ALPHABETIC_DESC)
        bindOrderClick(view, R.id.layoutOrderLengthAsc, FloatingWordOrderType.LENGTH_ASC)
        bindOrderClick(view, R.id.layoutOrderLengthDesc, FloatingWordOrderType.LENGTH_DESC)

        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreViewUpdates) return@setOnCheckedChangeListener
            viewModel.onAutoStartChanged(isChecked)
        }

        seekBallOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (ignoreViewUpdates || !fromUser) return
                viewModel.onBallOpacityChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        seekCardOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (ignoreViewUpdates || !fromUser) return
                viewModel.onCardOpacityChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        view.findViewById<View>(R.id.btnPickWords).setOnClickListener {
            pickWordsLauncher.launch(
                practiceEntry.createWordPickerIntent(
                    context = requireContext(),
                    initialSelectedIds = settings.selectedWordIds.toLongArray()
                )
            )
        }

        setupFieldConfigs(view)
    }

    private fun renderSettings(updated: FloatingWordSettings) {
        settings = updated
        if (!initialFloatingEnabledCaptured) {
            initialFloatingEnabledCaptured = true
            wasFloatingEnabledOnEntry = updated.enabled
        }
        val root = view ?: return
        ignoreViewUpdates = true
        root.findViewById<TextView>(R.id.tvSelectedCount).text = getString(
            R.string.module_floating_review_selected_count,
            updated.selectedWordIds.size
        )
        root.findViewById<SwitchMaterial>(R.id.switchFloatingAutoStart).isChecked =
            updated.autoStartOnAppLaunch
        root.findViewById<SeekBar>(R.id.seekBallOpacity).progress = updated.ballOpacityPercent
        root.findViewById<TextView>(R.id.tvBallOpacityValue).text = getString(
            R.string.module_floating_review_card_opacity_value,
            updated.ballOpacityPercent
        )
        root.findViewById<SeekBar>(R.id.seekCardOpacity).progress = updated.cardOpacityPercent
        root.findViewById<TextView>(R.id.tvCardOpacityValue).text = getString(
            R.string.module_floating_review_card_opacity_value,
            updated.cardOpacityPercent
        )
        renderSourceSelection(root, updated.sourceType)
        renderOrderSelection(root, updated.orderType)
        if (::adapter.isInitialized) {
            adapter.replaceItems(updated.fieldConfigs)
        }
        ignoreViewUpdates = false
        updateSourceVisibility(updated)
    }

    private fun setupFieldConfigs(root: View) {
        val recyclerView = root.findViewById<RecyclerView>(R.id.rvFieldConfigs)
        adapter = FloatingWordFieldConfigAdapter(
            items = settings.fieldConfigs,
            labelProvider = ::fieldLabel,
            onChanged = viewModel::onFieldConfigsChanged
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        val helper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = true
        })
        helper.attachToRecyclerView(recyclerView)
    }

    private fun updateSourceVisibility(settings: FloatingWordSettings) {
        val layout = view?.findViewById<View>(R.id.layoutPickWords) ?: return
        layout.visibility =
            if (settings.sourceType == FloatingWordSourceType.SELF_SELECT) View.VISIBLE else View.GONE
    }

    private fun ensureFloatingPreview() {
        val context = context ?: return
        if (!Settings.canDrawOverlays(context)) {
            if (!hasShownPreviewPermissionToast) {
                hasShownPreviewPermissionToast = true
                Toast.makeText(
                    context,
                    R.string.module_floating_review_preview_permission_required,
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        if (!wasFloatingEnabledOnEntry && !previewServiceStartedTemporarily) {
            previewServiceStartedTemporarily = true
            floatingWordEntry.dispatchServiceAction(context, FloatingWordActions.ACTION_START)
        }

        floatingWordEntry.dispatchServiceAction(context, FloatingWordActions.ACTION_PREVIEW_CARD)
    }

    private fun bindOrderClick(view: View, containerId: Int, orderType: FloatingWordOrderType) {
        view.findViewById<View>(containerId).setOnClickListener {
            viewModel.onOrderTypeChanged(orderType)
        }
    }

    private fun renderSourceSelection(root: View, sourceType: FloatingWordSourceType) {
        updateSourceRow(
            root = root,
            binding = SourceRowBinding(
                containerId = R.id.layoutSourceCurrent,
                radioId = R.id.rbSourceCurrent,
                labelId = R.id.tvSourceCurrent
            ),
            selected = sourceType == FloatingWordSourceType.CURRENT_BOOK
        )
        updateSourceRow(
            root = root,
            binding = SourceRowBinding(
                containerId = R.id.layoutSourceSelf,
                radioId = R.id.rbSourceSelf,
                labelId = R.id.tvSourceSelf
            ),
            selected = sourceType == FloatingWordSourceType.SELF_SELECT
        )
    }

    private fun renderOrderSelection(root: View, orderType: FloatingWordOrderType) {
        updateOrderRow(
            root = root,
            binding = OrderRowBinding(
                containerId = R.id.layoutOrderRandom,
                labelId = R.id.tvOrderRandom,
                checkId = R.id.ivOrderRandomCheck
            ),
            selected = orderType == FloatingWordOrderType.RANDOM
        )
        updateOrderRow(
            root = root,
            binding = OrderRowBinding(
                containerId = R.id.layoutOrderMemory,
                labelId = R.id.tvOrderMemory,
                checkId = R.id.ivOrderMemoryCheck
            ),
            selected = orderType == FloatingWordOrderType.MEMORY_CURVE
        )
        updateOrderRow(
            root = root,
            binding = OrderRowBinding(
                containerId = R.id.layoutOrderAlphaAsc,
                labelId = R.id.tvOrderAlphaAsc,
                checkId = R.id.ivOrderAlphaAscCheck
            ),
            selected = orderType == FloatingWordOrderType.ALPHABETIC_ASC
        )
        updateOrderRow(
            root = root,
            binding = OrderRowBinding(
                containerId = R.id.layoutOrderAlphaDesc,
                labelId = R.id.tvOrderAlphaDesc,
                checkId = R.id.ivOrderAlphaDescCheck
            ),
            selected = orderType == FloatingWordOrderType.ALPHABETIC_DESC
        )
        updateOrderRow(
            root = root,
            binding = OrderRowBinding(
                containerId = R.id.layoutOrderLengthAsc,
                labelId = R.id.tvOrderLengthAsc,
                checkId = R.id.ivOrderLengthAscCheck
            ),
            selected = orderType == FloatingWordOrderType.LENGTH_ASC
        )
        updateOrderRow(
            root = root,
            binding = OrderRowBinding(
                containerId = R.id.layoutOrderLengthDesc,
                labelId = R.id.tvOrderLengthDesc,
                checkId = R.id.ivOrderLengthDescCheck
            ),
            selected = orderType == FloatingWordOrderType.LENGTH_DESC
        )
    }

    private fun updateSourceRow(root: View, binding: SourceRowBinding, selected: Boolean) {
        root.findViewById<View>(binding.containerId).setBackgroundResource(
            if (selected) {
                R.drawable.module_floating_review_bg_settings_row_selected
            } else {
                R.drawable.module_floating_review_bg_settings_row_idle
            }
        )
        root.findViewById<RadioButton>(binding.radioId).isChecked = selected
        root.findViewById<TextView>(binding.labelId).setTextColor(
            android.graphics.Color.parseColor(if (selected) "#111827" else "#4B5563")
        )
    }

    private fun updateOrderRow(root: View, binding: OrderRowBinding, selected: Boolean) {
        root.findViewById<View>(binding.containerId).setBackgroundResource(
            if (selected) {
                R.drawable.module_floating_review_bg_settings_row_selected
            } else {
                R.drawable.module_floating_review_bg_settings_row_idle
            }
        )
        root.findViewById<TextView>(binding.labelId).setTextColor(
            android.graphics.Color.parseColor(if (selected) "#0A5FD3" else "#4B5563")
        )
        root.findViewById<View>(binding.checkId).visibility = if (selected) View.VISIBLE else View.GONE
    }

    private fun fieldLabel(type: FloatingWordFieldType): String {
        return when (type) {
            FloatingWordFieldType.WORD -> getString(R.string.module_floating_review_field_word)
            FloatingWordFieldType.PHONETIC -> getString(R.string.module_floating_review_field_phonetic)
            FloatingWordFieldType.MEANING -> getString(R.string.module_floating_review_field_meaning)
            FloatingWordFieldType.PART_OF_SPEECH -> getString(R.string.module_floating_review_field_pos)
            FloatingWordFieldType.EXAMPLE -> getString(R.string.module_floating_review_field_example)
            FloatingWordFieldType.NOTE -> getString(R.string.module_floating_review_field_note)
            FloatingWordFieldType.IMAGE -> getString(R.string.module_floating_review_field_image)
        }
    }
}
