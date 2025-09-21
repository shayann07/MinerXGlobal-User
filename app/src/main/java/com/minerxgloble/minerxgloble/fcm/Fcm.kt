package com.minerxgloble.minerxgloble.fcm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class Fcm {
    fun sendFCMNotification(
        targetDeviceToken: String,
        title: String,
        body: String,
        accessToken: String
    ) {
        val url = "https://fcm.googleapis.com/v1/projects/minerxgloble/messages:send"
        val client = OkHttpClient()

        val messageJson = JSONObject().apply {
            put("token", targetDeviceToken)

            put("android", JSONObject().apply {
                put("priority", "HIGH")
            })

            put("data", JSONObject().apply {
                put("title", title)
                put("body", body)
            })
        }

        val json = JSONObject().put("message", messageJson)
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    println("❌ FCM Error: ${response.code} – $responseBody")
                } else {
                    println("✅ Notification sent successfully")
                }
            } catch (e: IOException) {
                println("❌ Error sending notification: ${e.message}")
            }
        }
    }
}
