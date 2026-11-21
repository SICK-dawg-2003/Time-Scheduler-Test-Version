package com.example.time_scheduler.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.time_scheduler.util.AlarmSoundType
import com.example.time_scheduler.util.NotificationHelper

class ScheduleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("EXTRA_TASK_TITLE") ?: "Scheduled Task Alarm"
        val message = intent.getStringExtra("EXTRA_TASK_MESSAGE") ?: "Your scheduled task is starting now."
        val notificationId = intent.getIntExtra("EXTRA_NOTIFICATION_ID", (System.currentTimeMillis() % 10000).toInt())
        val soundTypeName = intent.getStringExtra("EXTRA_SOUND_TYPE") ?: AlarmSoundType.NOTIFICATION.name
        val soundType = try { AlarmSoundType.valueOf(soundTypeName) } catch (e: Exception) { AlarmSoundType.NOTIFICATION }

        NotificationHelper.showReminderNotification(
            context = context,
            title = title,
            message = message,
            notificationId = notificationId,
            soundType = soundType
        )
    }
}
