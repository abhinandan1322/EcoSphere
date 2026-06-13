package com.example.capstone

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var selectedRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedRole = intent.getStringExtra("ROLE")

        // Hide register link if admin selected
        if (selectedRole == "admin") {
            binding.tvRegister.visibility = View.GONE
        } else {
            setupRegisterClickableText()
        }

        binding.btnLogin.setOnClickListener {

            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "All fields required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(email, password)
        }
    }

    private fun setupRegisterClickableText() {
        // "Don't have an account?" is already in the XML as a static TextView
        // This TextView only needs to show the clickable "Register" word
        val fullText = "Register"
        val spannable = SpannableString(fullText)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
                intent.putExtra("ROLE", selectedRole)
                startActivity(intent)
            }
        }

        spannable.setSpan(clickableSpan, 0, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(Color.parseColor("#4CAF50")), 0, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.tvRegister.text = spannable
        binding.tvRegister.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun loginUser(email: String, password: String) {

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {

                val uid = auth.currentUser?.uid

                if (uid == null) {
                    Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                firestore.collection("Users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { document ->

                        if (!document.exists()) {
                            auth.signOut()
                            Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        val actualRole = document.getString("role")

                        if (actualRole == selectedRole) {
                            
                            // Check teacher approval status
                            if (actualRole == "teacher" || actualRole == "admin") {
                                val isApproved = document.getBoolean("isApproved") ?: true
                                val isRejected = document.getBoolean("isRejected") ?: false
                                
                                if (isRejected) {
                                    // Teacher was explicitly rejected
                                    auth.signOut()
                                    androidx.appcompat.app.AlertDialog.Builder(this)
                                        .setTitle("❌ Registration Rejected")
                                        .setMessage("Your registration has been rejected by the lead teacher.\n\nPlease contact your school administrator for more information.")
                                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                                        .show()
                                    return@addOnSuccessListener
                                }
                                
                                if (!isApproved) {
                                    // Teacher not approved yet
                                    auth.signOut()
                                    showPendingApprovalDialog()
                                    return@addOnSuccessListener
                                }
                            }

                            Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()

                            // Update lastLoginAt AND calculate streak at login time
                            // Must read previous lastLoginAt BEFORE overwriting it
                            val prevLastLoginAt = document.getTimestamp("lastLoginAt")
                            val prevStreak = document.getLong("currentStreak")?.toInt() ?: 0

                            val newStreak = calculateLoginStreak(prevLastLoginAt, prevStreak)

                            firestore.collection("Users").document(uid)
                                .update(
                                    mapOf(
                                        "lastLoginAt" to com.google.firebase.Timestamp.now(),
                                        "currentStreak" to newStreak,
                                        "lastStreakUpdate" to com.google.firebase.Timestamp.now()
                                    )
                                )
                                .addOnFailureListener { e ->
                                    android.util.Log.w("LoginActivity", "Failed to update login data", e)
                                }

                            if (actualRole == "student") {
                                startActivity(Intent(this, StudentMainActivity::class.java))
                            } else if (actualRole == "admin" || actualRole == "teacher") {
                                startActivity(Intent(this, AdminMainActivity::class.java))
                            }

                            finish()

                        } else {

                            // ROLE MISMATCH → BLOCK ACCESS
                            auth.signOut()

                            Toast.makeText(
                                this,
                                "Access denied. You are not registered as $selectedRole.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .addOnFailureListener {
                        auth.signOut()
                        Toast.makeText(this, "Failed to fetch user role", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: "Login Failed", Toast.LENGTH_SHORT).show()
            }
    }
    
    /**
     * NEW: Show pending approval dialog for unapproved teachers
     */
    private fun showPendingApprovalDialog() {
        AlertDialog.Builder(this)
            .setTitle("⏳ Registration Pending")
            .setMessage("Your registration is pending approval from the lead teacher of your school.\n\nYou will be able to login once your account is approved.\n\nPlease contact your school's lead teacher for approval.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Calculate streak at login time using the PREVIOUS lastLoginAt
     * before it gets overwritten with today's timestamp.
     */
    private fun calculateLoginStreak(
        lastLoginAt: com.google.firebase.Timestamp?,
        currentStreak: Int
    ): Int {
        if (lastLoginAt == null) return 1 // First ever login

        val lastLoginDate = java.util.Calendar.getInstance().apply {
            timeInMillis = lastLoginAt.toDate().time
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val daysDifference = ((today.timeInMillis - lastLoginDate.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()

        return when {
            daysDifference == 0 -> currentStreak       // Same day login, keep streak
            daysDifference == 1 -> currentStreak + 1   // Consecutive day, increment
            else -> 1                                   // Streak broken, reset to 1
        }
    }
}
