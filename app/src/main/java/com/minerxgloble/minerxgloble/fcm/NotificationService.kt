package com.minerxgloble.minerxgloble.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.ui.MainActivity
import com.minerxgloble.minerxgloble.utils.PrefService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.minerxgloble.minerxgloble.models.NotificationModel
import com.minerxgloble.minerxgloble.utils.NotificationPreferenceManager
import kotlin.random.Random

class NotificationService : FirebaseMessagingService() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val channelId = "default_channel"

    private lateinit var prefManager: NotificationPreferenceManager
    private lateinit var userPref: PrefService

    override fun onCreate() {
        super.onCreate()
        prefManager = NotificationPreferenceManager(applicationContext)
        userPref = PrefService(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("NotificationService", "New FCM token: $token")

        val uid = userPref.getUserId()
        if (uid.isNullOrEmpty()) {
            Log.w("NotificationService", "No userId in PrefService; skip saving token")
            return
        }

        firestore.collection("users").document(uid)
            .update("deviceToken", token)
            .addOnSuccessListener { Log.d("NotificationService", "Token saved for $uid") }
            .addOnFailureListener { e -> Log.e("NotificationService", "Save failed", e) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("NotificationService", "onMessageReceived: ${message.data}")

        val title = message.data["title"] ?: return
        val body = message.data["body"] ?: return

        createNotificationChannelIfNeeded()

        val uid = userPref.getUserId()
        if (!uid.isNullOrEmpty()) {
            prefManager.saveNotification(uid, NotificationModel(title, body, System.currentTimeMillis()))
        } else {
            Log.w("NotificationService", "Missing userId; not persisting notification")
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(Random.nextInt(), builder.build())
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "General Notifications", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Default notification channel"
                enableVibration(true)
                enableLights(true)
                setSound(null, null)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}

