package com.example.capstone

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone.databinding.ActivityAdminPlatformSettingsBinding
import com.google.firebase.firestore.FirebaseFirestore

class AdminPlatformSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPlatformSettingsBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val settingsRef = firestore.collection("PlatformSettings").document("config")
    private var isLeadTeacher: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPlatformSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadTeacherRole()
        setupClickListeners()
        loadCurrentSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Platform Settings"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    /**
     * Load teacher role to determine if user is Lead Teacher
     */
    private fun loadTeacherRole() {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            updateUIForTeacherRole()
            return
        }

        firestore.collection("Users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    isLeadTeacher = document.getBoolean("isLeadTeacher") ?: false
                    android.util.Log.d("PlatformSettings", "Is Lead Teacher: $isLeadTeacher")
                }
                updateUIForTeacherRole()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("PlatformSettings", "Failed to load teacher role", e)
                updateUIForTeacherRole()
            }
    }

    /**
     * Update UI based on teacher role (Lead Teacher vs Normal Teacher)
     */
    private fun updateUIForTeacherRole() {
        // Hide restricted cards for Normal Teachers
        if (isLeadTeacher) {
            binding.cardUserManagement.visibility = View.VISIBLE
            binding.cardAdminFeatures.visibility = View.VISIBLE
            binding.cardMaintenanceMode.visibility = View.VISIBLE
        } else {
            binding.cardUserManagement.visibility = View.GONE
            binding.cardAdminFeatures.visibility = View.GONE
            binding.cardMaintenanceMode.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        // General Configuration
        binding.cardLearningSystem.setOnClickListener { showLearningSystemDialog() }
        binding.cardUserManagement.setOnClickListener { showUserManagementDialog() }

        // Feature Toggles
        binding.cardStudentFeatures.setOnClickListener { showStudentFeaturesDialog() }
        binding.cardAdminFeatures.setOnClickListener { showAdminFeaturesDialog() }

        // Notifications
        binding.cardNotificationSettings.setOnClickListener { showNotificationSettingsDialog() }

        // System Maintenance
        binding.cardMaintenanceMode.setOnClickListener { showMaintenanceModeDialog() }

        // Action Buttons
        binding.btnSaveSettings.setOnClickListener { saveAllSettings() }
        binding.btnResetDefaults.setOnClickListener { showResetConfirmation() }
        binding.btnRefresh.setOnClickListener { loadCurrentSettings() }
    }

    private fun loadCurrentSettings() {
        binding.progressBar.visibility = View.VISIBLE
        
        settingsRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    updateUIWithSettings(document.data ?: emptyMap())
                } else {
                    // Create default settings if document doesn't exist
                    android.util.Log.d("AdminSettings", "Settings document doesn't exist, creating defaults")
                    createDefaultSettings()
                }
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                android.util.Log.e("AdminSettings", "Failed to load settings", e)
                Toast.makeText(this, "Failed to load settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUIWithSettings(settings: Map<String, Any>) {
        // Update UI elements with current settings
        val features = settings["features"] as? Map<String, Any> ?: emptyMap()
        val notifications = settings["notifications"] as? Map<String, Any> ?: emptyMap()
        val maintenance = settings["maintenance"] as? Map<String, Any> ?: emptyMap()

        // Update status indicators (App name and welcome message removed from UI)
        
        // Feature status
        val modulesEnabled = features["enableModules"] as? Boolean ?: true
        val challengesEnabled = features["enableChallenges"] as? Boolean ?: true
        val leaderboardEnabled = features["enableLeaderboard"] as? Boolean ?: true
        
        binding.tvFeatureStatus.text = "Modules: ${if (modulesEnabled) "✅" else "❌"} | " +
                "Challenges: ${if (challengesEnabled) "✅" else "❌"} | " +
                "Leaderboard: ${if (leaderboardEnabled) "✅" else "❌"}"

        // Maintenance status
        val maintenanceMode = maintenance["maintenanceMode"] as? Boolean ?: false
        binding.tvMaintenanceStatus.text = if (maintenanceMode) "🚧 MAINTENANCE MODE ACTIVE" else "✅ System Operational"
        binding.tvMaintenanceStatus.setTextColor(getColor(if (maintenanceMode) android.R.color.holo_red_dark else android.R.color.holo_green_dark))

        // Notification status
        val notificationsEnabled = notifications["enablePushNotifications"] as? Boolean ?: true
        binding.tvNotificationStatus.text = if (notificationsEnabled) "🔔 Notifications Active" else "🔕 Notifications Disabled"
    }

    private fun showLearningSystemDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_learning_system, null)
        val etQuizPassingScore = dialogView.findViewById<EditText>(R.id.etQuizPassingScore)
        val etMaxQuizAttempts = dialogView.findViewById<EditText>(R.id.etMaxQuizAttempts)

        // Load current values
        settingsRef.get().addOnSuccessListener { document ->
            val general = document.data?.get("general") as? Map<String, Any> ?: emptyMap()
            etQuizPassingScore.setText((general["quizPassingScore"] as? Long ?: 80L).toString())
            etMaxQuizAttempts.setText((general["maxQuizAttempts"] as? Long ?: 3L).toString())
        }

        AlertDialog.Builder(this)
            .setTitle("Learning System Configuration")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val passingScore = etQuizPassingScore.text.toString().toLong()
                    val maxAttempts = etMaxQuizAttempts.text.toString().toLong()
                    
                    // Validation
                    if (passingScore < 0 || passingScore > 100) {
                        Toast.makeText(this, "❌ Passing score must be between 0-100", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    if (maxAttempts < 1 || maxAttempts > 10) {
                        Toast.makeText(this, "❌ Max attempts must be between 1-10", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    val updates = mapOf(
                        "general.quizPassingScore" to passingScore,
                        "general.maxQuizAttempts" to maxAttempts
                    )
                    updateSettings(updates)
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "❌ Please enter valid numbers", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStudentFeaturesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_student_features, null)
        val switchModules = dialogView.findViewById<Switch>(R.id.switchModules)
        val switchChallenges = dialogView.findViewById<Switch>(R.id.switchChallenges)
        val switchLeaderboard = dialogView.findViewById<Switch>(R.id.switchLeaderboard)
        val switchEcoAssistant = dialogView.findViewById<Switch>(R.id.switchEcoAssistant)
        val switchProgressTracker = dialogView.findViewById<Switch>(R.id.switchProgressTracker)
        val switchEcosystemGame = dialogView.findViewById<Switch>(R.id.switchEcosystemGame)

        // Load current values
        settingsRef.get().addOnSuccessListener { document ->
            val features = document.data?.get("features") as? Map<String, Any> ?: emptyMap()
            switchModules.isChecked = features["enableModules"] as? Boolean ?: true
            switchChallenges.isChecked = features["enableChallenges"] as? Boolean ?: true
            switchLeaderboard.isChecked = features["enableLeaderboard"] as? Boolean ?: true
            switchEcoAssistant.isChecked = features["enableEcoAssistant"] as? Boolean ?: true
            switchProgressTracker.isChecked = features["enableProgressTracker"] as? Boolean ?: true
            switchEcosystemGame.isChecked = features["enableEcosystemGame"] as? Boolean ?: true
        }

        AlertDialog.Builder(this)
            .setTitle("Student App Features")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val updates = mapOf(
                    "features.enableModules" to switchModules.isChecked,
                    "features.enableChallenges" to switchChallenges.isChecked,
                    "features.enableLeaderboard" to switchLeaderboard.isChecked,
                    "features.enableEcoAssistant" to switchEcoAssistant.isChecked,
                    "features.enableProgressTracker" to switchProgressTracker.isChecked,
                    "features.enableEcosystemGame" to switchEcosystemGame.isChecked
                )
                updateSettings(updates)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNotificationSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_notification_settings, null)
        val switchPushNotifications = dialogView.findViewById<Switch>(R.id.switchPushNotifications)
        val switchDailyReminders = dialogView.findViewById<Switch>(R.id.switchDailyReminders)
        val etReminderTime = dialogView.findViewById<EditText>(R.id.etReminderTime)
        val etReminderMessage = dialogView.findViewById<EditText>(R.id.etReminderMessage)

        // Load current values
        settingsRef.get().addOnSuccessListener { document ->
            val notifications = document.data?.get("notifications") as? Map<String, Any> ?: emptyMap()
            switchPushNotifications.isChecked = notifications["enablePushNotifications"] as? Boolean ?: true
            switchDailyReminders.isChecked = notifications["enableDailyReminders"] as? Boolean ?: true
            etReminderTime.setText(notifications["dailyReminderTime"] as? String ?: "18:00")
            etReminderMessage.setText(notifications["reminderMessage"] as? String ?: "Time to learn something new! 🌱")
            
            android.util.Log.d("NotificationSettings", "Loaded time: ${notifications["dailyReminderTime"]}")
        }

        AlertDialog.Builder(this)
            .setTitle("Notification Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val reminderMessage = etReminderMessage.text.toString().trim()
                val reminderTime = etReminderTime.text.toString().trim()
                
                android.util.Log.d("NotificationSettings", "Saving time: $reminderTime")
                
                // Validation
                if (reminderMessage.isEmpty()) {
                    Toast.makeText(this, "❌ Reminder message cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (reminderMessage.length < 10) {
                    Toast.makeText(this, "❌ Reminder message must be at least 10 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Validate time format (HH:MM)
                if (!reminderTime.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$"))) {
                    Toast.makeText(this, "❌ Invalid time format. Use HH:MM (e.g., 18:00)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Create the complete notifications object
                val notificationsData = mapOf(
                    "enablePushNotifications" to switchPushNotifications.isChecked,
                    "enableDailyReminders" to switchDailyReminders.isChecked,
                    "dailyReminderTime" to reminderTime,
                    "reminderMessage" to reminderMessage
                )
                
                // Save to Firestore using set with merge
                settingsRef.set(
                    mapOf("notifications" to notificationsData),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                    .addOnSuccessListener {
                        android.util.Log.d("NotificationSettings", "✅ Settings saved successfully")
                        
                        // Verify the save
                        settingsRef.get().addOnSuccessListener { doc ->
                            val saved = doc.data?.get("notifications") as? Map<String, Any>
                            android.util.Log.d("NotificationSettings", "Verified saved time: ${saved?.get("dailyReminderTime")}")
                        }
                        
                        Toast.makeText(this, "✅ Settings saved successfully", Toast.LENGTH_SHORT).show()
                        loadCurrentSettings() // Refresh UI
                        
                        // Show appropriate message
                        if (switchDailyReminders.isChecked) {
                            Toast.makeText(this, "✅ Daily reminders scheduled for $reminderTime\n\n⚠️ Note: Reminders work on a daily schedule, not immediate testing. Use the Test Tool for immediate testing.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "✅ Daily reminders disabled", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("NotificationSettings", "❌ Failed to save settings", e)
                        Toast.makeText(this, "❌ Failed to save settings: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMaintenanceModeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_maintenance_mode, null)
        val switchMaintenanceMode = dialogView.findViewById<Switch>(R.id.switchMaintenanceMode)
        val etMaintenanceMessage = dialogView.findViewById<EditText>(R.id.etMaintenanceMessage)
        val switchAdminOverride = dialogView.findViewById<Switch>(R.id.switchAdminOverride)

        // Load current values
        settingsRef.get().addOnSuccessListener { document ->
            val maintenance = document.data?.get("maintenance") as? Map<String, Any> ?: emptyMap()
            switchMaintenanceMode.isChecked = maintenance["maintenanceMode"] as? Boolean ?: false
            etMaintenanceMessage.setText(maintenance["maintenanceMessage"] as? String ?: "System under maintenance. Please try again later.")
            switchAdminOverride.isChecked = maintenance["adminOverride"] as? Boolean ?: true
        }

        AlertDialog.Builder(this)
            .setTitle("System Maintenance")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val updates = mapOf(
                    "maintenance.maintenanceMode" to switchMaintenanceMode.isChecked,
                    "maintenance.maintenanceMessage" to etMaintenanceMessage.text.toString().trim(),
                    "maintenance.adminOverride" to switchAdminOverride.isChecked
                )
                updateSettings(updates)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUserManagementDialog() {
        val details = """
👥 User Management Settings

📊 Current Configuration:
• Auto-approve student registrations: Enabled
• Email verification required: Yes
• Maximum students per admin: 100
• Student data retention: 2 years
• Profile picture uploads: Enabled

🔐 Access Control:
• Student can edit profile: Yes
• Student can delete account: No
• Admin approval for sensitive changes: Yes

📧 Communication:
• Welcome email to new students: Enabled
• Progress notifications: Weekly
• Achievement notifications: Enabled

💡 Note: These settings control how student accounts are managed and what permissions students have within the platform.
        """.trimIndent()

        android.app.AlertDialog.Builder(this)
            .setTitle("User Management Settings")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showAdminFeaturesDialog() {
        val details = """
⚙️ Admin Features Configuration

🎛️ Available Features:
• Content Management: ✅ Enabled
• Student Analytics: ✅ Enabled
• Platform Settings: ✅ Enabled
• User Role Management: ✅ Enabled
• Data Export: ✅ Enabled
• System Monitoring: ✅ Enabled

🔧 Advanced Features:
• Bulk Operations: ✅ Enabled
• API Access: ❌ Disabled
• Custom Branding: ✅ Enabled
• Multi-language Support: ❌ Disabled
• Advanced Analytics: ✅ Enabled

📱 Mobile Features:
• Push Notifications: ✅ Enabled
• Offline Mode: ❌ Disabled
• Mobile Admin App: ❌ Disabled

💡 Note: These features control what administrative capabilities are available to admin users.
        """.trimIndent()

        android.app.AlertDialog.Builder(this)
            .setTitle("Admin Features Configuration")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun updateSettings(updates: Map<String, Any>) {
        // Use set with merge to create document if it doesn't exist
        settingsRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Settings updated successfully", Toast.LENGTH_SHORT).show()
                loadCurrentSettings() // Refresh UI
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AdminSettings", "Failed to update settings", e)
                Toast.makeText(this, "❌ Failed to update settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun saveAllSettings() {
        Toast.makeText(this, "All settings are auto-saved when you make changes", Toast.LENGTH_SHORT).show()
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Reset to Defaults")
            .setMessage("Are you sure you want to reset all platform settings to their default values? This action cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                createDefaultSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createDefaultSettings() {
        val defaultSettings = mapOf(
            "general" to mapOf(
                "primaryColor" to "#4CAF50",
                "secondaryColor" to "#2196F3",
                "quizPassingScore" to 80L,
                "maxQuizAttempts" to 3L
            ),
            "features" to mapOf(
                "enableModules" to true,
                "enableChallenges" to true,
                "enableLeaderboard" to true,
                "enableEcoAssistant" to true,
                "enableProgressTracker" to true,
                "enableEcosystemGame" to true
            ),
            "notifications" to mapOf(
                "enablePushNotifications" to true,
                "enableDailyReminders" to true,
                "dailyReminderTime" to "18:00",
                "reminderMessage" to "Time to learn something new! 🌱",
                "quietHoursStart" to "22:00",
                "quietHoursEnd" to "08:00"
            ),
            "maintenance" to mapOf(
                "maintenanceMode" to false,
                "maintenanceMessage" to "System under maintenance. Please try again later.",
                "adminOverride" to true
            )
        )

        settingsRef.set(defaultSettings)
            .addOnSuccessListener {
                Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                loadCurrentSettings()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to reset settings", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
