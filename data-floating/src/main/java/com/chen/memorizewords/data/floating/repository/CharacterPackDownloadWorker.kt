package com.chen.memorizewords.data.floating.repository

import android.content.Context
import android.os.StatFs
import android.system.ErrnoException
import android.system.OsConstants
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chen.memorizewords.core.sprite.SpritePackContractValidator
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.sprite.SpritePackManifest
import com.chen.memorizewords.core.sprite.SpritePackManifestParser
import com.chen.memorizewords.data.floating.di.CharacterPackHttpClient
import com.chen.memorizewords.data.floating.local.CharacterPackConditionalWriteResult
import com.chen.memorizewords.data.floating.local.CharacterPackLocalStore
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadError
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import com.chen.memorizewords.domain.floating.service.FloatingActivationEvent
import com.chen.memorizewords.domain.floating.service.FloatingActivationEventReporter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ProtocolException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class CharacterPackDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        doWorkOnIoDispatcher()
    }

    private suspend fun doWorkOnIoDispatcher(): Result {
        val packId = inputData.getString(CharacterPackWork.KEY_PACK_ID).orEmpty()
        val packVersion = inputData.getInt(CharacterPackWork.KEY_PACK_VERSION, 0)
        val requestedManifestSchemaVersion = inputData.getInt(
            CharacterPackWork.KEY_MANIFEST_SCHEMA_VERSION, 0
        )
        val displayName = inputData.getString(CharacterPackWork.KEY_DISPLAY_NAME).orEmpty()
        val description = inputData.getString(CharacterPackWork.KEY_DESCRIPTION)
        val previewUrl = inputData.getString(CharacterPackWork.KEY_PREVIEW_URL)
        val packageUrl = inputData.getString(CharacterPackWork.KEY_PACKAGE_URL).orEmpty()
        val expectedSha256 = inputData.getString(CharacterPackWork.KEY_PACKAGE_SHA256).orEmpty()
        val expectedSize = inputData.getLong(CharacterPackWork.KEY_PACKAGE_SIZE, 0L)
        val selectAfterInstall = inputData.getBoolean(
            CharacterPackWork.KEY_SELECT_AFTER_INSTALL,
            false
        )
        val activationRequestId = inputData.getString(CharacterPackWork.KEY_ACTIVATION_REQUEST_ID)
        val downloadRequestId = id.toString()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CharacterPackWorkerEntryPoint::class.java
        )
        val store = entryPoint.store()
        val expectedManifestSchemaVersion = requestedManifestSchemaVersion
            .takeIf { it in SUPPORTED_MANIFEST_SCHEMA_VERSIONS }
            ?: store.catalog().firstOrNull {
                it.packId == packId && it.packVersion == packVersion
            }?.manifestSchemaVersion
            ?: 0
        val baseState = CharacterPackDownloadState(
            packId = packId,
            packVersion = packVersion,
            downloadRequestId = downloadRequestId,
            status = CharacterPackDownloadStatus.DOWNLOADING,
            totalBytes = expectedSize,
            selectAfterInstall = selectAfterInstall,
            activationRequestId = activationRequestId
        )
        val packageHttpUrl = packageUrl.toHttpUrlOrNull()
        if (
            !CharacterPackLocalStore.isSafePackId(packId) ||
            packVersion <= 0 ||
            expectedManifestSchemaVersion !in SUPPORTED_MANIFEST_SCHEMA_VERSIONS ||
            packageHttpUrl?.isHttps != true ||
            !expectedSha256.matches(Regex("[a-fA-F0-9]{64}")) ||
            expectedSize !in 1..CharacterPackLocalStore.MAX_PACKAGE_BYTES ||
            (activationRequestId != null &&
                !CharacterPackLocalStore.isValidRequestId(activationRequestId))
        ) {
            if (!CharacterPackLocalStore.isSafePackId(packId)) return Result.failure()
            return fail(
                entryPoint = entryPoint,
                store = store,
                state = baseState,
                error = CharacterPackValidationException("Invalid character download identity")
            )
        }

        when (store.updateDownloadIfCurrent(packId, downloadRequestId, baseState)) {
            CharacterPackConditionalWriteResult.UPDATED -> Unit
            CharacterPackConditionalWriteResult.STALE -> return Result.success()
            CharacterPackConditionalWriteResult.PERSISTENCE_FAILED -> return Result.failure()
        }
        reportEventSafely(entryPoint, FloatingActivationEvent.DOWNLOAD_STARTED, baseState)

        val taskRoot = File(applicationContext.cacheDir, "character_packs/$packId/$downloadRequestId")
        val zipFile = File(taskRoot, "package.zip.part")
        val packRoot = File(applicationContext.filesDir, "character_packs/$packId")
        val installRoot = File(packRoot, ".install-$packVersion-$downloadRequestId")
        val finalRoot = File(packRoot, "$packVersion-$downloadRequestId")
        var installationCommitted = false
        return try {
            ensureAvailableStorage(
                path = applicationContext.cacheDir,
                requiredBytes = CharacterPackStoragePolicy.requiredPeakBytes(expectedSize)
            )
            ensureAvailableStorage(
                path = applicationContext.filesDir,
                requiredBytes = CharacterPackStoragePolicy.requiredPeakBytes(expectedSize)
            )
            if (!taskRoot.mkdirs() && !taskRoot.isDirectory) {
                throw CharacterPackInstallationException("Unable to create download directory")
            }
            download(
                client = entryPoint.characterPackHttpClient(),
                url = packageUrl,
                target = zipFile,
                store = store,
                state = baseState
            )
            if (zipFile.length() != expectedSize) {
                throw CharacterPackValidationException("Character package size mismatch")
            }
            if (!sha256(zipFile).equals(expectedSha256, ignoreCase = true)) {
                throw CharacterPackValidationException("Character package checksum mismatch")
            }
            ensureAvailableStorage(
                path = applicationContext.filesDir,
                requiredBytes = CharacterPackStoragePolicy.requiredInstallBytes(expectedSize)
            )
            ensureWorkerActive()
            persistCurrent(
                store,
                baseState.copy(
                    status = CharacterPackDownloadStatus.INSTALLING,
                    downloadedBytes = zipFile.length(),
                    totalBytes = expectedSize,
                    progress = 100
                )
            )
            if (!packRoot.mkdirs() && !packRoot.isDirectory) {
                throw CharacterPackInstallationException("Unable to create character directory")
            }
            installRoot.deleteRecursively()
            if (!installRoot.mkdirs()) {
                throw CharacterPackInstallationException("Unable to create install directory")
            }
            val extractedEntries = extractSafely(zipFile, installRoot)
            val manifestFile = File(
                installRoot,
                CharacterPackPackageLayout.MANIFEST_FILE_NAME
            )
            val manifest = try {
                CharacterPackManifestFileReader.parse(
                    file = manifestFile,
                    maxBytes = CharacterPackLocalStore.MAX_MANIFEST_BYTES,
                    parser = SpritePackManifestParser()
                )
            } catch (error: Exception) {
                throw CharacterPackValidationException("Invalid character manifest", error)
            }
            if (
                manifest.packId != SpritePackId(packId) ||
                manifest.packVersion != packVersion ||
                manifest.schemaVersion != expectedManifestSchemaVersion
            ) {
                throw CharacterPackValidationException("Character package identity mismatch")
            }
            try {
                entryPoint.contractValidator().validate(manifest)
            } catch (error: Exception) {
                throw CharacterPackValidationException("Character manifest contract mismatch", error)
            }
            if (extractedEntries != CharacterPackPackageLayout.expectedEntries(manifest)) {
                throw CharacterPackValidationException("Invalid character package entries")
            }
            try {
                CharacterPackTextureFileValidator.validate(
                    root = installRoot,
                    manifest = manifest,
                    decodeLastWebpFrame = true,
                    validateKtx2RuntimeReadiness = true
                )
            } catch (error: Exception) {
                throw CharacterPackValidationException("Character textures are invalid", error)
            }
            ensureWorkerActive()
            ensureCurrent(store, baseState)

            val previouslyInstalled = store.installed(packId)
            if (finalRoot.exists()) {
                val installedPath = previouslyInstalled?.installedDirectory
                if (installedPath == finalRoot.absolutePath) {
                    installRoot.deleteRecursively()
                } else if (!finalRoot.deleteRecursively()) {
                    throw CharacterPackInstallationException("Unable to replace staged character pack")
                }
            }
            if (!finalRoot.exists() && !installRoot.renameTo(finalRoot)) {
                throw CharacterPackInstallationException("Unable to activate character package")
            }
            ensureWorkerActive()

            val completedState = baseState.copy(
                status = CharacterPackDownloadStatus.COMPLETED,
                downloadedBytes = zipFile.length(),
                totalBytes = expectedSize,
                progress = 100,
                errorCode = null,
                errorMessage = null
            )
            val pendingRuntimeValidation =
                manifest.schemaVersion == SpritePackManifest.KTX2_SCHEMA_VERSION
            val runtimeFallback = CharacterPackRuntimeInstallPolicy.fallbackFor(
                required = pendingRuntimeValidation,
                previous = previouslyInstalled
            )
            val installed = InstalledCharacterPack(
                packId = packId,
                packVersion = packVersion,
                displayName = displayName.ifBlank { packId },
                description = description,
                previewUrl = previewUrl,
                installedDirectory = finalRoot.absolutePath,
                installedAtMs = System.currentTimeMillis(),
                pendingRuntimeValidation = pendingRuntimeValidation,
                lastKnownGoodVersion = runtimeFallback?.packVersion,
                lastKnownGoodDirectory = runtimeFallback?.installedDirectory
            )
            when (
                store.commitInstallationIfCurrent(
                    packId = packId,
                    downloadRequestId = downloadRequestId,
                    installed = installed,
                    completedDownload = completedState
                )
            ) {
                CharacterPackConditionalWriteResult.UPDATED -> Unit
                CharacterPackConditionalWriteResult.STALE -> throw ObsoleteCharacterPackWorkException()
                CharacterPackConditionalWriteResult.PERSISTENCE_FAILED ->
                    throw CharacterPackPersistenceException()
            }
            installationCommitted = true
            cleanupReplacedVersions(
                previous = previouslyInstalled,
                installed = installed,
                packRoot = packRoot
            )

            reportEventSafely(
                entryPoint = entryPoint,
                event = FloatingActivationEvent.DOWNLOAD_SUCCEEDED,
                state = completedState,
                extraAttributes = mapOf(
                    "selectionDeferredToForeground" to completedState.selectAfterInstall.toString()
                )
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ObsoleteCharacterPackWorkException) {
            Result.success()
        } catch (network: CharacterPackNetworkException) {
            if (network.retryable && runAttemptCount < MAX_NETWORK_RETRIES) {
                when (
                    store.updateDownloadIfCurrent(
                        packId,
                        downloadRequestId,
                        baseState.copy(status = CharacterPackDownloadStatus.QUEUED)
                    )
                ) {
                    CharacterPackConditionalWriteResult.UPDATED -> Result.retry()
                    CharacterPackConditionalWriteResult.STALE -> Result.success()
                    CharacterPackConditionalWriteResult.PERSISTENCE_FAILED -> Result.failure()
                }
            } else {
                fail(entryPoint, store, baseState, network)
            }
        } catch (error: Exception) {
            fail(entryPoint, store, baseState, error)
        } finally {
            deleteRecursivelySafely(installRoot)
            deleteRecursivelySafely(taskRoot)
            deleteEmptyDirectorySafely(taskRoot.parentFile)
            val finalDirectoryIsRetained = installationCommitted || runCatching {
                store.installed(packId)?.installedDirectory == finalRoot.absolutePath
            }.getOrDefault(false)
            if (!finalDirectoryIsRetained) {
                deleteRecursivelySafely(finalRoot)
            }
        }
    }

    private suspend fun download(
        client: OkHttpClient,
        url: String,
        target: File,
        store: CharacterPackLocalStore,
        state: CharacterPackDownloadState
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/zip, application/octet-stream;q=0.9, */*;q=0.8")
            .get()
            .build()
        ensureWorkerActive()
        val call = client.newCall(request)
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        if (isStopped) {
            call.cancel()
            cancellationHandle?.dispose()
            throw CancellationException()
        }
        try {
            val response = try {
                call.execute()
            } catch (error: IOException) {
                if (isStopped || call.isCanceled()) throw CancellationException()
                throw CharacterPackNetworkException(
                    message = "Character package request failed",
                    retryable = CharacterPackDownloadPolicy.isRetryableTransportFailure(error),
                    cause = error
                )
            }
            response.use {
                if (!it.isSuccessful) {
                    throw CharacterPackNetworkException(
                        message = "Character package request returned HTTP ${it.code}",
                        retryable = CharacterPackDownloadPolicy.isRetryableHttpStatus(it.code),
                        httpStatusCode = it.code
                    )
                }
                val body = it.body ?: throw CharacterPackNetworkException(
                    message = "Character package response body is empty",
                    retryable = true
                )
                val declaredLength = body.contentLength()
                if (!CharacterPackDownloadPolicy.contentLengthMatchesCatalog(
                        declaredLength = declaredLength,
                        catalogLength = state.totalBytes
                    )
                ) {
                    throw CharacterPackValidationException(
                        "Character package Content-Length mismatch"
                    )
                }
                val total = state.totalBytes
                body.byteStream().use { input ->
                    FileOutputStream(target, false).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        var lastReport = 0L
                        while (true) {
                            ensureWorkerActive()
                            val read = try {
                                input.read(buffer)
                            } catch (error: IOException) {
                                if (isStopped || call.isCanceled()) throw CancellationException()
                                throw CharacterPackNetworkException(
                                    message = "Character package response was interrupted",
                                    retryable = CharacterPackDownloadPolicy
                                        .isRetryableTransportFailure(error),
                                    cause = error
                                )
                            }
                            if (read < 0) break
                            downloaded += read
                            if (
                                downloaded > state.totalBytes ||
                                downloaded > CharacterPackLocalStore.MAX_PACKAGE_BYTES
                            ) {
                                throw CharacterPackValidationException(
                                    "Character package is too large"
                                )
                            }
                            output.write(buffer, 0, read)
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                                lastReport = now
                                report(store, state, downloaded, total)
                            }
                        }
                        output.fd.sync()
                        report(store, state, downloaded, total)
                    }
                }
            }
        } finally {
            cancellationHandle?.dispose()
        }
    }

    private fun report(
        store: CharacterPackLocalStore,
        state: CharacterPackDownloadState,
        downloaded: Long,
        total: Long
    ) {
        val progress = if (total > 0L) {
            ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
        } else {
            0
        }
        persistCurrent(
            store,
            state.copy(
                status = CharacterPackDownloadStatus.DOWNLOADING,
                downloadedBytes = downloaded,
                totalBytes = total,
                progress = progress
            )
        )
        setProgressAsync(workDataOf(CharacterPackWork.KEY_PROGRESS to progress))
    }

    private fun extractSafely(zipFile: File, target: File): Set<String> {
        val archive = zipFile.readBytes()
        val centralDirectoryEntries = validateCentralDirectory(archive)
        var entryCount = 0
        var totalBytes = 0L
        val names = mutableSetOf<String>()
        try {
            ZipInputStream(ByteArrayInputStream(archive).buffered()).use { zip ->
                while (true) {
                    ensureWorkerActive()
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRY_COUNT || entry.isDirectory) {
                        throw CharacterPackValidationException("Invalid character package entries")
                    }
                    val name = entry.name.replace('\\', '/')
                    val lower = name.lowercase(Locale.ROOT)
                    if (
                        name.isBlank() || name.startsWith('/') || name.contains('/') ||
                        name == ".." || name.contains("../") || name.contains(':') ||
                        !names.add(name) || lower.endsWith(".zip") || lower.endsWith(".apk") ||
                        lower.endsWith(".jar")
                    ) {
                        throw CharacterPackValidationException("Unsafe character package entry")
                    }
                    val entryLimit = if (name == "manifest.json") {
                        CharacterPackLocalStore.MAX_MANIFEST_BYTES
                    } else {
                        MAX_UNCOMPRESSED_BYTES
                    }
                    if (entry.size > entryLimit) {
                        throw CharacterPackValidationException("Character package entry is too large")
                    }
                    val output = File(target, name)
                    output.outputStream().buffered().use { fileOutput ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            ensureWorkerActive()
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            totalBytes += read
                            if (entryBytes > entryLimit || totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw CharacterPackValidationException(
                                    "Character package expands beyond limit"
                                )
                            }
                            fileOutput.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: ZipException) {
            throw CharacterPackValidationException("Invalid ZIP data", error)
        }
        if (entryCount != centralDirectoryEntries) {
            throw CharacterPackValidationException("ZIP entry count mismatch")
        }
        return names
    }

    private fun validateCentralDirectory(archive: ByteArray): Int {
        val eocd = findEndOfCentralDirectory(archive)
        if (eocd < 0) throw CharacterPackValidationException("Invalid ZIP directory")
        val diskNumber = unsignedShort(archive, eocd + 4)
        val centralDiskNumber = unsignedShort(archive, eocd + 6)
        val entriesOnDisk = unsignedShort(archive, eocd + 8)
        val entryCount = unsignedShort(archive, eocd + 10)
        val centralSize = unsignedInt(archive, eocd + 12)
        val centralOffset = unsignedInt(archive, eocd + 16)
        val commentLength = unsignedShort(archive, eocd + 20)
        if (
            diskNumber != 0 || centralDiskNumber != 0 || entriesOnDisk != entryCount ||
            entryCount > MAX_ENTRY_COUNT ||
            entryCount == 0xffff || centralSize == 0xffffffffL ||
            centralOffset == 0xffffffffL || commentLength < 0 ||
            eocd.toLong() + 22L + commentLength != archive.size.toLong() ||
            centralOffset + centralSize != eocd.toLong()
        ) {
            throw CharacterPackValidationException("ZIP64 character packages are not supported")
        }
        var cursor = centralOffset.toInt()
        repeat(entryCount) {
            ensureWorkerActive()
            if (cursor + 46 > archive.size || unsignedInt(archive, cursor) != 0x02014b50L) {
                throw CharacterPackValidationException("Invalid ZIP central directory")
            }
            val generalPurposeFlags = unsignedShort(archive, cursor + 8)
            val compressionMethod = unsignedShort(archive, cursor + 10)
            if ((generalPurposeFlags and 0x1) != 0 || compressionMethod !in SUPPORTED_ZIP_METHODS) {
                throw CharacterPackValidationException("Unsupported ZIP entry encoding")
            }
            val hostSystem = (unsignedShort(archive, cursor + 4) ushr 8) and 0xff
            val unixMode = ((unsignedInt(archive, cursor + 38) ushr 16) and 0xffff).toInt()
            if (hostSystem == 3 && (unixMode and 0xf000) == 0xa000) {
                throw CharacterPackValidationException("Symbolic links are not allowed")
            }
            val next = cursor.toLong() + 46L +
                unsignedShort(archive, cursor + 28) +
                unsignedShort(archive, cursor + 30) +
                unsignedShort(archive, cursor + 32)
            if (next > archive.size) {
                throw CharacterPackValidationException("Invalid ZIP central directory")
            }
            cursor = next.toInt()
        }
        if (cursor.toLong() != centralOffset + centralSize) {
            throw CharacterPackValidationException("Invalid ZIP central directory size")
        }
        return entryCount
    }

    private fun findEndOfCentralDirectory(archive: ByteArray): Int {
        val minimum = maxOf(0, archive.size - 65_557)
        for (offset in archive.size - 22 downTo minimum) {
            if (unsignedInt(archive, offset) == 0x06054b50L) return offset
        }
        return -1
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) return -1L
        return (bytes[offset].toLong() and 0xffL) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xffL) shl 24)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(Locale.US, byte) }
    }

    private fun fail(
        entryPoint: CharacterPackWorkerEntryPoint,
        store: CharacterPackLocalStore,
        state: CharacterPackDownloadState,
        error: Exception
    ): Result {
        val failure = classifyFailure(error, state.totalBytes)
        val failedState = state.copy(
            status = CharacterPackDownloadStatus.FAILED,
            errorCode = failure.first,
            errorMessage = failure.second
        )
        return when (
            store.updateDownloadIfCurrent(state.packId, state.downloadRequestId.orEmpty(), failedState)
        ) {
            CharacterPackConditionalWriteResult.UPDATED -> {
                reportEventSafely(
                    entryPoint = entryPoint,
                    event = FloatingActivationEvent.DOWNLOAD_FAILED,
                    state = failedState,
                    extraAttributes = mapOf("error" to failure.first.name)
                )
                Result.failure()
            }
            CharacterPackConditionalWriteResult.STALE -> Result.success()
            CharacterPackConditionalWriteResult.PERSISTENCE_FAILED -> Result.failure()
        }
    }

    private fun eventAttributes(state: CharacterPackDownloadState): Map<String, String> {
        return buildMap {
            put("packId", state.packId)
            put("packVersion", state.packVersion.toString())
            put("downloadRequestId", state.downloadRequestId.orEmpty())
            state.activationRequestId?.let { requestId ->
                put("requestId", requestId)
                put("activationRequestId", requestId)
            }
        }
    }

    private fun reportEventSafely(
        entryPoint: CharacterPackWorkerEntryPoint,
        event: FloatingActivationEvent,
        state: CharacterPackDownloadState,
        extraAttributes: Map<String, String> = emptyMap()
    ) {
        runCatching {
            entryPoint.activationEventReporter().report(
                event,
                eventAttributes(state) + extraAttributes
            )
        }
    }

    private fun persistCurrent(
        store: CharacterPackLocalStore,
        state: CharacterPackDownloadState
    ) {
        when (
            store.updateDownloadIfCurrent(
                packId = state.packId,
                downloadRequestId = state.downloadRequestId.orEmpty(),
                updatedState = state
            )
        ) {
            CharacterPackConditionalWriteResult.UPDATED -> Unit
            CharacterPackConditionalWriteResult.STALE -> throw ObsoleteCharacterPackWorkException()
            CharacterPackConditionalWriteResult.PERSISTENCE_FAILED ->
                throw CharacterPackPersistenceException()
        }
    }

    private fun ensureCurrent(
        store: CharacterPackLocalStore,
        state: CharacterPackDownloadState
    ) {
        ensureWorkerActive()
        if (store.download(state.packId)?.downloadRequestId != state.downloadRequestId) {
            throw ObsoleteCharacterPackWorkException()
        }
    }

    private fun cleanupReplacedVersions(
        previous: InstalledCharacterPack?,
        installed: InstalledCharacterPack,
        packRoot: File
    ) {
        previous ?: return
        val safeRoot = try {
            packRoot.canonicalFile
        } catch (_: IOException) {
            return
        }
        val retainedDirectories = listOfNotNull(
            installed.installedDirectory,
            installed.lastKnownGoodDirectory
        ).mapNotNull { path ->
            managedInstalledDirectory(safeRoot, path)?.absolutePath
        }.toSet()
        listOfNotNull(
            previous.installedDirectory,
            previous.lastKnownGoodDirectory
        ).distinct().mapNotNull { path ->
            managedInstalledDirectory(safeRoot, path)
        }.filterNot { directory ->
            directory.absolutePath in retainedDirectories
        }.forEach { directory ->
            directory.deleteRecursively()
        }
    }

    private fun managedInstalledDirectory(safeRoot: File, path: String): File? {
        return try {
            File(path).canonicalFile.takeIf { directory ->
                directory.parentFile == safeRoot &&
                    CharacterPackLocalStore.isManagedInstalledDirectoryName(directory.name)
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun deleteRecursivelySafely(directory: File?) {
        if (directory == null) return
        runCatching {
            if (directory.exists()) directory.deleteRecursively()
        }
    }

    private fun deleteEmptyDirectorySafely(directory: File?) {
        if (directory == null) return
        runCatching {
            if (directory.isDirectory && directory.list().isNullOrEmpty()) directory.delete()
        }
    }

    private fun ensureWorkerActive() {
        if (isStopped) throw CancellationException()
    }

    private fun classifyFailure(
        error: Exception,
        packageSizeBytes: Long
    ): Pair<CharacterPackDownloadError, String> {
        return when {
            isStorageError(error) -> CharacterPackDownloadError.STORAGE to
                "存储空间不足，请至少释放约 ${CharacterPackStoragePolicy.requiredSpaceMiB(packageSizeBytes)} MB 后重试"
            error is CharacterPackNetworkException && error.httpStatusCode != null &&
                !error.retryable -> CharacterPackDownloadError.INVALID_PACKAGE to
                "角色资源暂不可用，请刷新角色列表后重试"
            error is CharacterPackNetworkException -> CharacterPackDownloadError.NETWORK to
                "网络连接异常，请检查网络后重试"
            error is CharacterPackValidationException ->
                CharacterPackDownloadError.INVALID_PACKAGE to "角色文件校验失败，请重试"
            error is CharacterPackInstallationException ||
                error is CharacterPackPersistenceException ||
                error is IOException ->
                CharacterPackDownloadError.INSTALLATION to "角色安装失败，请重试"
            else -> CharacterPackDownloadError.UNKNOWN to "角色下载失败，请重试"
        }
    }

    private fun isStorageError(error: Exception): Boolean {
        return generateSequence(error as Throwable?) { it.cause }.any { cause ->
            if (cause is CharacterPackStorageException) return@any true
            if (cause is ErrnoException && cause.errno == OsConstants.ENOSPC) return@any true
            cause.message?.let { message ->
                val normalized = message.lowercase(Locale.ROOT)
                normalized.contains("no space left") ||
                    normalized.contains("enospc") ||
                    normalized.contains("空间不足")
            } == true
        }
    }

    private fun ensureAvailableStorage(path: File, requiredBytes: Long) {
        val availableBytes = try {
            StatFs(path.absolutePath).availableBytes
        } catch (_: RuntimeException) {
            // Preflight is advisory; bounded writes still surface and classify ENOSPC precisely.
            return
        }
        if (availableBytes < requiredBytes) {
            throw CharacterPackStorageException(requiredBytes, availableBytes)
        }
    }

    companion object {
        private const val MAX_ENTRY_COUNT = 16
        private const val MAX_UNCOMPRESSED_BYTES = CharacterPackLocalStore.MAX_ATLAS_BYTES
        private const val MAX_NETWORK_RETRIES = 2
        private const val PROGRESS_INTERVAL_MS = 500L
        private val SUPPORTED_ZIP_METHODS = setOf(0, 8)
        private val SUPPORTED_MANIFEST_SCHEMA_VERSIONS = setOf(
            SpritePackManifest.KTX2_SCHEMA_VERSION
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CharacterPackWorkerEntryPoint {
    fun store(): CharacterPackLocalStore

    @CharacterPackHttpClient
    fun characterPackHttpClient(): OkHttpClient

    fun contractValidator(): SpritePackContractValidator
    fun activationEventReporter(): FloatingActivationEventReporter
}

private class CharacterPackValidationException(
    message: String,
    cause: Exception? = null
) : IllegalArgumentException(message, cause)

private class CharacterPackInstallationException(
    message: String,
    cause: Exception? = null
) : IOException(message, cause)

private class CharacterPackNetworkException(
    message: String,
    val retryable: Boolean,
    val httpStatusCode: Int? = null,
    cause: Exception? = null
) : IOException(message, cause)

private class CharacterPackStorageException(
    val requiredBytes: Long,
    val availableBytes: Long
) : IOException("Insufficient character pack storage")

private class ObsoleteCharacterPackWorkException : IllegalStateException()

internal data class CharacterPackRuntimeFallbackRevision(
    val packVersion: Int,
    val installedDirectory: String
)

internal object CharacterPackRuntimeInstallPolicy {
    fun fallbackFor(
        required: Boolean,
        previous: InstalledCharacterPack?
    ): CharacterPackRuntimeFallbackRevision? {
        if (!required || previous == null) return null
        if (!previous.pendingRuntimeValidation) {
            return CharacterPackRuntimeFallbackRevision(
                packVersion = previous.packVersion,
                installedDirectory = previous.installedDirectory
            )
        }
        val fallbackVersion = previous.lastKnownGoodVersion ?: return null
        val fallbackDirectory = previous.lastKnownGoodDirectory ?: return null
        if (fallbackVersion <= 0 || fallbackDirectory.isBlank()) return null
        return CharacterPackRuntimeFallbackRevision(
            packVersion = fallbackVersion,
            installedDirectory = fallbackDirectory
        )
    }
}
internal object CharacterPackDownloadPolicy {
    fun contentLengthMatchesCatalog(declaredLength: Long, catalogLength: Long): Boolean =
        declaredLength < 0L || declaredLength == catalogLength

    fun isRetryableHttpStatus(statusCode: Int): Boolean =
        statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599

    fun isRetryableTransportFailure(error: IOException): Boolean = when (error) {
        is SSLHandshakeException,
        is SSLPeerUnverifiedException,
        is ProtocolException -> false
        else -> true
    }

}

internal object CharacterPackStoragePolicy {
    private const val BYTES_PER_MIB = 1024L * 1024L
    private const val MIN_INSTALL_BYTES = 4L * BYTES_PER_MIB
    private const val SAFETY_BYTES = 2L * BYTES_PER_MIB
    private const val MAX_INSTALL_ESTIMATE_BYTES = 60L * BYTES_PER_MIB

    fun requiredInstallBytes(packageSizeBytes: Long): Long {
        val packageBytes = packageSizeBytes.coerceAtLeast(0L)
        val expandedEstimate = saturatingMultiply(packageBytes, 2L)
            .coerceIn(MIN_INSTALL_BYTES, MAX_INSTALL_ESTIMATE_BYTES)
        return saturatingAdd(expandedEstimate, SAFETY_BYTES)
    }

    fun requiredPeakBytes(packageSizeBytes: Long): Long = saturatingAdd(
        packageSizeBytes.coerceAtLeast(0L),
        requiredInstallBytes(packageSizeBytes)
    )

    fun requiredSpaceMiB(packageSizeBytes: Long): Long {
        val bytes = requiredPeakBytes(packageSizeBytes)
        return saturatingAdd(bytes, BYTES_PER_MIB - 1L) / BYTES_PER_MIB
    }

    private fun saturatingMultiply(left: Long, right: Long): Long {
        if (left <= 0L || right <= 0L) return 0L
        return if (left > Long.MAX_VALUE / right) Long.MAX_VALUE else left * right
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}
