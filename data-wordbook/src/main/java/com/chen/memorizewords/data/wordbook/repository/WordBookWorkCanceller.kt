package com.chen.memorizewords.data.wordbook.repository

import android.content.Context
import androidx.work.WorkManager
import com.chen.memorizewords.data.wordbook.repository.download.WordBookDownloadWorkConstants
import com.chen.memorizewords.data.wordbook.repository.wordbook.update.CurrentWordBookUpdateWorkConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface WordBookWorkCanceller {
    fun cancel(bookId: Long)
}

class WorkManagerWordBookWorkCanceller @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) : WordBookWorkCanceller {
    override fun cancel(bookId: Long) {
        runCatching {
            val workManager = WorkManager.getInstance(appContext)
            workManager.cancelUniqueWork(WordBookDownloadWorkConstants.uniqueWorkName(bookId))
            workManager.cancelUniqueWork(CurrentWordBookUpdateWorkConstants.uniqueWorkName(bookId))
        }
    }
}
