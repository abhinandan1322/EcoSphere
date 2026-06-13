package com.example.capstone.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.capstone.InAppNotificationsActivity
import com.example.capstone.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Service to handle push notifications from admin
 * Shows a status bar notification that opens the in-app notifications screen
 */
object PushNotificationService {
    
    private const val TAG = "PushNotificationService"
    private const val CHANNEL_ID = "admin_push_notifications"
    private const val CHANNEL_NAME = "Admin Notifications"
    
    // Store listener registration so it can be removed on logout
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    
    /**
     * Start listening for push notifications
     */
    fun startListening(context: Context) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // Remove any existing listener first to avoid duplicates
        stopListening()
        
        Log.d(TAG, "Starting push notification listener for user: $userId")
        
        listenerRegistration = FirebaseFirestore.getInstance()
            .collection("PushNotifications")
            .whereEqualTo("type", "admin_push")
            .whereEqualTo("status", "sent")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for push notifications", error)
                    return@addSnapshotListener
                }
                
                if (snapshots != null && !snapshots.isEmpty) {
                    for (docChange in snapshots.documentChanges) {
                        if (docChange.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val notification = docChange.document
                            val notificationId = notification.id
                            val title = notification.getString("title") ?: "New Notification"
                            val message = notification.getString("message") ?: ""
                            
                            // Check if already shown
                            val prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)
                            val isShown = prefs.getBoolean("shown_${userId}_$notificationId", false)
                            
                            if (!isShown) {
                                Log.d(TAG, "📢 New push notification: $title")
                                showStatusBarNotification(context, title, message, notificationId)
                                
                                // Mark as shown
                                prefs.edit().putBoolean("shown_${userId}_$notificationId", true).apply()
                            }
                        }
                    }
                }
            }
    }
    
    /**
     * Stop listening — call this on logout to prevent PERMISSION_DENIED errors
     */
    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
        Log.d(TAG, "Push notification listener removed")
    }
    
    /**
     * Show notification in status bar
     */
    private fun showStatusBarNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: String
    ) {
        createNotificationChannel(context)
        
        // Create intent with proper back stack
        // This ensures pressing back goes to home screen, not exits app
        val notificationIntent = Intent(context, InAppNotificationsActivity::class.java)
        val homeIntent = Intent(context, com.example.capstone.StudentMainActivity::class.java)
        
        // Create task stack: Home -> Notifications
        val pendingIntent = android.app.TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(homeIntent)  // Add home as parent
            addNextIntent(notificationIntent)          // Add notifications on top
            getPendingIntent(
                notificationId.hashCode(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        
        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId.hashCode(), notification)
        
        Log.d(TAG, "✅ Status bar notification shown: $title")
    }
    
    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications from admin"
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
