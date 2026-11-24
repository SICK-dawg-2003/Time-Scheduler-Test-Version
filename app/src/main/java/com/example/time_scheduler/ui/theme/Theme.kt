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

enum class AppThemeColor(
    val label: String,
    val swatchColor: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color
) {
    VIOLET(
        label = "Violet",
        swatchColor = Purple40,
        lightPrimary = Purple40,
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFEADDFF),
        lightOnPrimaryContainer = Color(0xFF21005D),
        darkPrimary = Color(0xFFD0BCFF),
        darkOnPrimary = Color(0xFF381E72),
        darkPrimaryContainer = Color(0xFF4F378B),
        darkOnPrimaryContainer = Color(0xFFEADDFF)
    ),
    OCEAN(
        label = "Ocean",
        swatchColor = Color(0xFF006494),
        lightPrimary = Color(0xFF006494),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFCAE6FF),
        lightOnPrimaryContainer = Color(0xFF001E30),
        darkPrimary = Color(0xFF8DCDFF),
        darkOnPrimary = Color(0xFF00344F),
        darkPrimaryContainer = Color(0xFF004B71),
        darkOnPrimaryContainer = Color(0xFFCAE6FF)
    ),
    EMERALD(
        label = "Emerald",
        swatchColor = Color(0xFF1B6D43),
        lightPrimary = Color(0xFF1B6D43),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFA5F5C0),
        lightOnPrimaryContainer = Color(0xFF00210F),
        darkPrimary = Color(0xFF8AD8A5),
        darkOnPrimary = Color(0xFF00391E),
        darkPrimaryContainer = Color(0xFF00522D),
        darkOnPrimaryContainer = Color(0xFFA5F5C0)
    ),
    SUNSET(
        label = "Sunset",
        swatchColor = Color(0xFF9E4300),
        lightPrimary = Color(0xFF9E4300),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFDBCA),
        lightOnPrimaryContainer = Color(0xFF341100),
        darkPrimary = Color(0xFFFFB68E),
        darkOnPrimary = Color(0xFF552100),
        darkPrimaryContainer = Color(0xFF783100),
        darkOnPrimaryContainer = Color(0xFFFFDBCA)
    ),
    ROSE(
        label = "Rose",
        swatchColor = Color(0xFF9C3E65),
        lightPrimary = Color(0xFF9C3E65),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFD9E4),
        lightOnPrimaryContainer = Color(0xFF3E001E),
        darkPrimary = Color(0xFFFFB0CD),
        darkOnPrimary = Color(0xFF600F36),
        darkPrimaryContainer = Color(0xFF7E264D),
        darkOnPrimaryContainer = Color(0xFFFFD9E4)
    )
}

private val BaseDarkColorScheme = darkColorScheme(
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

private val BaseLightColorScheme = lightColorScheme(
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
    themeColor: AppThemeColor = AppThemeColor.VIOLET,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BaseDarkColorScheme.copy(
            primary = themeColor.darkPrimary,
            onPrimary = themeColor.darkOnPrimary,
            primaryContainer = themeColor.darkPrimaryContainer,
            onPrimaryContainer = themeColor.darkOnPrimaryContainer
        )
        else -> BaseLightColorScheme.copy(
            primary = themeColor.lightPrimary,
            onPrimary = themeColor.lightOnPrimary,
            primaryContainer = themeColor.lightPrimaryContainer,
            onPrimaryContainer = themeColor.lightOnPrimaryContainer
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}