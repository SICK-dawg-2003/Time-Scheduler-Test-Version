package com.example.time_scheduler.ui.navigation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.time_scheduler.ui.ScheduleViewModel
import com.example.time_scheduler.ui.calendar.CalendarScreen
import com.example.time_scheduler.ui.calendar.CalendarViewMode
import com.example.time_scheduler.ui.camera.CameraScanScreen
import com.example.time_scheduler.ui.theme.AppThemeColor
import kotlinx.coroutines.launch

enum class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    CALENDAR("calendar", "Calendar", Icons.Default.CalendarMonth),
    SCANNER("scanner", "Scanner", Icons.Default.CropFree)
}

@Composable
fun MainAppNavigation(
    viewModel: ScheduleViewModel = viewModel(),
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    selectedThemeColor: AppThemeColor = AppThemeColor.VIOLET,
    onThemeColorSelected: (AppThemeColor) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val navPrefs = remember {
        context.getSharedPreferences("unisync_nav_prefs", Context.MODE_PRIVATE)
    }

    val initialDestination = remember {
        val savedDest = navPrefs.getString("last_destination", AppDestination.CALENDAR.name)
        AppDestination.entries.find { it.name == savedDest } ?: AppDestination.CALENDAR
    }
    val initialViewMode = remember {
        val savedMode = navPrefs.getString("last_view_mode", CalendarViewMode.DAY.name)
        CalendarViewMode.entries.find { it.name == savedMode } ?: CalendarViewMode.DAY
    }

    var currentDestination by rememberSaveable { mutableStateOf(initialDestination) }
    var currentViewMode by rememberSaveable { mutableStateOf(initialViewMode) }

    // Google Account & Sync Profile State
    var accountsList by remember { mutableStateOf(AccountPrefsManager.loadAccounts(context)) }
    var activeAccount by remember {
        mutableStateOf(AccountPrefsManager.getActiveAccount(context, accountsList))
    }
    var lastSyncLabel by remember { mutableStateOf(AccountPrefsManager.getLastSyncTime(context)) }
    var isAccountSwitcherOpen by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val drawerScrollState = rememberScrollState()

    val sidepanelItems = listOf(
        Triple(CalendarViewMode.AGENDA, "Agenda", Icons.Default.ViewAgenda),
        Triple(CalendarViewMode.DAY, "Day", Icons.Default.ViewDay),
        Triple(CalendarViewMode.WEEK, "Week", Icons.Default.ViewWeek),
        Triple(CalendarViewMode.MONTH, "Month", Icons.Default.CalendarViewMonth)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || currentDestination == AppDestination.SCANNER,
        modifier = modifier.fillMaxSize(),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Scrollable Top & Middle Sections
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(drawerScrollState)
                    ) {
                        Text(
                            text = "UniSync Schedule",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        // 1. Navigation Destinations (Calendar & Scanner)
                        Text(
                            text = "MAIN NAVIGATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                        )

                        AppDestination.entries.forEach { dest ->
                            val isSelected = currentDestination == dest
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = dest.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.title,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                selected = isSelected,
                                onClick = {
                                    currentDestination = dest
                                    navPrefs.edit { putString("last_destination", dest.name) }
                                    coroutineScope.launch { drawerState.close() }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // 2. Calendar View Modes (Agenda, Day, Week, Month)
                        Text(
                            text = "CALENDAR VIEWS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                        )

                        sidepanelItems.forEach { (mode, label, icon) ->
                            val isSelected = currentDestination == AppDestination.CALENDAR && currentViewMode == mode
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                selected = isSelected,
                                onClick = {
                                    currentDestination = AppDestination.CALENDAR
                                    currentViewMode = mode
                                    navPrefs.edit {
                                        putString("last_destination", AppDestination.CALENDAR.name)
                                        putString("last_view_mode", mode.name)
                                    }
                                    coroutineScope.launch { drawerState.close() }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // 3. Theme Color Swatch Selector
                        Text(
                            text = "THEME COLORS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppThemeColor.entries.forEach { themeOption ->
                                val isSelected = selectedThemeColor == themeOption
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onThemeColorSelected(themeOption) }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(themeOption.swatchColor)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        width = 2.dp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        shape = CircleShape
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "${themeOption.label} selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = themeOption.label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 4. Bottom Pinned Google Account Profile Footer
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    DrawerProfileFooter(
                        activeAccount = activeAccount,
                        lastSyncLabel = lastSyncLabel,
                        onClick = {
                            accountsList = AccountPrefsManager.loadAccounts(context)
                            isAccountSwitcherOpen = true
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentDestination) {
                    AppDestination.CALENDAR -> CalendarScreen(
                        viewModel = viewModel,
                        currentViewMode = currentViewMode,
                        onViewModeChange = { newMode ->
                            currentViewMode = newMode
                            navPrefs.edit {
                                putString("last_destination", AppDestination.CALENDAR.name)
                                putString("last_view_mode", newMode.name)
                            }
                        },
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = onToggleDarkMode,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                    )
                    AppDestination.SCANNER -> CameraScanScreen(viewModel = viewModel)
                }
            }
        }
    }

    if (isAccountSwitcherOpen) {
        AccountSwitcherDialog(
            accounts = accountsList,
            activeAccount = activeAccount,
            onDismiss = { isAccountSwitcherOpen = false },
            onSelectAccount = { chosen ->
                AccountPrefsManager.setActiveAccount(context, chosen.email)
                activeAccount = chosen
                lastSyncLabel = AccountPrefsManager.getLastSyncTime(context)
            },
            onAddAccount = { name, email ->
                val updatedList = AccountPrefsManager.addLocalAccount(context, name, email)
                accountsList = updatedList
                activeAccount = AccountPrefsManager.getActiveAccount(context, updatedList)
                lastSyncLabel = AccountPrefsManager.getLastSyncTime(context)
            },
            onSyncNow = {
                AccountPrefsManager.setActiveAccount(context, activeAccount.email)
                lastSyncLabel = AccountPrefsManager.getLastSyncTime(context)
            }
        )
    }
}