package com.example.capstone.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.capstone.R
import com.example.capstone.StudentMainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        const val CHANNEL_ID = "EcoSphere_channel"
        const val CHANNEL_NAME = "EcoSphere Notifications"
        const val CHANNEL_DESCRIPTION = "Notifications for EcoSphere app"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "✅ New FCM token: $token")
        
        // Save token to SharedPreferences
        com.example.capstone.utils.NotificationHelper.saveFcmToken(this, token)
        
        // Save token to Firestore for current user
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            saveFCMToken(userId, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d(TAG, "📩 Message received from: ${message.from}")
        
        // Check message type - only show system notification for daily reminders
        val messageType = message.data["type"] ?: message.notification?.tag ?: "general"
        
        if (messageType == "daily_reminder") {
            // Show system notification for daily reminders
            val title = message.data["title"] ?: message.notification?.title ?: "EcoSphere Daily Reminder"
            val body = message.data["body"] ?: message.notification?.body ?: "Time to learn something new!"
            
            Log.d(TAG, "📱 Showing system notification for daily reminder")
            showNotification(title, body, message.data)
        } else {
            // For admin push notifications, just log - they will be shown in-app
            Log.d(TAG, "📢 Admin push notification received - will show in-app only")
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        createNotificationChannel()
        
        val intent = Intent(this, StudentMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Add data to intent if needed
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        
        Log.d(TAG, "✅ Notification shown: $title")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun saveFCMToken(userId: String, token: String) {
        val db = FirebaseFirestore.getInstance()
        
        db.collection("Users")
            .document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "✅ FCM token saved for user: $userId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save FCM token: ${e.message}")
            }
    }
}
