package com.chen.memorizewords.data.repository.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chen.memorizewords.domain.repository.download.DownloadRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val taskId = intent.getStringExtra(FileDownloadWorkConstants.EXTRA_TASK_ID).orEmpty()
        if (taskId.isBlank()) return
        val pendingResult = goAsync()
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            DownloadActionEntryPoint::class.java
        )
        val repository = entryPoint.downloadRepository()
        when (intent.action) {
            FileDownloadWorkConstants.ACTION_PAUSE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    repository.pause(taskId)
                    pendingResult.finish()
                }
            }

            FileDownloadWorkConstants.ACTION_RESUME -> {
                CoroutineScope(Dispatchers.IO).launch {
                    repository.resume(taskId)
                    pendingResult.finish()
                }
            }

            FileDownloadWorkConstants.ACTION_CANCEL -> {
                CoroutineScope(Dispatchers.IO).launch {
                    repository.cancel(taskId)
                    pendingResult.finish()
                }
            }
            else -> pendingResult.finish()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadActionEntryPoint {
    fun downloadRepository(): DownloadRepository
}
