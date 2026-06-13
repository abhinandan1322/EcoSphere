package com.example.capstone.repository

import android.net.Uri
import com.example.capstone.models.Batch
import com.example.capstone.models.School
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    fun registerUser(
        name: String,
        email: String,
        password: String,
        role: String,
        phone: String,
        dob: String,
        gender: String,
        profileImageUri: Uri?,
        onResult: (Boolean, String?) -> Unit
    ) {

        println("=== AUTH REPOSITORY: Starting registration ===")
        println("Profile image URI: $profileImageUri")

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->

                val uid = authResult.user?.uid

                if (uid == null) {
                    println("ERROR: User ID is null after registration")
                    onResult(false, "User ID is null")
                    return@addOnSuccessListener
                }

                println("User created successfully with UID: $uid")

                // If profile image is selected, upload it first
                if (profileImageUri != null) {
                    println("Profile image provided, starting upload...")
                    uploadProfileImage(uid, profileImageUri) { imageUrl ->
                        println("Upload callback received. Image URL: $imageUrl")
                        saveUserData(uid, name, email, role, phone, dob, gender, imageUrl, null, null, false, onResult)
                    }
                } else {
                    println("No profile image provided, saving user data without image")
                    saveUserData(uid, name, email, role, phone, dob, gender, null, null, null, false, onResult)
                }
            }
            .addOnFailureListener { e ->
                println("ERROR: Failed to create user: ${e.message}")
                onResult(false, e.message)
            }
    }
    
    /**
     * NEW: Register user with school and batch information (multi-tenancy)
     */
    fun registerUserWithSchool(
        name: String,
        email: String,
        password: String,
        role: String,
        phone: String,
        dob: String,
        gender: String,
        profileImageUri: Uri?,
        school: School,
        batch: Batch?,
        isFirstTeacher: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        println("=== AUTH REPOSITORY: Starting registration with school ===")
        println("School: ${school.schoolName}")
        println("Batch: ${batch?.batchName}")
        println("Is First Teacher: $isFirstTeacher")

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->

                val uid = authResult.user?.uid

                if (uid == null) {
                    println("ERROR: User ID is null after registration")
                    onResult(false, "User ID is null")
                    return@addOnSuccessListener
                }

                println("User created successfully with UID: $uid")

                // If profile image is selected, upload it first
                if (profileImageUri != null) {
                    println("Profile image provided, starting upload...")
                    uploadProfileImage(uid, profileImageUri) { imageUrl ->
                        println("Upload callback received. Image URL: $imageUrl")
                        saveUserData(uid, name, email, role, phone, dob, gender, imageUrl, school, batch, isFirstTeacher, onResult)
                    }
                } else {
                    println("No profile image provided, saving user data without image")
                    saveUserData(uid, name, email, role, phone, dob, gender, null, school, batch, isFirstTeacher, onResult)
                }
            }
            .addOnFailureListener { e ->
                println("ERROR: Failed to create user: ${e.message}")
                onResult(false, e.message)
            }
    }

    private fun uploadProfileImage(uid: String, imageUri: Uri, onComplete: (String?) -> Unit) {
        val storageRef = storage.reference.child("profile_pictures/$uid.jpg")
        
        println("Starting profile picture upload for user: $uid")
        println("Image URI: $imageUri")
        
        storageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                println("Profile picture uploaded successfully")
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val downloadUrl = uri.toString()
                    println("Download URL obtained: $downloadUrl")
                    onComplete(downloadUrl)
                }.addOnFailureListener { e ->
                    println("Failed to get download URL: ${e.message}")
                    onComplete(null)
                }
            }
            .addOnFailureListener { e ->
                println("Failed to upload profile picture: ${e.message}")
                onComplete(null)
            }
    }

    private fun saveUserData(
        uid: String,
        name: String,
        email: String,
        role: String,
        phone: String,
        dob: String,
        gender: String,
        profilePictureUrl: String?,
        school: School?,
        batch: Batch?,
        isFirstTeacher: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        val userMap = hashMapOf<String, Any>(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "role" to role,
            "phone" to phone,
            "dob" to dob,
            "gender" to gender,
            "profilePictureUrl" to (profilePictureUrl ?: ""),
            "ecoPoints" to 0
        )

        println("=== SAVING USER DATA TO FIRESTORE ===")
        println("UID: $uid")
        println("Profile Picture URL: ${profilePictureUrl ?: "NONE"}")
        
        // NEW: Add multi-tenancy fields
        if (school != null) {
            userMap["schoolId"] = school.schoolId
            userMap["schoolCode"] = school.schoolCode
            
            println("School ID: ${school.schoolId}")
            println("School Code: ${school.schoolCode}")
            
            if (role == "teacher") {
                userMap["isLeadTeacher"] = isFirstTeacher
                userMap["isApproved"] = isFirstTeacher // Auto-approve first teacher
                userMap["assignedBatches"] = listOf<String>()
                
                println("Is Lead Teacher: $isFirstTeacher")
                println("Is Approved: $isFirstTeacher")
            } else if (role == "student" && batch != null) {
                userMap["batchId"] = batch.batchId
                userMap["teacherId"] = batch.teacherId
                
                println("Batch ID: ${batch.batchId}")
                println("Teacher ID: ${batch.teacherId}")
            }
        }

        firestore.collection("Users")
            .document(uid)
            .set(userMap)
            .addOnSuccessListener {
                println("SUCCESS: User data saved to Firestore")
                
                // Update school counters
                if (school != null) {
                    updateSchoolCounters(school.schoolId, role)
                }
                
                // Update batch studentCount when a student registers
                if (role == "student" && batch != null) {
                    firestore.collection("Batches")
                        .document(batch.batchId)
                        .update("studentCount", FieldValue.increment(1))
                        .addOnSuccessListener {
                            println("SUCCESS: Updated studentCount for batch ${batch.batchId}")
                        }
                        .addOnFailureListener { e ->
                            println("ERROR: Failed to update batch studentCount: ${e.message}")
                        }
                }
                
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                println("ERROR: Failed to save user data: ${e.message}")
                onResult(false, e.message)
            }
    }
    
    /**
     * NEW: Update school counters when a new user registers
     */
    private fun updateSchoolCounters(schoolId: String, role: String) {
        val field = if (role == "teacher") "totalTeachers" else "totalStudents"
        
        firestore.collection("Schools")
            .document(schoolId)
            .update(field, FieldValue.increment(1))
            .addOnSuccessListener {
                println("SUCCESS: Updated $field counter for school $schoolId")
            }
            .addOnFailureListener { e ->
                println("ERROR: Failed to update school counter: ${e.message}")
            }
    }
}
