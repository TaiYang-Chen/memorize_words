package com.chen.memorizewords.domain.floating.model

data class CharacterPackCatalogItem(
    val packId: String,
    val packVersion: Int,
    val displayName: String,
    val description: String? = null,
    val sortOrder: Int = 0,
    /**
     * The server-managed default for users who have never applied a character pack.
     * It is metadata only: an existing user's applied pack is always resolved by the server.
     */
    val isDefault: Boolean = false,
    val previewUrl: String,
    val packageUrl: String,
    val packageSha256: String,
    val packageSizeBytes: Long,
    val manifestSchemaVersion: Int,
    val updatedAtMs: Long
)

/** Result of resolving the server-managed character applied to the current user. */
sealed interface CharacterPackResolution {
    data class Resolved(val item: CharacterPackCatalogItem) : CharacterPackResolution

    /** The user's prior applied character is no longer usable and requires an explicit choice. */
    data object SelectionRequired : CharacterPackResolution
}

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

enum class CharacterPackDownloadError {
    NETWORK,
    STORAGE,
    INVALID_PACKAGE,
    INSTALLATION,
    UNKNOWN
}

data class CharacterPackDownloadState(
    val packId: String,
    val packVersion: Int = 0,
    val downloadRequestId: String? = null,
    val status: CharacterPackDownloadStatus = CharacterPackDownloadStatus.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val errorCode: CharacterPackDownloadError? = null,
    val selectAfterInstall: Boolean = false,
    val activationRequestId: String? = null
)
