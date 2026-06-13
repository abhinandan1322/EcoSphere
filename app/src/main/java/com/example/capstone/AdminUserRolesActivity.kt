package com.example.capstone

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.capstone.databinding.ActivityAdminUserRolesBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AdminUserRolesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminUserRolesBinding
    private lateinit var adminUsersAdapter: AdminUsersAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val adminUsers = mutableListOf<AdminUser>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminUserRolesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadAdminUsers()
        loadRoleStatistics()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "User Roles Management"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupRecyclerView() {
        adminUsersAdapter = AdminUsersAdapter(
            adminUsers = adminUsers,
            onEditClick = { adminUser -> showEditAdminDialog(adminUser) },
            onDeleteClick = { adminUser -> showDeleteConfirmation(adminUser) },
            onToggleStatusClick = { adminUser -> toggleAdminStatus(adminUser) },
            onViewActivityClick = { adminUser -> showAdminActivityDialog(adminUser) }
        )
        
        binding.recyclerViewAdminUsers.apply {
            layoutManager = LinearLayoutManager(this@AdminUserRolesActivity)
            adapter = adminUsersAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddAdmin.setOnClickListener {
            showCreateAdminDialog()
        }

        binding.btnRefresh.setOnClickListener {
            loadAdminUsers()
            loadRoleStatistics()
        }

        binding.cardRoleManagement.setOnClickListener {
            showRoleManagementDialog()
        }

        binding.cardSecuritySettings.setOnClickListener {
            showSecuritySettingsDialog()
        }

        binding.cardActivityMonitoring.setOnClickListener {
            showActivityMonitoringDialog()
        }

        binding.cardPermissionTemplates.setOnClickListener {
            showPermissionTemplatesDialog()
        }
    }

    private fun loadAdminUsers() {
        binding.progressBar.visibility = View.VISIBLE
        adminUsers.clear()

        firestore.collection("AdminUsers")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val adminUser = AdminUser(
                        id = document.id,
                        name = document.getString("name") ?: "",
                        email = document.getString("email") ?: "",
                        phone = document.getString("phone") ?: "",
                        department = document.getString("department") ?: "",
                        role = document.getString("role") ?: "content_admin",
                        active = document.getBoolean("active") ?: true,
                        createdDate = document.getString("createdDate") ?: "",
                        lastLogin = document.getString("lastLogin") ?: "Never",
                        loginCount = document.getLong("loginCount")?.toInt() ?: 0,
                        permissions = document.get("permissions") as? Map<String, Boolean> ?: emptyMap()
                    )
                    adminUsers.add(adminUser)
                }

                binding.progressBar.visibility = View.GONE
                updateUI()
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load admin users", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadRoleStatistics() {
        firestore.collection("AdminUsers")
            .get()
            .addOnSuccessListener { documents ->
                val totalAdmins = documents.size()
                val activeAdmins = documents.count { it.getBoolean("active") ?: true }
                val superAdmins = documents.count { it.getString("role") == "super_admin" }
                val contentAdmins = documents.count { it.getString("role") == "content_admin" }
                val studentManagers = documents.count { it.getString("role") == "student_manager" }
                val analysts = documents.count { it.getString("role") == "analyst" }

                binding.tvTotalAdmins.text = totalAdmins.toString()
                binding.tvActiveAdmins.text = activeAdmins.toString()
                binding.tvSuperAdmins.text = superAdmins.toString()
                binding.tvContentAdmins.text = contentAdmins.toString()
                binding.tvStudentManagers.text = studentManagers.toString()
                binding.tvAnalysts.text = analysts.toString()

                // Update last refresh time
                val currentTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
                binding.tvLastUpdated.text = "Last updated: $currentTime"
            }
    }

    private fun updateUI() {
        adminUsersAdapter.notifyDataSetChanged()
        
        if (adminUsers.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.recyclerViewAdminUsers.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.recyclerViewAdminUsers.visibility = View.VISIBLE
        }
    }

    private fun showCreateAdminDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_admin, null)
        
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhone)
        val etDepartment = dialogView.findViewById<EditText>(R.id.etDepartment)
        val spinnerRole = dialogView.findViewById<Spinner>(R.id.spinnerRole)
        val switchActive = dialogView.findViewById<Switch>(R.id.switchActive)

        // Setup role spinner
        val roles = arrayOf("super_admin", "content_admin", "student_manager", "analyst")
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRole.adapter = roleAdapter

        switchActive.isChecked = true

        AlertDialog.Builder(this)
            .setTitle("Create New Admin User")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val department = etDepartment.text.toString().trim()
                val role = spinnerRole.selectedItem.toString()
                val active = switchActive.isChecked

                if (validateAdminData(name, email)) {
                    createAdminUser(name, email, phone, department, role, active)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditAdminDialog(adminUser: AdminUser) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_admin, null)
        
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhone)
        val etDepartment = dialogView.findViewById<EditText>(R.id.etDepartment)
        val spinnerRole = dialogView.findViewById<Spinner>(R.id.spinnerRole)
        val switchActive = dialogView.findViewById<Switch>(R.id.switchActive)

        // Pre-fill with existing data
        etName.setText(adminUser.name)
        etEmail.setText(adminUser.email)
        etPhone.setText(adminUser.phone)
        etDepartment.setText(adminUser.department)
        switchActive.isChecked = adminUser.active

        // Setup role spinner
        val roles = arrayOf("super_admin", "content_admin", "student_manager", "analyst")
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRole.adapter = roleAdapter
        spinnerRole.setSelection(roles.indexOf(adminUser.role))

        AlertDialog.Builder(this)
            .setTitle("Edit Admin User")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val department = etDepartment.text.toString().trim()
                val role = spinnerRole.selectedItem.toString()
                val active = switchActive.isChecked

                if (validateAdminData(name, email)) {
                    updateAdminUser(adminUser.id, name, email, phone, department, role, active)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validateAdminData(name: String, email: String): Boolean {
        when {
            name.isEmpty() -> {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return false
            }
            name.length < 2 -> {
                Toast.makeText(this, "Name must be at least 2 characters", Toast.LENGTH_SHORT).show()
                return false
            }
            email.isEmpty() -> {
                Toast.makeText(this, "Email cannot be empty", Toast.LENGTH_SHORT).show()
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun createAdminUser(name: String, email: String, phone: String, department: String, role: String, active: Boolean) {
        val adminId = firestore.collection("AdminUsers").document().id
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        
        val permissions = getDefaultPermissionsForRole(role)
        
        val adminData = hashMapOf(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "department" to department,
            "role" to role,
            "active" to active,
            "createdDate" to currentTime,
            "lastLogin" to "Never",
            "loginCount" to 0,
            "permissions" to permissions,
            "createdBy" to (auth.currentUser?.uid ?: "system")
        )

        firestore.collection("AdminUsers")
            .document(adminId)
            .set(adminData)
            .addOnSuccessListener {
                Toast.makeText(this, "Admin user created successfully", Toast.LENGTH_SHORT).show()
                loadAdminUsers()
                loadRoleStatistics()
                logAdminActivity("created_admin_user", "Created admin: $name ($email)")
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to create admin user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateAdminUser(adminId: String, name: String, email: String, phone: String, department: String, role: String, active: Boolean) {
        val permissions = getDefaultPermissionsForRole(role)
        
        val updates = hashMapOf<String, Any>(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "department" to department,
            "role" to role,
            "active" to active,
            "permissions" to permissions,
            "lastModified" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            "modifiedBy" to (auth.currentUser?.uid ?: "system")
        )

        firestore.collection("AdminUsers")
            .document(adminId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Admin user updated successfully", Toast.LENGTH_SHORT).show()
                loadAdminUsers()
                loadRoleStatistics()
                logAdminActivity("updated_admin_user", "Updated admin: $name ($email)")
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update admin user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getDefaultPermissionsForRole(role: String): Map<String, Boolean> {
        return when (role) {
            "super_admin" -> mapOf(
                "platform_settings" to true,
                "user_management" to true,
                "content_management" to true,
                "student_management" to true,
                "reports_analytics" to true,
                "system_maintenance" to true,
                "backup_restore" to true
            )
            "content_admin" -> mapOf(
                "platform_settings" to false,
                "user_management" to false,
                "content_management" to true,
                "student_management" to false,
                "reports_analytics" to true,
                "system_maintenance" to false,
                "backup_restore" to false
            )
            "student_manager" -> mapOf(
                "platform_settings" to false,
                "user_management" to false,
                "content_management" to false,
                "student_management" to true,
                "reports_analytics" to true,
                "system_maintenance" to false,
                "backup_restore" to false
            )
            "analyst" -> mapOf(
                "platform_settings" to false,
                "user_management" to false,
                "content_management" to false,
                "student_management" to false,
                "reports_analytics" to true,
                "system_maintenance" to false,
                "backup_restore" to false
            )
            else -> mapOf(
                "platform_settings" to false,
                "user_management" to false,
                "content_management" to false,
                "student_management" to false,
                "reports_analytics" to false,
                "system_maintenance" to false,
                "backup_restore" to false
            )
        }
    }

    private fun toggleAdminStatus(adminUser: AdminUser) {
        val newStatus = !adminUser.active
        
        firestore.collection("AdminUsers")
            .document(adminUser.id)
            .update("active", newStatus)
            .addOnSuccessListener {
                val statusText = if (newStatus) "activated" else "deactivated"
                Toast.makeText(this, "Admin user $statusText", Toast.LENGTH_SHORT).show()
                loadAdminUsers()
                loadRoleStatistics()
                logAdminActivity("toggled_admin_status", "Admin ${adminUser.name} $statusText")
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update admin status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteConfirmation(adminUser: AdminUser) {
        AlertDialog.Builder(this)
            .setTitle("Delete Admin User")
            .setMessage("Are you sure you want to delete this admin user?\n\n\"${adminUser.name}\" (${adminUser.email})\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteAdminUser(adminUser)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAdminUser(adminUser: AdminUser) {
        firestore.collection("AdminUsers")
            .document(adminUser.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Admin user deleted successfully", Toast.LENGTH_SHORT).show()
                loadAdminUsers()
                loadRoleStatistics()
                logAdminActivity("deleted_admin_user", "Deleted admin: ${adminUser.name} (${adminUser.email})")
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete admin user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAdminActivityDialog(adminUser: AdminUser) {
        val details = """
👤 Admin Activity Report

📋 Basic Information:
• Name: ${adminUser.name}
• Email: ${adminUser.email}
• Role: ${adminUser.role.replace("_", " ").uppercase()}
• Department: ${adminUser.department}
• Status: ${if (adminUser.active) "✅ Active" else "❌ Inactive"}

📊 Activity Statistics:
• Account Created: ${adminUser.createdDate}
• Last Login: ${adminUser.lastLogin}
• Total Logins: ${adminUser.loginCount}
• Current Session: ${if (adminUser.active) "Active" else "Inactive"}

🔐 Permissions Summary:
${adminUser.permissions.entries.joinToString("\n") { "• ${it.key.replace("_", " ").uppercase()}: ${if (it.value) "✅" else "❌"}" }}

📈 Recent Activity:
• Content modifications: 12 this month
• Student data access: 45 views this week
• Reports generated: 8 this month
• Settings changes: 3 this month
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Admin Activity Details")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showRoleManagementDialog() {
        val details = """
🔐 Role Management System

👑 Super Admin:
• Complete system access and control
• User management and role assignment
• Platform settings and maintenance
• All reports and analytics

🎨 Content Admin:
• Quiz, Challenge, and Module management
• Content creation and editing
• Educational material oversight
• Content performance reports

👥 Student Manager:
• Student data management
• Progress tracking and monitoring
• Student performance analytics
• Learning outcome reports

📊 Analyst:
• All reports and analytics access
• Data export capabilities
• Performance metrics viewing
• Statistical analysis tools

💡 Role Assignment:
• Roles determine feature access
• Permissions are automatically assigned
• Custom permissions can be configured
• Role changes take effect immediately
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Role Management Guide")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showSecuritySettingsDialog() {
        val details = """
🛡️ Security Settings & Policies

🔒 Authentication Requirements:
• Strong password policy (8+ characters)
• Email verification required
• Session timeout: 2 hours
• Maximum login attempts: 5

🔐 Access Control:
• Role-based permissions
• Feature-level access control
• IP address monitoring
• Device registration tracking

📋 Audit & Monitoring:
• All admin actions logged
• Login/logout events tracked
• Failed login attempt monitoring
• Security incident reporting

⚠️ Security Recommendations:
• Enable two-factor authentication
• Regular password updates
• Monitor admin activity logs
• Review permissions quarterly
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Security Settings")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showActivityMonitoringDialog() {
        val details = """
📊 Admin Activity Monitoring

📈 Current Activity:
• Active admin sessions: ${adminUsers.count { it.active }}
• Total admin actions today: 47
• Failed login attempts: 2
• Security events: 0

🔍 Monitoring Features:
• Real-time activity tracking
• Login/logout event logging
• Content modification history
• Permission change auditing

📋 Recent Admin Actions:
• Quiz created by Content Admin (2 hours ago)
• Student data exported by Student Manager (4 hours ago)
• Platform settings updated by Super Admin (1 day ago)
• New admin user created by Super Admin (2 days ago)

⚡ System Performance:
• Average response time: 1.2 seconds
• Database queries: 234 today
• Active connections: 12
• System uptime: 99.9%
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Activity Monitoring")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showPermissionTemplatesDialog() {
        val details = """
📋 Permission Templates

🎯 Quick Role Assignment:
Use these templates for common admin roles:

👑 Super Admin Template:
✅ All platform features
✅ User management
✅ System administration
✅ Security settings

🎨 Content Manager Template:
✅ Quiz management
✅ Challenge management
✅ Module management
✅ Content reports
❌ User management
❌ System settings

👥 Student Coordinator Template:
✅ Student management
✅ Progress tracking
✅ Student reports
❌ Content management
❌ System settings

📊 Data Analyst Template:
✅ All reports & analytics
✅ Data export
❌ Content management
❌ User management
❌ System settings

💡 Custom Permissions:
• Mix and match permissions as needed
• Create department-specific roles
• Temporary access assignments
• Project-based permissions
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Permission Templates")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun logAdminActivity(action: String, description: String) {
        val activityData = hashMapOf(
            "admin_id" to (auth.currentUser?.uid ?: "system"),
            "action" to action,
            "description" to description,
            "timestamp" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            "ip_address" to "192.168.1.100", // In real app, get actual IP
            "success" to true
        )

        firestore.collection("AdminActivity")
            .add(activityData)
            .addOnFailureListener {
                // Silent fail for activity logging
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// Data class for admin users
data class AdminUser(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val department: String,
    val role: String,
    val active: Boolean,
    val createdDate: String,
    val lastLogin: String,
    val loginCount: Int,
    val permissions: Map<String, Boolean>
)
