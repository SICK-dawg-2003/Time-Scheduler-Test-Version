package com.example.time_scheduler.ui.calendar

import android.media.Ringtone
import android.media.RingtoneManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.util.AlarmSoundType
import com.example.time_scheduler.util.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsSheet(
    tasks: List<TaskItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    var selectedLeadTimeMinutes by remember { mutableIntStateOf(15) }
    var selectedSoundType by remember { mutableStateOf(AlarmSoundType.NOTIFICATION) }
    
    // Ringtone preview state & automatic cleanup on sheet dismissal
    var currentRingtone by remember { mutableStateOf<Ringtone?>(null) }
    var isPlayingPreview by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            currentRingtone?.stop()
            currentRingtone = null
            isPlayingPreview = false
        }
    }

    val leadTimeOptions = listOf(
        5 to "5 mins before",
        15 to "15 mins before",
        30 to "30 mins before",
        60 to "1 hour before"
    )

    ModalBottomSheet(
        onDismissRequest = {
            currentRingtone?.stop()
            currentRingtone = null
            isPlayingPreview = false
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Notification & Alarm Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automated background task reminders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Lead Time Selector (LazyRow for 100% Consistent Horizontally-Scrollable Single Line Chips)
            Column {
                Text(
                    text = "Reminder Lead Time:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(leadTimeOptions) { (mins, label) ->
                        val isSelected = selectedLeadTimeMinutes == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedLeadTimeMinutes = mins },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }

            // Alarm Sound Type Selector & Audio Play/Stop Preview Controls
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Alarm Sound Type:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPlayingPreview) {
                            // Stop Button (⏹️)
                            IconButton(
                                onClick = {
                                    currentRingtone?.stop()
                                    currentRingtone = null
                                    isPlayingPreview = false
                                    Toast.makeText(context, "Audio preview stopped.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Preview",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            // Play Button (🔊)
                            IconButton(
                                onClick = {
                                    currentRingtone?.stop()
                                    val soundUri = RingtoneManager.getDefaultUri(selectedSoundType.ringtoneType)
                                    val ringtone = RingtoneManager.getRingtone(context, soundUri)
                                    currentRingtone = ringtone
                                    ringtone?.play()
                                    isPlayingPreview = true
                                    Toast.makeText(context, "Playing preview: ${selectedSoundType.displayName}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Play Preview",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AlarmSoundType.entries.toTypedArray()) { soundType ->
                        val isSelected = selectedSoundType == soundType
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                currentRingtone?.stop()
                                isPlayingPreview = false
                                selectedSoundType = soundType
                            },
                            label = { Text(soundType.displayName, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }

            // Schedule All Alarms Primary Button
            Button(
                onClick = {
                    currentRingtone?.stop()
                    isPlayingPreview = false

                    var scheduledCount = 0
                    tasks.forEach { task ->
                        val updatedTask = task.copy(leadTimeMinutes = selectedLeadTimeMinutes)
                        val count = NotificationHelper.scheduleTaskDualAlarms(context, updatedTask, soundType = selectedSoundType)
                        if (count > 0) scheduledCount += count
                    }
                    if (scheduledCount > 0) {
                        Toast.makeText(
                            context,
                            "Scheduled $scheduledCount background alarms with ${selectedSoundType.displayName}!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "No upcoming future tasks to schedule for today.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Schedule Dual Alarms (${tasks.size} Tasks)",
                    fontWeight = FontWeight.Bold
                )
            }

            // Instant Test Notification Outlined Button
            OutlinedButton(
                onClick = {
                    currentRingtone?.stop()
                    isPlayingPreview = false

                    NotificationHelper.showReminderNotification(
                        context = context,
                        title = "Time Scheduler Test Notification",
                        message = "Testing alarm sound: ${selectedSoundType.displayName}",
                        soundType = selectedSoundType
                    )
                    Toast.makeText(context, "Test notification sent with ${selectedSoundType.displayName}!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Test Notification (${selectedSoundType.displayName})")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
