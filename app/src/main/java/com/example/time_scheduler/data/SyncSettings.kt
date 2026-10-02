package com.example.time_scheduler.data

data class UserAccount(
    val name: String = "Alex Morgan",
    val email: String = "alex.morgan@example.com",
    val avatarUrl: String = "",
    val accountType: String = "Pro Plan Member"
)

data class NotificationSettings(
    val notificationsEnabled: Boolean = true,
    val leadTimeMinutes: Int = 15,
    val soundEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00"
)

data class ActivityStats(
    val tasksCompletedToday: Int = 6,
    val totalTasksToday: Int = 8,
    val weeklyProductivityHours: Double = 34.5,
    val streakDays: Int = 12,
    val favoriteCategory: Category = Category.WORK
) {
    val completionPercentage: Int
        get() = if (totalTasksToday > 0) ((tasksCompletedToday.toDouble() / totalTasksToday) * 100).toInt() else 0
}

data class CalendarSyncState(
    val providerName: String,
    val isConnected: Boolean,
    val accountEmail: String,
    val autoSync: Boolean = true,
    val lastSyncTime: String = "Just now",
    val isSyncing: Boolean = false
)

data class ProfileState(
    val account: UserAccount = UserAccount(),
    val notifications: NotificationSettings = NotificationSettings(),
    val activity: ActivityStats = ActivityStats(),
    val googleSync: CalendarSyncState = CalendarSyncState(
        providerName = "Google Calendar",
        isConnected = true,
        accountEmail = "alex.morgan@gmail.com",
        lastSyncTime = "10 minutes ago"
    ),
    val outlookSync: CalendarSyncState = CalendarSyncState(
        providerName = "Microsoft Outlook",
        isConnected = false,
        accountEmail = "alex.morgan@outlook.com",
        lastSyncTime = "Not connected"
    ),
    val appVersion: String = "1.0.0 (Build 102)"
)
