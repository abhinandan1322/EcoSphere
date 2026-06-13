package com.example.capstone.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.capstone.R
import com.example.capstone.StudentMainActivity
import com.google.firebase.firestore.FirebaseFirestore

class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "DailyReminderWorker"
        private const val CHANNEL_ID = "EcoSphere_daily_reminders"
        private const val NOTIFICATION_ID = 1001
    }

    override fun doWork(): Result {
        Log.d(TAG, "🔔 Daily reminder worker started at ${System.currentTimeMillis()}")
        Log.d(TAG, "   Run attempt: $runAttemptCount")

        try {
            // Check if daily reminders are enabled in platform settings
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("PlatformSettings")
                .document("config")
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        Log.d(TAG, "Platform settings loaded successfully")
                        val notifications = document.data?.get("notifications") as? Map<String, Any>
                        val enableDailyReminders = notifications?.get("enableDailyReminders") as? Boolean ?: true
                        val reminderMessage = notifications?.get("reminderMessage") as? String 
                            ?: "Time to learn something new! 🌱"

                        Log.d(TAG, "Daily reminders enabled: $enableDailyReminders")
                        Log.d(TAG, "Reminder message: $reminderMessage")

                        if (enableDailyReminders) {
                            Log.d(TAG, "Sending notification...")
                            sendNotification(reminderMessage)
                        } else {
                            Log.d(TAG, "Daily reminders disabled, skipping notification")
                        }
                    } else {
                        Log.w(TAG, "Platform settings not found, using default message")
                        sendNotification("Time to learn something new! 🌱")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to load platform settings", e)
                    // Send notification with default message anyway
                    sendNotification("Time to learn something new! 🌱")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in doWork", e)
        }

        Log.d(TAG, "✅ Worker completed successfully")
        return Result.success()
    }

    private fun sendNotification(message: String) {
        Log.d(TAG, "📱 Preparing to send notification...")
        Log.d(TAG, "   Message: $message")
        
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create notification channel for Android O and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Daily Learning Reminders",
                    NotificationManager.IMPORTANCE_HIGH // Changed to HIGH for better visibility
                ).apply {
                    description = "Daily reminders to continue your learning journey"
                    enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "✅ Notification channel created: $CHANNEL_ID")
            }

            // Create intent to open the app
            val intent = Intent(applicationContext, StudentMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("type", "daily_reminder") // Mark as daily reminder
            }

            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Build notification
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("EcoSphere Daily Reminder")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Changed to HIGH
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Add sound, vibration, lights
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "✅ System notification sent successfully!")
            Log.d(TAG, "   Notification ID: $NOTIFICATION_ID")
            Log.d(TAG, "   Channel ID: $CHANNEL_ID")
            Log.d(TAG, "   Time: ${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send notification", e)
        }
    }
}
