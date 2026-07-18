package com.chen.memorizewords.domain.floating.model

data class CharacterPackCatalogItem(
    val packId: String,
    val packVersion: Int,
    val displayName: String,
    val description: String? = null,
    val sortOrder: Int = 0,
    val previewUrl: String,
    val packageUrl: String,
    val packageSha256: String,
    val packageSizeBytes: Long,
    val manifestSchemaVersion: Int,
    val updatedAtMs: Long
)

data class InstalledCharacterPack(
    val packId: String,
    val packVersion: Int,
    val displayName: String,
    val description: String? = null,
    val previewUrl: String? = null,
    val installedDirectory: String,
    val installedAtMs: Long
)

enum class CharacterPackDownloadStatus {
    IDLE,
    QUEUED,
    DOWNLOADING,
    INSTALLING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class CharacterPackDownloadState(
    val packId: String,
    val packVersion: Int = 0,
    val status: CharacterPackDownloadStatus = CharacterPackDownloadStatus.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val selectAfterInstall: Boolean = false
)
