package com.example.capstone

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("EcoSphereSettings", Context.MODE_PRIVATE)

        setupClickListeners()
        loadSettings()
    }

    private fun setupClickListeners() {
        // Save notification preference when changed
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit()
                .putBoolean("notifications_enabled", isChecked)
                .apply()
            
            Toast.makeText(
                this,
                if (isChecked) "Notifications enabled" else "Notifications disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnClearCache.setOnClickListener {
            // Clear app cache (simulate)
            val cacheCleared = clearAppCache()
            if (cacheCleared) {
                Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to clear cache", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            Toast.makeText(this, "Privacy Policy - Coming Soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettings() {
        // Load saved notification preference
        val notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
        binding.switchNotifications.isChecked = notificationsEnabled
    }

    private fun clearAppCache(): Boolean {
        return try {
            // Clear SharedPreferences cache (except settings)
            val tempPrefs = getSharedPreferences("TempData", Context.MODE_PRIVATE)
            tempPrefs.edit().clear().apply()
            
            // In a real app, you might also clear image cache, temporary files, etc.
            // For now, we'll just simulate cache clearing
            true
        } catch (e: Exception) {
            false
        }
    }
}
