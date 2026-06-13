package com.example.capstone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.capstone.databinding.ActivityStudentMainBinding
import com.example.capstone.utils.NotificationHelper
import com.example.capstone.workers.DailyReminderWorker
import com.example.capstone.services.PushNotificationService
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Calendar
import java.util.concurrent.TimeUnit

class StudentMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentMainBinding
    private lateinit var navigationController: StudentNavigationController
    // Store listener so it can be removed on logout/destroy
    private var platformSettingsListener: com.google.firebase.firestore.ListenerRegistration? = null
    
    companion object {
        private const val TAG = "StudentMainActivity"
    }
    
    // Permission launcher for notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
            initializeFCM()
        } else {
            Log.d(TAG, "Notification permission denied")
            Toast.makeText(this, "Notifications disabled. Enable in settings to receive updates.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navigationController = StudentNavigationController(this)
        setupViewPager()
        setupBottomNavigation()
        
        // Initialize notifications
        requestNotificationPermission()
        scheduleDailyReminders()
        
        // Start listening for real-time push notifications
        PushNotificationService.startListening(this)
    }
    
    fun navigateToTab(tabIndex: Int) {
        binding.viewPager.currentItem = tabIndex
    }
    
    fun getNavigationController(): NavigationController = navigationController

    private fun setupViewPager() {
        val fragments = listOf(
            HomeFragment(),
            LearnFragment(),
            CompeteFragment(),
            ProfileFragment()
        )

        val adapter = ViewPagerAdapter(this, fragments)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = true // Enable swipe

        // Sync ViewPager with BottomNavigation and set navigation controller
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.bottomNavigation.menu.getItem(position).isChecked = true
                
                // Set navigation controller for the current fragment
                setNavigationControllerForCurrentFragment(position)
            }
        })
        
        // Set navigation controller for the initial fragment (position 0)
        binding.viewPager.post {
            setNavigationControllerForCurrentFragment(0)
        }
    }
    
    private fun setNavigationControllerForCurrentFragment(position: Int) {
        // Use a more reliable way to get the current fragment
        val fragmentTag = "f$position"
        val currentFragment = supportFragmentManager.findFragmentByTag(fragmentTag)
        if (currentFragment is NavigationAware) {
            currentFragment.setNavigationController(navigationController)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.viewPager.currentItem = 0
                    true
                }
                R.id.nav_learn -> {
                    binding.viewPager.currentItem = 1
                    true
                }
                R.id.nav_compete -> {
                    binding.viewPager.currentItem = 2
                    true
                }
                R.id.nav_profile -> {
                    binding.viewPager.currentItem = 3
                    true
                }
                else -> false
            }
        }
    }
    
    override fun onBackPressed() {
        // Handle back button properly - show exit confirmation
        showExitConfirmation()
    }
    
    private fun showExitConfirmation() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Exit EcoSphere")
            .setMessage("Are you sure you want to exit the app?")
            .setPositiveButton("Exit") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove all Firestore listeners to prevent PERMISSION_DENIED errors after logout
        stopListeners()
        Log.d(TAG, "All Firestore listeners removed")
    }

    /** Called before logout to cleanly remove all active listeners */
    fun stopListeners() {
        platformSettingsListener?.remove()
        platformSettingsListener = null
        PushNotificationService.stopListening()
    }
    
    // ─── Notification Setup ───────────────────────────────────────────────────
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                    initializeFCM()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show explanation and request permission
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Enable Notifications")
                        .setMessage("EcoSphere would like to send you daily learning reminders and important updates. Enable notifications?")
                        .setPositiveButton("Enable") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Not Now", null)
                        .show()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For Android 12 and below, permission is granted by default
            initializeFCM()
        }
    }
    
    private fun initializeFCM() {
        Log.d(TAG, "Initializing FCM...")
        
        // Get FCM token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d(TAG, "FCM Token: $token")
            
            // Save token to NotificationHelper (SharedPreferences)
            NotificationHelper.saveFcmToken(this, token)
            
            // Save token to Firestore for current user
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                saveFCMTokenToFirestore(userId, token)
            }
            
            // Subscribe to topics
            subscribeToTopics()
        }
    }
    
    private fun saveFCMTokenToFirestore(userId: String, token: String) {
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
                Log.d(TAG, "✅ FCM token saved to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save FCM token to Firestore: ${e.message}")
                
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
    
    private fun subscribeToTopics() {
        // Subscribe to "all_students" topic for broadcast notifications
        FirebaseMessaging.getInstance().subscribeToTopic("all_students")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed to all_students topic")
                } else {
                    Log.w(TAG, "Failed to subscribe to all_students topic", task.exception)
                }
            }
        
        // Subscribe to "daily_reminders" topic
        FirebaseMessaging.getInstance().subscribeToTopic("daily_reminders")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed to daily_reminders topic")
                } else {
                    Log.w(TAG, "Failed to subscribe to daily_reminders topic", task.exception)
                }
            }
    }
    
    private fun scheduleDailyReminders() {
        // Listen for real-time changes to platform settings
        val firestore = FirebaseFirestore.getInstance()
        platformSettingsListener = firestore.collection("PlatformSettings")
            .document("config")
            .addSnapshotListener { document, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to platform settings", error)
                    return@addSnapshotListener
                }
                
                if (document != null && document.exists()) {
                    val notifications = document.data?.get("notifications") as? Map<String, Any>
                    val enableDailyReminders = notifications?.get("enableDailyReminders") as? Boolean ?: true
                    val reminderTime = notifications?.get("dailyReminderTime") as? String ?: "18:00"
                    
                    if (enableDailyReminders) {
                        Log.d(TAG, "📅 Scheduling daily reminders at $reminderTime")
                        scheduleWorkManager(reminderTime)
                    } else {
                        Log.d(TAG, "🚫 Daily reminders disabled, cancelling scheduled work")
                        WorkManager.getInstance(this).cancelUniqueWork("daily_reminder")
                    }
                } else {
                    Log.d(TAG, "Platform settings not found, using defaults")
                    scheduleWorkManager("18:00")
                }
            }
    }
    
    private fun scheduleWorkManager(reminderTime: String) {
        try {
            Log.d(TAG, "⏰ Scheduling WorkManager for time: $reminderTime")
            
            // Check for exact alarm permission on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "⚠️ Exact alarm permission not granted. Notifications may be delayed.")
                    Toast.makeText(
                        this,
                        "⚠️ For precise daily reminders, please enable 'Alarms & reminders' permission in app settings",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            
            // Parse reminder time (format: "HH:mm")
            val timeParts = reminderTime.split(":")
            if (timeParts.size != 2) {
                Log.e(TAG, "❌ Invalid time format: $reminderTime")
                return
            }
            
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            
            Log.d(TAG, "Parsed time: $hour:$minute")
            
            // Calculate initial delay until the reminder time
            val currentTime = Calendar.getInstance()
            val scheduledTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            Log.d(TAG, "Current time: ${currentTime.time}")
            Log.d(TAG, "Scheduled time: ${scheduledTime.time}")
            
            // If scheduled time has passed today, schedule for tomorrow
            if (scheduledTime.before(currentTime)) {
                scheduledTime.add(Calendar.DAY_OF_MONTH, 1)
                Log.d(TAG, "Time has passed today, scheduling for tomorrow: ${scheduledTime.time}")
            }
            
            val initialDelay = scheduledTime.timeInMillis - currentTime.timeInMillis
            val delayMinutes = initialDelay / 1000 / 60
            val delayHours = delayMinutes / 60
            
            Log.d(TAG, "Initial delay: $delayMinutes minutes ($delayHours hours)")
            
            // Cancel any existing work first
            WorkManager.getInstance(this).cancelUniqueWork("daily_reminder")
            Log.d(TAG, "Cancelled existing daily reminder work")
            
            // Create periodic work request (runs daily)
            val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyReminderWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("daily_reminder")
                .build()
            
            // Enqueue the work
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "daily_reminder",
                ExistingPeriodicWorkPolicy.REPLACE,
                dailyWorkRequest
            )
            
            Log.d(TAG, "✅ Daily reminder scheduled successfully!")
            Log.d(TAG, "   Time: $reminderTime")
            Log.d(TAG, "   Next run: ${scheduledTime.time}")
            Log.d(TAG, "   Delay: $delayMinutes minutes ($delayHours hours)")
            Log.d(TAG, "   Work ID: ${dailyWorkRequest.id}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule daily reminder", e)
            Toast.makeText(this, "Failed to schedule daily reminder: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
