package com.example.time_scheduler.data

import androidx.compose.ui.graphics.Color

enum class Category(
    val displayName: String,
    val color: Color,
    val hexColor: String
) {
    WORK("Work", Color(0xFF2196F3), "#2196F3"),         // Blue
    PERSONAL("Personal", Color(0xFF4CAF50), "#4CAF50"), // Green
    HEALTH("Health", Color(0xFFE91E63), "#E91E63"),     // Pink/Red
    STUDY("Study", Color(0xFF9C27B0), "#9C27B0"),       // Purple
    URGENT("Urgent", Color(0xFFFF5722), "#FF5722"),     // Deep Orange
    OTHER("Other", Color(0xFF009688), "#009688");       // Teal

    companion object {
        fun fromName(name: String): Category {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true) }
                ?: OTHER
        }
    }
}
