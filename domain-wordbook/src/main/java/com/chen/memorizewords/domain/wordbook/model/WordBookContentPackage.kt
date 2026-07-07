package com.chen.memorizewords.domain.wordbook.model

data class WordBookContentPackage(
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val contentType: String,
    val schemaVersion: Int,
    val contentVersion: Long
)
