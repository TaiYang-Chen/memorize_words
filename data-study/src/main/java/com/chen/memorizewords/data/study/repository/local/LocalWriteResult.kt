package com.chen.memorizewords.data.study.repository.local

import com.chen.memorizewords.domain.sync.OutboxCommand

data class LocalWriteResult(
    val pendingOutboxCommands: List<OutboxCommand> = emptyList()
)
