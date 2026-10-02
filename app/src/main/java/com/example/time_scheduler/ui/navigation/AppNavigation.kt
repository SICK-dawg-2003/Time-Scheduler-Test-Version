package com.example.time_scheduler.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.time_scheduler.ui.ScheduleViewModel
import com.example.time_scheduler.ui.calendar.CalendarScreen
import com.example.time_scheduler.ui.camera.CameraScanScreen

enum class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    CALENDAR("calendar", "Calendar", Icons.Default.CalendarMonth),
    SCANNER("scanner", "Scanner", Icons.Default.CameraAlt)
}

@Composable
fun MainAppNavigation(
    viewModel: ScheduleViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(AppDestination.CALENDAR) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTabletOrExpanded = maxWidth > 600.dp

        if (isTabletOrExpanded) {
            // Tablet / Foldable Responsive Layout with Navigation Rail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    AppDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = currentDestination == destination,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title
                                )
                            },
                            label = { Text(destination.title) }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Main Content View
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (currentDestination) {
                        AppDestination.CALENDAR -> CalendarScreen(viewModel = viewModel)
                        AppDestination.SCANNER -> CameraScanScreen(viewModel = viewModel)
                    }
                }
            }
        } else {
            // Compact Phone Responsive Layout with Bottom Navigation Bar
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        AppDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentDestination == destination,
                                onClick = { currentDestination = destination },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.title
                                    )
                                },
                                label = { Text(destination.title) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentDestination) {
                        AppDestination.CALENDAR -> CalendarScreen(viewModel = viewModel)
                        AppDestination.SCANNER -> CameraScanScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
