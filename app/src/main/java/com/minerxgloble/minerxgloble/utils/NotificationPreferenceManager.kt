package com.minerxgloble.minerxgloble.utils

import android.content.Context
import android.content.SharedPreferences

import org.json.JSONObject
import kotlin.collections.iterator
import androidx.core.content.edit
import com.minerxgloble.minerxgloble.models.NotificationModel
import kotlin.apply
import kotlin.collections.sortByDescending
import kotlin.text.startsWith

class NotificationPreferenceManager(private val context: Context) {

    // Function to get SharedPreferences for a specific user
    private fun getSharedPreferences(userId: String): SharedPreferences {
        return context.getSharedPreferences("notifications_$userId", Context.MODE_PRIVATE)
    }

    // Function to save a notification to SharedPreferences for a specific user
    fun saveNotification(userId: String,notification: NotificationModel) {
        val notificationKey = "notification_${System.currentTimeMillis()}"
        val notificationContent = JSONObject().apply {
            put("title", notification.title)
            put("body", notification.body)
            put("timestamp", notification.timestamp)
        }.toString()  // Convert the data to a JSON stringvert the data to a JSON string

        // Get SharedPreferences for the user
        val userPrefs = getSharedPreferences(userId)
        val editor = userPrefs.edit()


        editor.putString(notificationKey, notificationContent)
        editor.apply()
    }

    // Function to retrieve notifications for a user in descending order (most recent first)
    fun getNotifications(userId: String): List<NotificationModel> {
        val userPrefs = getSharedPreferences(userId)
        val notifications = mutableListOf<NotificationModel>()

        // Loop through all keys in SharedPreferences to find the notifications
        val allEntries = userPrefs.all
        for (entry in allEntries) {
            if (entry.key.startsWith("notification_")) {
                val notificationData = entry.value as String
                val json = JSONObject(notificationData)
                val title = json.getString("title")
                val body = json.getString("body")
                val timestamp = json.getLong("timestamp")

                notifications.add(NotificationModel(title, body, timestamp))
            }
        }

        // Sort the notifications by timestamp in descending order (most recent first)
        notifications.sortByDescending { it.timestamp }

        // Return the notifications in descending order of time
        return notifications
    }

    // Function to clear all notifications for a specific user (if needed)
    fun clearNotifications(userId: String) {
        val userPrefs = getSharedPreferences(userId)
        val editor = userPrefs.edit()

        // Loop through all entries in SharedPreferences and remove only the notification keys
        val allEntries = userPrefs.all
        for (entry in allEntries) {
            if (entry.key.startsWith("notification_")) {
                editor.remove(entry.key) // Remove each notification entry
            }
        }

        // Apply the changes
        editor.apply()
    }



}
