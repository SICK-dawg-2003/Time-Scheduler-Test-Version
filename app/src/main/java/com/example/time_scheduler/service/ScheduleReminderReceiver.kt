package com.example.time_scheduler.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.time_scheduler.util.NotificationHelper

class ScheduleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("EXTRA_TASK_TITLE") ?: "Upcoming Task Reminder"
        val message = intent.getStringExtra("EXTRA_TASK_MESSAGE") ?: "Your scheduled task is starting soon."

        NotificationHelper.showReminderNotification(
            context = context,
            title = title,
            message = message
        )
    }
}
