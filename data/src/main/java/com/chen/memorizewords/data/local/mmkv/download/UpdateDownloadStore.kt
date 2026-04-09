package com.chen.memorizewords.data.local.mmkv.download

import com.chen.memorizewords.domain.model.download.DownloadCompletionAction
import com.chen.memorizewords.domain.model.download.DownloadStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class UpdateDownloadStore @Inject constructor(
    private val mmkv: MMKV,
    private val gson: Gson
) {

    data class DownloadRecord(
        val taskId: String,
        val url: String,
        val fileName: String,
        val mimeType: String,
        val displayTitle: String,
        val displayDesc: String,
        val destinationDir: String,
        val completionAction: DownloadCompletionAction,
        val filePath: String?,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val etag: String?,
        val status: DownloadStatus,
        val lastError: String?
    )

    private val lock = Any()
    private val recordsFlow = MutableStateFlow(loadAll())

    fun observeRecords(): StateFlow<Map<String, DownloadRecord>> = recordsFlow.asStateFlow()

    fun get(taskId: String): DownloadRecord? = recordsFlow.value[taskId]

    fun upsert(record: DownloadRecord) {
        updateInternal { current ->
            val updated = current.toMutableMap()
            updated[record.taskId] = record
            updated
        }
    }

    fun remove(taskId: String) {
        updateInternal { current ->
            val updated = current.toMutableMap()
            updated.remove(taskId)
            updated
        }
    }

    fun update(taskId: String, updater: (DownloadRecord) -> DownloadRecord) {
        updateInternal { current ->
            val existing = current[taskId] ?: return@updateInternal current
            val updated = current.toMutableMap()
            updated[taskId] = updater(existing)
            updated
        }
    }

    fun clear() {
        synchronized(lock) {
            recordsFlow.value = emptyMap()
            mmkv.removeValueForKey(KEY_DOWNLOAD_RECORDS)
        }
    }

    private fun updateInternal(transform: (Map<String, DownloadRecord>) -> Map<String, DownloadRecord>) {
        synchronized(lock) {
            val updated = transform(recordsFlow.value)
            recordsFlow.value = updated
            saveAll(updated)
        }
    }

    private fun loadAll(): Map<String, DownloadRecord> {
        val json = mmkv.decodeString(KEY_DOWNLOAD_RECORDS, null)?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, DownloadRecord>>() {}.type
            gson.fromJson<Map<String, DownloadRecord>>(json, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    private fun saveAll(records: Map<String, DownloadRecord>) {
        mmkv.encode(KEY_DOWNLOAD_RECORDS, gson.toJson(records))
    }

    private companion object {
        private const val KEY_DOWNLOAD_RECORDS = "download_records"
    }
}

