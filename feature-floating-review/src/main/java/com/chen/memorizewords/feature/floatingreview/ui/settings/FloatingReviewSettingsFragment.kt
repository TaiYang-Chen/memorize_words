package com.chen.memorizewords.feature.floatingreview.ui.settings

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.chen.memorizewords.core.navigation.FloatingWordActions
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.core.navigation.PracticeEntry
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldType
import com.chen.memorizewords.domain.model.floating.FloatingWordOrderType
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.floating.FloatingWordSourceType
import com.chen.memorizewords.feature.floatingreview.R
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FloatingReviewSettingsFragment : Fragment() {

    private val viewModel: FloatingReviewSettingsViewModel by lazy {
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.module_floating_review_fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindView(view)
        collectState()
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
        val rgSource = view.findViewById<RadioGroup>(R.id.rgSource)
        val rgOrder = view.findViewById<RadioGroup>(R.id.rgOrder)
        val switchAutoStart = view.findViewById<SwitchMaterial>(R.id.switchFloatingAutoStart)
        val seekCardOpacity = view.findViewById<SeekBar>(R.id.seekCardOpacity)

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            requireActivity().finish()
        }

        rgSource.setOnCheckedChangeListener { _, checkedId ->
            if (ignoreViewUpdates) return@setOnCheckedChangeListener
            viewModel.onSourceTypeChanged(
                if (checkedId == R.id.rbSourceSelf) {
                    FloatingWordSourceType.SELF_SELECT
                } else {
                    FloatingWordSourceType.CURRENT_BOOK
                }
            )
        }

        rgOrder.setOnCheckedChangeListener { _, checkedId ->
            if (ignoreViewUpdates) return@setOnCheckedChangeListener
            viewModel.onOrderTypeChanged(idToOrder(checkedId))
        }

        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreViewUpdates) return@setOnCheckedChangeListener
            viewModel.onAutoStartChanged(isChecked)
        }

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

    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settings.collect(::renderSettings)
                }
                launch {
                    viewModel.uiEvent.collect { event ->
                        val route = event as? UiEvent.Navigation.Route ?: return@collect
                        val target =
                            route.target as? FloatingReviewSettingsViewModel.Route.DispatchFloatingAction
                                ?: return@collect
                        if (target.action == FloatingWordActions.ACTION_PREVIEW_CARD) {
                            ensureCardOpacityPreview()
                        } else {
                            floatingWordEntry.dispatchServiceAction(requireContext(), target.action)
                        }
                    }
                }
            }
        }
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
        root.findViewById<RadioGroup>(R.id.rgSource).check(
            if (updated.sourceType == FloatingWordSourceType.SELF_SELECT) {
                R.id.rbSourceSelf
            } else {
                R.id.rbSourceCurrent
            }
        )
        root.findViewById<RadioGroup>(R.id.rgOrder).check(orderToId(updated.orderType))
        root.findViewById<SwitchMaterial>(R.id.switchFloatingAutoStart).isChecked =
            updated.autoStartOnAppLaunch
        root.findViewById<SeekBar>(R.id.seekCardOpacity).progress = updated.cardOpacityPercent
        root.findViewById<TextView>(R.id.tvCardOpacityValue).text = getString(
            R.string.module_floating_review_card_opacity_value,
            updated.cardOpacityPercent
        )
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

    private fun ensureCardOpacityPreview() {
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

    private fun orderToId(orderType: FloatingWordOrderType): Int {
        return when (orderType) {
            FloatingWordOrderType.RANDOM -> R.id.rbOrderRandom
            FloatingWordOrderType.MEMORY_CURVE -> R.id.rbOrderMemory
            FloatingWordOrderType.ALPHABETIC_ASC -> R.id.rbOrderAlphaAsc
            FloatingWordOrderType.ALPHABETIC_DESC -> R.id.rbOrderAlphaDesc
            FloatingWordOrderType.LENGTH_ASC -> R.id.rbOrderLengthAsc
            FloatingWordOrderType.LENGTH_DESC -> R.id.rbOrderLengthDesc
        }
    }

    private fun idToOrder(checkedId: Int): FloatingWordOrderType {
        return when (checkedId) {
            R.id.rbOrderMemory -> FloatingWordOrderType.MEMORY_CURVE
            R.id.rbOrderAlphaAsc -> FloatingWordOrderType.ALPHABETIC_ASC
            R.id.rbOrderAlphaDesc -> FloatingWordOrderType.ALPHABETIC_DESC
            R.id.rbOrderLengthAsc -> FloatingWordOrderType.LENGTH_ASC
            R.id.rbOrderLengthDesc -> FloatingWordOrderType.LENGTH_DESC
            else -> FloatingWordOrderType.RANDOM
        }
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
