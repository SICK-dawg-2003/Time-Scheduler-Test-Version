package com.example.time_scheduler.util

import android.R
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.time_scheduler.MainActivity
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.service.ScheduleReminderReceiver
import java.time.LocalDateTime
import java.time.ZoneId

enum class AlarmSoundType(val displayName: String, val ringtoneType: Int) {
    NOTIFICATION("Notification Chime", RingtoneManager.TYPE_NOTIFICATION),
    ALARM("Loud Alarm Clock", RingtoneManager.TYPE_ALARM),
    RINGTONE("Phone Ringtone", RingtoneManager.TYPE_RINGTONE)
}

object NotificationHelper {

    fun showReminderNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = (System.currentTimeMillis() % 10000).toInt(),
        soundType: AlarmSoundType = AlarmSoundType.NOTIFICATION
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(soundType.ringtoneType)
        val channelId = "schedule_reminder_channel_" + soundType.name.lowercase()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Schedule Alarms (${soundType.displayName})",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Time Scheduler alarms with ${soundType.displayName}"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(
                            if (soundType == AlarmSoundType.ALARM) AudioAttributes.USAGE_ALARM
                            else AudioAttributes.USAGE_NOTIFICATION_EVENT
                        )
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setAutoCancel(true)
            .setContentIntent(pendingContentIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Schedules BOTH:
     * 1. The Lead-Time Reminder Alarm (e.g. 15 mins before start time).
     * 2. The Exact Start Threshold Alarm (e.g. when task start time hits).
     */
    fun scheduleTaskDualAlarms(
        context: Context,
        task: TaskItem,
        soundType: AlarmSoundType = AlarmSoundType.NOTIFICATION
    ): Int {
        var alarmsScheduled = 0

        // Cancel any existing alarms for this task first
        cancelTaskAlarms(context, task.id)

        // 1. Lead-Time Alarm (e.g., 5m, 15m, 30m, or 60m before)
        if (task.leadTimeMinutes > 0) {
            val leadSuccess = scheduleSingleAlarm(
                context = context,
                task = task,
                leadTimeMinutes = task.leadTimeMinutes,
                requestCode = (task.id + "_lead").hashCode(),
                title = "Upcoming Task in ${task.leadTimeMinutes}m: ${task.title}",
                message = "Starts at ${task.formattedTimeString} (${task.category.displayName})",
                soundType = soundType
            )
            if (leadSuccess) alarmsScheduled++
        }

        // 2. Exact Start Threshold Alarm (Hits at exact task start time)
        val exactSuccess = scheduleSingleAlarm(
            context = context,
            task = task,
            leadTimeMinutes = 0,
            requestCode = (task.id + "_exact").hashCode(),
            title = "Task Starting Now: ${task.title}",
            message = "Scheduled from ${task.formattedTimeString} (${task.category.displayName})",
            soundType = soundType
        )
        if (exactSuccess) alarmsScheduled++

        return alarmsScheduled
    }

    private fun scheduleSingleAlarm(
        context: Context,
        task: TaskItem,
        leadTimeMinutes: Int,
        requestCode: Int,
        title: String,
        message: String,
        soundType: AlarmSoundType
    ): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val taskDateTime = LocalDateTime.of(task.date, task.startTime)
        val triggerTime = if (leadTimeMinutes > 0) taskDateTime.minusMinutes(leadTimeMinutes.toLong()) else taskDateTime
        val triggerMillis = triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis()) {
            return false // Skip past times
        }

        val intent = Intent(context, ScheduleReminderReceiver::class.java).apply {
            putExtra("EXTRA_TASK_TITLE", title)
            putExtra("EXTRA_TASK_MESSAGE", message)
            putExtra("EXTRA_NOTIFICATION_ID", requestCode)
            putExtra("EXTRA_SOUND_TYPE", soundType.name)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            return true
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun cancelTaskAlarms(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleReminderReceiver::class.java)

        // Cancel Lead Alarm
        val leadIntent = PendingIntent.getBroadcast(
            context,
            (taskId + "_lead").hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (leadIntent != null) {
            alarmManager.cancel(leadIntent)
            leadIntent.cancel()
        }

        // Cancel Exact Threshold Alarm
        val exactIntent = PendingIntent.getBroadcast(
            context,
            (taskId + "_exact").hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (exactIntent != null) {
            alarmManager.cancel(exactIntent)
            exactIntent.cancel()
        }
    }
}
