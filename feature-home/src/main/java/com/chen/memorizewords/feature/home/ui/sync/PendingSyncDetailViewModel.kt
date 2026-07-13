package com.chen.memorizewords.feature.home.ui.sync

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.sync.service.SyncFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PendingSyncDetailViewModel @Inject constructor(
    syncFacade: SyncFacade,
    private val formatter: PendingSyncDetailFormatter
) : BaseViewModel() {

    private val expandedIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<PendingSyncDetailUiState> =
        combine(
            syncFacade.observePendingSyncRecords(),
            expandedIds
        ) { records, expanded ->
            val validExpandedIds = expanded.intersect(records.map { it.id }.toSet())
            PendingSyncDetailUiState(
                titleText = formatter.formatTitle(records.size),
                isEmpty = records.isEmpty(),
                items = records.map { record ->
                    formatter.toItemUi(
                        record = record,
                        isExpanded = validExpandedIds.contains(record.id)
                    )
                }
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PendingSyncDetailUiState(titleText = formatter.formatTitle(0))
        )

    fun onBackClicked() {
        back()
    }

    fun onItemClicked(id: String) {
        expandedIds.value = expandedIds.value.toMutableSet().apply {
            if (!add(id)) {
                remove(id)
            }
        }
    }
}
