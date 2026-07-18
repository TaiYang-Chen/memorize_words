package com.chen.memorizewords.data.floating.repository

import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chen.memorizewords.core.sprite.SpritePackContractValidator
import com.chen.memorizewords.core.sprite.SpritePackId
import com.chen.memorizewords.core.sprite.SpritePackManifestParser
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.data.floating.local.CharacterPackLocalStore
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request

class CharacterPackDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val packId = inputData.getString(CharacterPackWork.KEY_PACK_ID).orEmpty()
        val packVersion = inputData.getInt(CharacterPackWork.KEY_PACK_VERSION, 0)
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
        if (
            !CharacterPackLocalStore.isSafePackId(packId) ||
            packVersion <= 0 ||
            packageUrl.isBlank() ||
            !expectedSha256.matches(Regex("[a-fA-F0-9]{64}")) ||
            expectedSize > MAX_PACKAGE_BYTES
        ) {
            return Result.failure()
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CharacterPackWorkerEntryPoint::class.java
        )
        val store = entryPoint.store()
        val baseState = CharacterPackDownloadState(
            packId = packId,
            packVersion = packVersion,
            status = CharacterPackDownloadStatus.DOWNLOADING,
            totalBytes = expectedSize,
            selectAfterInstall = selectAfterInstall
        )
        store.putDownload(baseState)

        val taskRoot = File(applicationContext.cacheDir, "character_packs/$packId/$packVersion")
        val zipFile = File(taskRoot, "package.zip.part")
        val staging = File(taskRoot, "staging")
        return try {
            taskRoot.mkdirs()
            download(
                client = entryPoint.okHttpClient(),
                url = packageUrl,
                target = zipFile,
                store = store,
                state = baseState
            )
            if (expectedSize > 0L && zipFile.length() != expectedSize) {
                throw CharacterPackInstallException("Character package size mismatch")
            }
            if (!sha256(zipFile).equals(expectedSha256, ignoreCase = true)) {
                throw CharacterPackInstallException("Character package checksum mismatch")
            }
            store.putDownload(
                baseState.copy(
                    status = CharacterPackDownloadStatus.INSTALLING,
                    downloadedBytes = zipFile.length(),
                    totalBytes = expectedSize.takeIf { it > 0L } ?: zipFile.length(),
                    progress = 100
                )
            )
            staging.deleteRecursively()
            staging.mkdirs()
            extractSafely(zipFile, staging)
            val manifestFile = File(staging, "manifest.json")
            val manifest = manifestFile.bufferedReader().use(SpritePackManifestParser()::parse)
            if (manifest.packId != SpritePackId(packId) || manifest.packVersion != packVersion) {
                throw CharacterPackInstallException("Character package identity mismatch")
            }
            entryPoint.contractValidator().validate(manifest)
            validateAtlas(staging, manifest.atlas.fileName, manifest.atlas.width, manifest.atlas.height)

            val packRoot = File(applicationContext.filesDir, "character_packs/$packId")
            val versionRoot = File(packRoot, packVersion.toString())
            packRoot.mkdirs()
            versionRoot.deleteRecursively()
            if (!staging.renameTo(versionRoot)) {
                throw CharacterPackInstallException("Unable to activate character package")
            }
            store.putInstalled(
                InstalledCharacterPack(
                    packId = packId,
                    packVersion = packVersion,
                    displayName = displayName.ifBlank { packId },
                    description = description,
                    previewUrl = previewUrl,
                    installedDirectory = versionRoot.absolutePath,
                    installedAtMs = System.currentTimeMillis()
                )
            )
            if (selectAfterInstall) {
                val settingsRepository = entryPoint.settingsRepository()
                val settings = settingsRepository.getSettings()
                settingsRepository.saveSettings(settings.copy(selectedCharacterPackId = packId))
                if (settings.enabled) {
                    entryPoint.floatingWordEntry().dispatchServiceAction(
                        applicationContext,
                        FloatingWordActions.ACTION_APPLY_CHARACTER_PACK
                    )
                }
            }
            store.putDownload(
                baseState.copy(
                    status = CharacterPackDownloadStatus.COMPLETED,
                    downloadedBytes = zipFile.length(),
                    totalBytes = expectedSize.takeIf { it > 0L } ?: zipFile.length(),
                    progress = 100
                )
            )
            zipFile.delete()
            taskRoot.deleteRecursively()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (network: IOException) {
            if (runAttemptCount < MAX_NETWORK_RETRIES) {
                store.putDownload(baseState.copy(status = CharacterPackDownloadStatus.QUEUED))
                Result.retry()
            } else {
                fail(store, baseState, network)
            }
        } catch (error: Throwable) {
            fail(store, baseState, error)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun download(
        client: OkHttpClient,
        url: String,
        target: File,
        store: CharacterPackLocalStore,
        state: CharacterPackDownloadState
    ) {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            val body = it.body ?: throw IOException("Empty response body")
            val total = body.contentLength().takeIf { length -> length > 0L } ?: state.totalBytes
            if (total > MAX_PACKAGE_BYTES) {
                throw CharacterPackInstallException("Character package is too large")
            }
            body.byteStream().use { input ->
                FileOutputStream(target, false).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastReport = 0L
                    while (true) {
                        if (isStopped) throw CancellationException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded > MAX_PACKAGE_BYTES) {
                            throw CharacterPackInstallException("Character package is too large")
                        }
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
    }

    private fun report(
        store: CharacterPackLocalStore,
        state: CharacterPackDownloadState,
        downloaded: Long,
        total: Long
    ) {
        val progress = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
        store.putDownload(
            state.copy(
                status = CharacterPackDownloadStatus.DOWNLOADING,
                downloadedBytes = downloaded,
                totalBytes = total,
                progress = progress
            )
        )
        setProgressAsync(workDataOf(CharacterPackWork.KEY_PROGRESS to progress))
    }

    private fun extractSafely(zipFile: File, target: File) {
        val archive = zipFile.readBytes()
        rejectSymbolicLinks(archive)
        var entryCount = 0
        var totalBytes = 0L
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(archive).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ENTRY_COUNT || entry.isDirectory) {
                    throw CharacterPackInstallException("Invalid character package entries")
                }
                val name = entry.name.replace('\\', '/')
                val lower = name.lowercase(Locale.ROOT)
                if (
                    name.isBlank() || name.startsWith('/') || name.contains('/') ||
                    name == ".." || name.contains("../") || name.contains(':') ||
                    !names.add(name) || lower.endsWith(".zip") || lower.endsWith(".apk") ||
                    lower.endsWith(".jar")
                ) {
                    throw CharacterPackInstallException("Unsafe character package entry")
                }
                val output = File(target, name)
                output.outputStream().buffered().use { fileOutput ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                            throw CharacterPackInstallException("Character package expands beyond limit")
                        }
                        fileOutput.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun rejectSymbolicLinks(archive: ByteArray) {
        val eocd = findEndOfCentralDirectory(archive)
        if (eocd < 0) throw CharacterPackInstallException("Invalid ZIP directory")
        val entryCount = unsignedShort(archive, eocd + 10)
        val centralOffset = unsignedInt(archive, eocd + 16)
        if (entryCount == 0xffff || centralOffset == 0xffffffffL || centralOffset > archive.size) {
            throw CharacterPackInstallException("ZIP64 character packages are not supported")
        }
        var cursor = centralOffset.toInt()
        repeat(entryCount) {
            if (cursor + 46 > archive.size || unsignedInt(archive, cursor) != 0x02014b50L) {
                throw CharacterPackInstallException("Invalid ZIP central directory")
            }
            val hostSystem = (unsignedShort(archive, cursor + 4) ushr 8) and 0xff
            val unixMode = ((unsignedInt(archive, cursor + 38) ushr 16) and 0xffff).toInt()
            if (hostSystem == 3 && (unixMode and 0xf000) == 0xa000) {
                throw CharacterPackInstallException("Symbolic links are not allowed")
            }
            val next = cursor.toLong() + 46L +
                unsignedShort(archive, cursor + 28) +
                unsignedShort(archive, cursor + 30) +
                unsignedShort(archive, cursor + 32)
            if (next > archive.size) {
                throw CharacterPackInstallException("Invalid ZIP central directory")
            }
            cursor = next.toInt()
        }
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

    private fun validateAtlas(root: File, fileName: String, expectedWidth: Int, expectedHeight: Int) {
        val atlas = File(root, fileName)
        if (!atlas.isFile) throw CharacterPackInstallException("Character atlas is missing")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(atlas.absolutePath, options)
        if (options.outWidth != expectedWidth || options.outHeight != expectedHeight) {
            throw CharacterPackInstallException("Character atlas dimensions mismatch")
        }
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
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun fail(
        store: CharacterPackLocalStore,
        state: CharacterPackDownloadState,
        error: Throwable
    ): Result {
        store.putDownload(
            state.copy(
                status = CharacterPackDownloadStatus.FAILED,
                errorMessage = error.message ?: "Character package installation failed"
            )
        )
        return Result.failure()
    }

    companion object {
        private const val MAX_ENTRY_COUNT = 8
        private const val MAX_UNCOMPRESSED_BYTES = 60L * 1024L * 1024L
        private const val MAX_PACKAGE_BYTES = 25L * 1024L * 1024L
        private const val MAX_NETWORK_RETRIES = 2
        private const val PROGRESS_INTERVAL_MS = 500L
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CharacterPackWorkerEntryPoint {
    fun store(): CharacterPackLocalStore
    fun okHttpClient(): OkHttpClient
    fun contractValidator(): SpritePackContractValidator
    fun settingsRepository(): FloatingWordSettingsRepository
    fun floatingWordEntry(): FloatingWordEntry
}

private class CharacterPackInstallException(message: String) : IllegalArgumentException(message)
