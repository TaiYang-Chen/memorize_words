package com.chen.memorizewords.feature.home.ui.sync

data class PendingSyncDetailFieldUi(
    val label: String,
    val value: String
)

data class PendingSyncItemUi(
    val id: Long,
    val bizTypeLabel: String,
    val stateLabel: String,
    val operationLabel: String,
    val bizKeyText: String,
    val updatedAtText: String,
    val retryText: String,
    val failureText: String,
    val lastErrorText: String,
    val nextRetryAtText: String,
    val expandHintText: String,
    val detailHintText: String,
    val detailFields: List<PendingSyncDetailFieldUi>,
    val detailFieldsText: String,
    val rawPayloadText: String,
    val isExpanded: Boolean
)

data class PendingSyncDetailUiState(
    val titleText: String = "",
    val isEmpty: Boolean = true,
    val items: List<PendingSyncItemUi> = emptyList()
)
