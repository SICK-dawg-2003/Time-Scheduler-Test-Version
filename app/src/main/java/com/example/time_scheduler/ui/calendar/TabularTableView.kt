package com.example.time_scheduler.ui.calendar

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.time_scheduler.data.TaskItem

@Composable
fun TabularTableView(
    tasks: List<TaskItem>,
    onEditTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (isLandscape) 12.dp else 8.dp, vertical = 6.dp)
    ) {
        // Tabular Form Header (Identical Column Weights to Body)
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIME",
                    fontSize = if (isLandscape) 10.sp else 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1.3f)
                )
                Text(
                    text = "ACTIVITY",
                    fontSize = if (isLandscape) 10.sp else 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    text = "NOTES",
                    fontSize = if (isLandscape) 10.sp else 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1.8f)
                )
                Text(
                    text = "CATEGORY",
                    fontSize = if (isLandscape) 10.sp else 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(0.9f)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Tabular Rows List (Identical Column Weights to Header)
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No scheduled tasks found for this day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(tasks) { index, task ->
                    val rowBackground = if (index % 2 == 0) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }

                    Surface(
                        color = rowBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditTask(task) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Time Column (Weight 1.3f - Keeps 9:00 AM - 10:00 AM on 1 Single Line)
                            Text(
                                text = task.formattedTimeString,
                                fontSize = if (isLandscape) 10.sp else 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.3f)
                            )

                            // Activity Title Column (Weight 1.2f)
                            Text(
                                text = task.title,
                                fontSize = if (isLandscape) 10.5.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.2f)
                            )

                            // Notes/Description Column (Weight 1.8f)
                            Text(
                                text = task.description.ifBlank { "-" },
                                fontSize = if (isLandscape) 10.sp else 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.8f)
                            )

                            // Category Badge Column (Weight 0.9f - Centered)
                            Box(
                                modifier = Modifier.weight(0.9f),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = task.category.color.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(task.category.color)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = task.category.displayName,
                                            fontSize = if (isLandscape) 8.5.sp else 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}
