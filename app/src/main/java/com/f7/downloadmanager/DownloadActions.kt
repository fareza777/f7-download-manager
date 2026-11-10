package com.f7.downloadmanager

import android.content.Context
import androidx.core.content.ContextCompat
import com.f7.core.download.service.DownloadService

object DownloadActions {

    private fun send(context: Context, action: String, id: Long) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                DownloadService.intent(context, action).putExtra(DownloadService.EXTRA_ID, id)
            )
        }
    }

    suspend fun pause(context: Context, id: Long) {
        val app = context.applicationContext as F7Application
        app.graph.repository.pause(id)
        send(context, DownloadService.ACTION_PAUSE, id)
    }

    suspend fun resume(context: Context, id: Long) {
        val app = context.applicationContext as F7Application
        app.graph.repository.queue(id)
        app.graph.startServiceSafely()
    }

    suspend fun retry(context: Context, id: Long) {
        val app = context.applicationContext as F7Application
        app.graph.repository.queue(id)
        app.graph.startServiceSafely()
    }

    suspend fun cancel(context: Context, id: Long) {
        send(context, DownloadService.ACTION_CANCEL, id)
    }

    suspend fun pauseAll(context: Context) {
        send(context, DownloadService.ACTION_PAUSE_ALL, -1)
    }

    suspend fun resumeAll(context: Context) {
        send(context, DownloadService.ACTION_RESUME_ALL, -1)
    }

    suspend fun retryFailed(context: Context) {
        send(context, DownloadService.ACTION_RETRY_FAILED, -1)
    }

    suspend fun delete(context: Context, id: Long, deleteFile: Boolean) {
        val app = context.applicationContext as F7Application
        app.graph.repository.delete(id, deleteFile)
    }
}

