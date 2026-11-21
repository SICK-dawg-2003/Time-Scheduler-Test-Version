package com.example.time_scheduler.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainerHigh = Color(0xFFECE6F0)
)

@Composable
fun Time_SchedulerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
