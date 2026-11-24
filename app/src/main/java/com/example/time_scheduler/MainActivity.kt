package com.example.time_scheduler

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.example.time_scheduler.ui.navigation.MainAppNavigation
import com.example.time_scheduler.ui.theme.AppThemeColor
import com.example.time_scheduler.ui.theme.Time_SchedulerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("unisync_theme_prefs", Context.MODE_PRIVATE)
        val initialDarkMode = prefs.getBoolean("is_dark_mode", false)
        val savedColorName = prefs.getString("theme_color", AppThemeColor.VIOLET.name)
        val initialThemeColor = AppThemeColor.entries.find { it.name == savedColorName } ?: AppThemeColor.VIOLET

        setContent {
            var isDarkMode by rememberSaveable { mutableStateOf(initialDarkMode) }
            var themeColor by rememberSaveable { mutableStateOf(initialThemeColor) }

            Time_SchedulerTheme(
                darkTheme = isDarkMode,
                themeColor = themeColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppNavigation(
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = {
                            val updated = !isDarkMode
                            isDarkMode = updated
                            prefs.edit { putBoolean("is_dark_mode", updated) }
                        },
                        selectedThemeColor = themeColor,
                        onThemeColorSelected = { selected ->
                            themeColor = selected
                            prefs.edit { putString("theme_color", selected.name) }
                        }
                    )
                }
            }
        }
    }
}