package com.example.capstone.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object NotificationHelper {
    
    private const val TAG = "NotificationHelper"
    const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    private const val PREFS_NAME = "EcoSphere_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"
    
    /**
     * Save FCM token to SharedPreferences
     */
    fun saveFcmToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
        Log.d(TAG, "✅ FCM token saved to SharedPreferences")
    }
    
    /**
     * Get FCM token from SharedPreferences
     */
    fun getFcmToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FCM_TOKEN, null)
    }
    
    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required for Android 12 and below
        }
    }
    
    /**
     * Request notification permission (Android 13+)
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission(activity)) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    /**
     * Get FCM token and save to Firestore
     */
    fun initializeFCM(userId: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "❌ Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            
            // Get new FCM registration token
            val token = task.result
            Log.d(TAG, "✅ FCM Token: $token")
            
            // Save token to Firestore
            saveFCMToken(userId, token)
        }
    }
    
    /**
     * Save FCM token to Firestore
     */
    private fun saveFCMToken(userId: String, token: String) {
        val db = FirebaseFirestore.getInstance()
        
        db.collection("Users")
            .document(userId)
            .update(
                mapOf(
                    "fcmToken" to token,
                    "fcmTokenUpdatedAt" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Log.d(TAG, "✅ FCM token saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save FCM token: ${e.message}")
                
                // If update fails, try to set the field
                db.collection("Users")
                    .document(userId)
                    .set(
                        mapOf(
                            "fcmToken" to token,
                            "fcmTokenUpdatedAt" to com.google.firebase.Timestamp.now()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
            }
    }
    
    /**
     * Subscribe to topic for push notifications
     */
    fun subscribeToTopic(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Subscribed to topic: $topic")
                } else {
                    Log.e(TAG, "❌ Failed to subscribe to topic: $topic")
                }
            }
    }
    
    /**
     * Unsubscribe from topic
     */
    fun unsubscribeFromTopic(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Unsubscribed from topic: $topic")
                } else {
                    Log.e(TAG, "❌ Failed to unsubscribe from topic: $topic")
                }
            }
    }
    
    /**
     * Check if push notifications are enabled in settings
     */
    fun arePushNotificationsEnabled(callback: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        
        db.collection("PlatformSettings")
            .document("config")
            .get()
            .addOnSuccessListener { document ->
                val notifications = document.data?.get("notifications") as? Map<String, Any>
                val enabled = notifications?.get("enablePushNotifications") as? Boolean ?: true
                callback(enabled)
            }
            .addOnFailureListener {
                callback(true) // Default to enabled if can't fetch
            }
    }
}
