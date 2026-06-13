package com.example.capstone

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone.databinding.ActivityChallengeDetailBinding
import com.example.capstone.utils.ActivityLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ChallengeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChallengeDetailBinding
    private lateinit var challengeId: String
    private lateinit var titleText: String
    private var points: Long = 0
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedImageUri = it
                binding.imgProof.setImageURI(it)
                binding.uploadPlaceholder.visibility = View.GONE
                binding.btnPickImage.visibility = View.VISIBLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChallengeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        challengeId = intent.getStringExtra("challengeId") ?: ""
        titleText = intent.getStringExtra("title") ?: "Eco Challenge"
        points = intent.getLongExtra("points", 0)

        binding.tvTitle.text = titleText
        binding.tvPoints.text = "⭐ $points EcoPoints"

        binding.btnPickImage.visibility = View.GONE

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.cardImageUpload.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnSubmit.setOnClickListener {
            submitChallenge()
        }
        
        // Check if user has already submitted this challenge
        checkExistingSubmission()
    }
    
    private fun checkExistingSubmission() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        
        db.collection("ChallengeSubmissions")
            .whereEqualTo("challengeId", challengeId)
            .whereEqualTo("studentId", uid)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // User has already submitted this challenge
                    val submission = documents.documents[0]
                    val status = submission.getString("status") ?: "pending"
                    
                    when (status) {
                        "pending" -> {
                            // Submission is pending review
                            disableSubmission("⏳ Submission Pending Review", 
                                "Your challenge submission is awaiting admin review. You'll be notified once it's reviewed.")
                        }
                        "approved" -> {
                            // Submission was approved
                            disableSubmission("✅ Challenge Completed!", 
                                "You've already completed this challenge and earned $points EcoPoints!")
                        }
                        "rejected" -> {
                            // Submission was rejected - allow resubmission
                            val feedback = submission.getString("adminFeedback") ?: "No feedback provided"
                            showRejectionFeedback(feedback)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChallengeDetail", "Failed to check existing submission", e)
            }
    }
    
    private fun disableSubmission(title: String, message: String) {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Already Submitted"
        binding.cardImageUpload.isEnabled = false
        binding.cardImageUpload.alpha = 0.5f
        
        // Show only dialog, no toast
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showRejectionFeedback(feedback: String) {
        // Show rejection feedback but allow resubmission
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("❌ Previous Submission Rejected")
            .setMessage("Admin Feedback:\n\n$feedback\n\nYou can submit again with improvements.")
            .setPositiveButton("Submit Again") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun submitChallenge() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val note = binding.etNote.text.toString().trim()
        val imageUri = selectedImageUri

        if (imageUri == null) {
            Toast.makeText(this, "Please upload proof image", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Uploading Proof..."

        uploadImageToStorage(uid, imageUri, note)
    }

    private fun uploadImageToStorage(uid: String, uri: Uri, note: String) {
        val storageRef = FirebaseStorage.getInstance().reference
            .child("challengeProofs/$uid/${System.currentTimeMillis()}.jpg")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    saveSubmissionAndPoints(uid, downloadUri.toString(), note)
                }
            }
            .addOnFailureListener { e ->
                resetSubmitButton()
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveSubmissionAndPoints(uid: String, proofUrl: String, note: String) {
        val db = FirebaseFirestore.getInstance()
        
        // Get student info including teacherId and batchId
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val studentName = userDoc.getString("name") ?: "Unknown Student"
                val batchId = userDoc.getString("batchId") // NEW: Get student's batch
                val schoolId = userDoc.getString("schoolId") // NEW: Get student's school
                
                // NEW: Get teacher ID from batch (if student has a batch)
                if (batchId != null) {
                    db.collection("Batches").document(batchId).get()
                        .addOnSuccessListener { batchDoc ->
                            val teacherId = batchDoc.getString("teacherId")
                            saveSubmissionToFirestore(uid, studentName, proofUrl, note, teacherId, batchId, schoolId)
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.w("ChallengeDetail", "Failed to get batch info, saving without teacherId", e)
                            saveSubmissionToFirestore(uid, studentName, proofUrl, note, null, batchId, schoolId)
                        }
                } else {
                    // Student has no batch, save without teacherId
                    android.util.Log.d("ChallengeDetail", "Student has no batch, saving without teacherId")
                    saveSubmissionToFirestore(uid, studentName, proofUrl, note, null, null, schoolId)
                }
            }
            .addOnFailureListener { e ->
                resetSubmitButton()
                Toast.makeText(this, "Failed to get user info: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    /**
     * NEW: Save submission to Firestore with teacherId and batchId
     */
    private fun saveSubmissionToFirestore(
        uid: String,
        studentName: String,
        proofUrl: String,
        note: String,
        teacherId: String?,
        batchId: String?,
        schoolId: String?
    ) {
        val db = FirebaseFirestore.getInstance()
        
        // Save to ChallengeSubmissions collection for admin review
        val submissionData = hashMapOf(
            "challengeId" to challengeId,
            "challengeTitle" to titleText,
            "challengePoints" to points,
            "studentId" to uid,
            "studentName" to studentName,
            "photoUrl" to proofUrl,
            "status" to "pending",
            "submittedAt" to FieldValue.serverTimestamp(),
            "reviewedBy" to "",
            "reviewedAt" to null,
            "adminFeedback" to "",
            "teacherId" to (teacherId ?: ""), // NEW: Add teacher reference
            "batchId" to (batchId ?: ""),     // NEW: Add batch reference
            "schoolId" to (schoolId ?: "")    // NEW: Add school reference
        )
        
        android.util.Log.d("ChallengeDetail", "Saving submission with teacherId: $teacherId, batchId: $batchId, schoolId: $schoolId")
        
        db.collection("ChallengeSubmissions")
            .add(submissionData)
            .addOnSuccessListener {
                // Log activity to feed
                ActivityLogger.logChallengeSubmitted(titleText)
                
                Toast.makeText(
                    this, 
                    "Challenge submitted! 🌱 Awaiting admin review", 
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            .addOnFailureListener { e ->
                resetSubmitButton()
                Toast.makeText(this, "Failed to submit: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resetSubmitButton() {
        binding.btnSubmit.isEnabled = true
        binding.btnSubmit.text = "Submit Challenge"
    }
}
