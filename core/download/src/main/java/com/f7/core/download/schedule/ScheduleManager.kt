package com.f7.core.download.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.f7.core.data.DataCore
import com.f7.core.data.DownloadsRepository
import com.f7.core.data.NewDownload
import com.f7.core.data.db.ScheduleEntity
import com.f7.core.download.service.DownloadNotifier
import com.f7.core.download.service.DownloadService
import com.f7.core.model.DownloadKind
import com.f7.core.model.F7Format
import com.f7.core.model.FileCategory
import com.f7.core.model.RepeatRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Schedules use AlarmManager#setAndAllowWhileIdle, which needs no exact-alarm
 * permission (Play friendly) but may be delayed by Doze. If Android refuses to start
 * the foreground service from the alarm, a notification asks the user to start it.
 */
class ScheduleManager(
    private val context: Context,
    private val repository: DownloadsRepository,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleNext(schedule: ScheduleEntity) {
        if (!schedule.enabled) {
            cancel(schedule.id)
            return
        }
        val fireAt = schedule.nextRunAt ?: schedule.triggerAt
        if (fireAt <= System.currentTimeMillis()) return
        val pending = pendingIntent(schedule.id)
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
    }

    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        repository.schedulesOnce().filter { it.enabled }.forEach { schedule ->
            var fireAt = schedule.nextRunAt ?: schedule.triggerAt
            val repeat = runCatching { RepeatRule.valueOf(schedule.repeatRule) }.getOrDefault(RepeatRule.ONCE)
            if (fireAt <= now) {
                if (repeat == RepeatRule.ONCE) {
                    repository.updateSchedule(schedule.copy(enabled = false, nextRunAt = null))
                    cancel(schedule.id)
                    return@forEach
                }
                while (fireAt <= now) {
                    fireAt = nextOccurrence(fireAt, repeat)
                }
            }
            val updated = schedule.copy(nextRunAt = fireAt, triggerAt = schedule.triggerAt)
            repository.updateSchedule(updated)
            scheduleNext(updated)
        }
    }

    fun cancel(id: Long) {
        runCatching { alarmManager.cancel(pendingIntent(id)) }
    }

    private fun pendingIntent(id: Long): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
            .setAction(ScheduleReceiver.ACTION_FIRE)
            .putExtra(ScheduleReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context,
            (id % 100_000).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        fun nextOccurrence(previousFireAt: Long, repeat: RepeatRule): Long = when (repeat) {
            RepeatRule.DAILY -> previousFireAt + 24 * 60 * 60 * 1000L
            RepeatRule.WEEKLY -> previousFireAt + 7 * 24 * 60 * 60 * 1000L
            RepeatRule.ONCE -> previousFireAt
        }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DataCore.init(context)
                val repository = DataCore.repository
                val schedule = repository.scheduleById(id) ?: return@launch
                if (!schedule.enabled) return@launch
                val now = System.currentTimeMillis()
                val repeat = runCatching { RepeatRule.valueOf(schedule.repeatRule) }.getOrDefault(RepeatRule.ONCE)
                val next = if (repeat == RepeatRule.ONCE) null else {
                    var fire = schedule.nextRunAt ?: schedule.triggerAt
                    while (fire <= now) fire = ScheduleManager.nextOccurrence(fire, repeat)
                    fire
                }

                repository.enqueue(
                    NewDownload(
                        url = schedule.url,
                        fileName = schedule.fileName,
                        category = schedule.category?.let { runCatching { FileCategory.valueOf(it) }.getOrNull() },
                        kind = if (F7Format.isPlaylistUrl(schedule.url)) DownloadKind.HLS else DownloadKind.HTTP,
                    )
                )
                repository.updateSchedule(
                    schedule.copy(
                        lastRunAt = now,
                        nextRunAt = next,
                        enabled = next != null,
                    )
                )
                next?.let { fireTime ->
                    ScheduleManager(context, repository).scheduleNext(schedule.copy(nextRunAt = fireTime, enabled = true))
                }

                val started = runCatching {
                    DownloadService.start(context)
                    true
                }.getOrDefault(false)

                if (!started) {
                    val notifier = DownloadNotifier(context)
                    notifier.notifySchedule(
                        id,
                        notifier.scheduleReady(
                            title = schedule.fileName ?: context.getString(com.f7.core.download.R.string.f7_schedule_notification_title),
                            text = context.getString(com.f7.core.download.R.string.f7_schedule_notification_text),
                            scheduleId = id,
                        )
                    )
                }
            } catch (_: Throwable) {
                // A missed schedule must never crash the process.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.f7.download.schedule.FIRE"
        const val EXTRA_ID = "schedule_id"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DataCore.init(context)
                ScheduleManager(context, DataCore.repository).rescheduleAll()
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}

