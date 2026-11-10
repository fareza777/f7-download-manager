package com.f7.core.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.f7.core.download.R
import com.f7.core.model.DownloadItem
import com.f7.core.model.DownloadStatus
import com.f7.core.model.F7Format

class DownloadNotifier(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val active = NotificationChannel(
            CHANNEL_ACTIVE,
            context.getString(R.string.f7_channel_active),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.f7_channel_active_desc)
            setShowBadge(false)
        }
        val done = NotificationChannel(
            CHANNEL_DONE,
            context.getString(R.string.f7_channel_done),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.f7_channel_done_desc) }
        val schedule = NotificationChannel(
            CHANNEL_SCHEDULE,
            context.getString(R.string.f7_channel_schedule),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.f7_channel_schedule_desc) }
        manager.createNotificationChannels(listOf(active, done, schedule))
    }

    fun serviceNotification(primary: DownloadItem?, activeCount: Int, totalSpeed: Long): Notification {
        val builder = baseBuilder(CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_f7_stat)
            .setContentIntent(openApp(primary?.id))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)

        if (primary == null) {
            return builder
                .setContentTitle(context.getString(R.string.f7_notif_preparing))
                .setContentText(context.getString(R.string.f7_notif_preparing_desc))
                .setProgress(0, 0, true)
                .build()
        }

        val indeterminate = primary.totalBytes <= 0
        val progress = (primary.progress * 100).toInt()
        val speedText = if (primary.speedBytesPerSec > 0) F7Format.speed(primary.speedBytesPerSec) else ""
        val detail = buildString {
            append(
                if (indeterminate) F7Format.bytes(primary.downloadedBytes)
                else "${F7Format.bytes(primary.downloadedBytes)} / ${F7Format.bytes(primary.totalBytes)}"
            )
            if (speedText.isNotEmpty()) append(" • ").append(speedText)
            if (activeCount > 1) append(" • ").append(context.getString(R.string.f7_notif_more, activeCount - 1))
        }
        builder
            .setContentTitle(primary.fileName)
            .setContentText(detail)
            .setProgress(100, progress, indeterminate)
            .setSubText(F7Format.speed(totalSpeed))
            .addAction(action(primary.id, ACTION_PAUSE, R.string.f7_action_pause))
            .addAction(action(primary.id, ACTION_CANCEL, R.string.f7_action_cancel))
        return builder.build()
    }

    fun progressNotification(item: DownloadItem): Notification {
        val builder = baseBuilder(CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_f7_stat)
            .setContentTitle(item.fileName)
            .setContentIntent(openApp(item.id))
            .setOnlyAlertOnce(true)
            .setOngoing(item.status.isActive)
            .setSilent(true)
            .setShowWhen(false)
            .addAction(
                if (item.status == DownloadStatus.PAUSED) {
                    action(item.id, ACTION_RESUME, R.string.f7_action_resume)
                } else {
                    action(item.id, ACTION_PAUSE, R.string.f7_action_pause)
                }
            )
            .addAction(action(item.id, ACTION_CANCEL, R.string.f7_action_cancel))

        val indeterminate = item.totalBytes <= 0
        builder.setProgress(100, (item.progress * 100).toInt(), indeterminate)
        val text = if (indeterminate) {
            "${F7Format.bytes(item.downloadedBytes)} • ${F7Format.speed(item.speedBytesPerSec)}"
        } else {
            "${F7Format.percent(item.progress)} • ${F7Format.bytes(item.downloadedBytes)} / ${F7Format.bytes(item.totalBytes)}"
        }
        builder.setContentText(text)
        builder.setSubText(F7Format.speed(item.speedBytesPerSec))
        return builder.build()
    }

    fun completed(item: DownloadItem): Notification = baseBuilder(CHANNEL_DONE)
        .setSmallIcon(R.drawable.ic_f7_stat)
        .setContentTitle(context.getString(R.string.f7_notif_completed))
        .setContentText(item.fileName)
        .setContentIntent(openApp(item.id))
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .build()

    fun failed(item: DownloadItem): Notification = baseBuilder(CHANNEL_DONE)
        .setSmallIcon(R.drawable.ic_f7_stat)
        .setContentTitle(context.getString(R.string.f7_notif_failed))
        .setContentText(item.fileName)
        .setContentIntent(openApp(item.id))
        .setAutoCancel(true)
        .build()

    fun noInternet(): Notification = baseBuilder(CHANNEL_ACTIVE)        .setSmallIcon(R.drawable.ic_f7_stat)
        .setContentTitle(context.getString(R.string.f7_notif_waiting_network))
        .setContentText(context.getString(R.string.f7_notif_waiting_network_desc))
        .setContentIntent(openApp(null))
        .setOngoing(true)
        .setSilent(true)
        .build()

    fun timeoutNotification(): Notification = baseBuilder(CHANNEL_DONE)
        .setSmallIcon(R.drawable.ic_f7_stat)
        .setContentTitle(context.getString(R.string.f7_notif_timeout))
        .setContentText(context.getString(R.string.f7_notif_timeout_desc))
        .setContentIntent(openApp(null))
        .setAutoCancel(true)
        .build()

    fun scheduleReady(title: String, text: String, scheduleId: Long): Notification = baseBuilder(CHANNEL_SCHEDULE)
        .setSmallIcon(R.drawable.ic_f7_stat)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(openApp(null))
        .setAutoCancel(true)
        .addAction(
            NotificationCompat.Action(
                0,
                context.getString(R.string.f7_action_start_now),
                PendingIntent.getForegroundService(
                    context,
                    (scheduleId + 7000).toInt(),
                    DownloadService.intent(context, DownloadService.ACTION_START),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        )
        .build()

    fun notifyProgress(item: DownloadItem) {
        runCatching { manager.notify(progressId(item.id), progressNotification(item)) }
    }

    fun updateService(notification: Notification) {
        runCatching { manager.notify(SERVICE_NOTIFICATION_ID, notification) }
    }

    fun cancelProgress(id: Long) {
        runCatching { manager.cancel(progressId(id)) }
    }

    fun notifyCompleted(item: DownloadItem) {
        runCatching { manager.notify(doneId(item.id), completed(item)) }
    }

    fun notifyFailed(item: DownloadItem) {
        runCatching { manager.notify(doneId(item.id), failed(item)) }
    }

    fun notifySchedule(id: Long, notification: Notification) {
        runCatching { manager.notify(scheduleNotifId(id), notification) }
    }

    fun cancel(id: Long) {
        runCatching { manager.cancel(progressId(id)) }
        runCatching { manager.cancel(doneId(id)) }
    }

    private fun baseBuilder(channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setColor(0xFF1F5AE0.toInt())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun action(id: Long, action: String, titleRes: Int): NotificationCompat.Action {
        val intent = DownloadService.intent(context, action).putExtra(DownloadService.EXTRA_ID, id)
        val pending = PendingIntent.getService(
            context,
            (id.toInt() * 10 + action.hashCode()).and(0xFFFF),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action(0, context.getString(titleRes), pending)
    }

    private fun openApp(downloadId: Long?): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName))
            .apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (downloadId != null) putExtra(EXTRA_OPEN_DOWNLOAD, downloadId)
            }
        return PendingIntent.getActivity(
            context,
            (downloadId ?: 0L).toInt() + 1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val CHANNEL_ACTIVE = "f7_downloads_active"
        const val CHANNEL_DONE = "f7_downloads_done"
        const val CHANNEL_SCHEDULE = "f7_schedule"
        const val SERVICE_NOTIFICATION_ID = 9001
        const val EXTRA_OPEN_DOWNLOAD = "open_download_id"
        private const val GROUP = "com.f7.downloads"

        fun progressId(id: Long): Int = 3000 + (id % 10_000).toInt()
        fun doneId(id: Long): Int = 13_000 + (id % 10_000).toInt()
        fun scheduleNotifId(id: Long): Int = 23_000 + (id % 10_000).toInt()

        const val ACTION_PAUSE = "com.f7.download.action.PAUSE"
        const val ACTION_RESUME = "com.f7.download.action.RESUME"
        const val ACTION_CANCEL = "com.f7.download.action.CANCEL"
    }
}
