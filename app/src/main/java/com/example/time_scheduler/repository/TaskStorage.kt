package com.example.time_scheduler.repository

import android.content.Context
import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class TaskStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveTasks(tasks: List<TaskItem>) {
        val jsonArray = JSONArray()
        tasks.forEach { task ->
            jsonArray.put(task.toJson())
        }
        prefs.edit().putString(KEY_TASKS, jsonArray.toString()).apply()
    }

    fun loadTasks(): List<TaskItem> {
        val jsonString = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        val tasks = mutableListOf<TaskItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                try {
                    tasks.add(taskItemFromJson(jsonObject))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tasks
    }

    private fun TaskItem.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("description", description)
            put("date", date.toString())
            put("startTime", startTime.toString())
            put("endTime", endTime.toString())
            put("category", category.name)
            put("isCompleted", isCompleted)
            put("isSyncedGoogle", isSyncedGoogle)
            put("isSyncedOutlook", isSyncedOutlook)
            put("bufferMinutes", bufferMinutes)
        }
    }

    private fun taskItemFromJson(json: JSONObject): TaskItem {
        return TaskItem(
            id = json.optString("id", UUID.randomUUID().toString()),
            title = json.optString("title", "Untitled Task"),
            description = json.optString("description", ""),
            date = LocalDate.parse(json.getString("date")),
            startTime = LocalTime.parse(json.getString("startTime")),
            endTime = LocalTime.parse(json.getString("endTime")),
            category = Category.fromName(json.optString("category", Category.WORK.name)),
            isCompleted = json.optBoolean("isCompleted", false),
            isSyncedGoogle = json.optBoolean("isSyncedGoogle", false),
            isSyncedOutlook = json.optBoolean("isSyncedOutlook", false),
            bufferMinutes = json.optInt("bufferMinutes", 15)
        )
    }

    companion object {
        private const val PREFS_NAME = "time_scheduler_saved_state"
        private const val KEY_TASKS = "saved_scheduled_tasks"
    }
}
