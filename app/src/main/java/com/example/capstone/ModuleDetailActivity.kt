package com.example.capstone

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone.databinding.ActivityModuleDetailBinding
import com.example.capstone.utils.ActivityLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ModuleDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModuleDetailBinding

    private lateinit var moduleId: String
    private lateinit var moduleTitle: String
    private var modulePoints: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModuleDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        moduleId = intent.getStringExtra("moduleId") ?: ""
        moduleTitle = intent.getStringExtra("title") ?: ""
        val content = intent.getStringExtra("content") ?: ""
        modulePoints = intent.getLongExtra("points", 0)

        binding.tvModuleTitle.text = moduleTitle
        binding.tvModuleContent.text = content
        binding.tvModulePoints.text = "⭐ $modulePoints EcoPoints"

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = moduleTitle

        binding.btnStartQuiz.setOnClickListener {
            val intent = Intent(this, QuizActivity::class.java)
            intent.putExtra("moduleId", moduleId)
            intent.putExtra("moduleTitle", moduleTitle)
            startActivity(intent)
        }

        // 🔒 Default locked state
        binding.btnCompleteModule.isEnabled = false
        binding.btnCompleteModule.text = "Pass Quiz to Unlock Module"

        binding.btnCompleteModule.setOnClickListener {
            completeModule()
        }
    }

    override fun onResume() {
        super.onResume()
        checkQuizStatus()
        checkIfAlreadyCompleted()
    }

    private fun checkQuizStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(userId)
            .collection("quizAttempts")
            .document(moduleId)
            .get()
            .addOnSuccessListener { doc ->

                if (doc.exists()) {
                    val passed = doc.getBoolean("passed") ?: false
                    val percentage = doc.getLong("percentage") ?: 0

                    if (passed) {
                        binding.btnCompleteModule.isEnabled = true
                        binding.btnCompleteModule.text = "Complete Module"
                    } else {
                        binding.btnCompleteModule.isEnabled = false
                        binding.btnCompleteModule.text = "Quiz Not Passed ($percentage%)"
                    }
                } else {
                    binding.btnCompleteModule.isEnabled = false
                    binding.btnCompleteModule.text = "Pass Quiz to Unlock Module"
                }
            }
            .addOnFailureListener {
                binding.btnCompleteModule.isEnabled = false
                binding.btnCompleteModule.text = "Pass Quiz to Unlock Module"
            }
    }

    private fun checkIfAlreadyCompleted() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("Users")
            .document(userId)
            .collection("completions")
            .document(moduleId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    binding.btnCompleteModule.isEnabled = false
                    binding.btnCompleteModule.text = "Completed ✅"
                }
            }
    }

    private fun completeModule() {

        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val studentRef = db.collection("Users").document(userId)
        val completionRef = studentRef.collection("completions").document(moduleId)

        db.runTransaction { transaction ->

            val completionSnapshot = transaction.get(completionRef)

            if (completionSnapshot.exists()) {
                throw Exception("Module already completed")
            }

            val completionData = hashMapOf(
                "title" to moduleTitle,
                "points" to modulePoints,
                "timestamp" to FieldValue.serverTimestamp()
            )

            transaction.set(completionRef, completionData)

            transaction.update(
                studentRef,
                "ecoPoints",
                FieldValue.increment(modulePoints)
            )

        }.addOnSuccessListener {

            // Log module completion to activity feed
            ActivityLogger.logModuleCompleted(moduleTitle, modulePoints)

            Toast.makeText(
                this,
                "Module Completed! 🌱 +$modulePoints EcoPoints",
                Toast.LENGTH_SHORT
            ).show()

            binding.btnCompleteModule.isEnabled = false
            binding.btnCompleteModule.text = "Completed ✅"

        }.addOnFailureListener { e ->
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }
}
