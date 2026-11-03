package com.example.time_scheduler.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.time_scheduler.util.NotificationHelper

class ScheduleReminderService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskTitle = intent?.getStringExtra("TASK_TITLE") ?: "Upcoming Task"
        NotificationHelper.showReminderNotification(
            this,
            "Schedule Reminder",
            "Reminder for task: $taskTitle"
        )
        stopSelf()
        return START_NOT_STICKY
    }
}
