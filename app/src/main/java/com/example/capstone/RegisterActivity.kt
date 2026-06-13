package com.example.capstone

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.capstone.databinding.ActivityRegisterBinding
import com.example.capstone.models.Batch
import com.example.capstone.models.School
import com.example.capstone.utils.SchoolRegistrationHelper
import com.example.capstone.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var viewModel: AuthViewModel
    private lateinit var firestore: FirebaseFirestore
    
    private var role: String? = null
    private var selectedImageUri: Uri? = null
    private var selectedDob: String = ""
    
    // NEW: Multi-tenancy fields
    private var selectedSchool: School? = null
    private var selectedBatch: Batch? = null
    private var availableBatches: List<Batch> = emptyList()
    private var isValidatingCode = false

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            if (selectedImageUri != null) {
                try {
                    // Take persistable URI permission
                    contentResolver.takePersistableUriPermission(
                        selectedImageUri!!,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    println("Could not take persistable permission: ${e.message}")
                }
                binding.ivProfilePicture.setImageURI(selectedImageUri)
                Toast.makeText(this, "Image selected successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        firestore = FirebaseFirestore.getInstance()

        // Get role from intent
        role = intent.getStringExtra("ROLE")

        if (role == null) {
            Toast.makeText(this, "Role not selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 👇 OBSERVE FIRST (important)
        observeViewModel()
        
        // NEW: Setup school code validation
        setupSchoolCodeValidation()

        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnUploadPicture.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            imagePickerLauncher.launch(intent)
        }

        binding.etDob.setOnClickListener {
            showDatePicker()
        }

        binding.btnRegister.setOnClickListener {

            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val dob = selectedDob
            
            val genderId = binding.rgGender.checkedRadioButtonId
            val gender = when (genderId) {
                R.id.rbMale -> "Male"
                R.id.rbFemale -> "Female"
                R.id.rbOther -> "Other"
                else -> ""
            }

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Name, Email and Password are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (phone.isEmpty() || dob.isEmpty() || gender.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // NEW: Validate school code
            if (selectedSchool == null) {
                Toast.makeText(this, "Please enter a valid school code", Toast.LENGTH_SHORT).show()
                binding.etSchoolCode.requestFocus()
                return@setOnClickListener
            }

            // NEW: Validate batch selection (for students only)
            println("DEBUG: Role = '$role', selectedBatch = $selectedBatch")
            if (role == "student") {
                if (selectedBatch == null) {
                    Toast.makeText(this, "Please select your class/batch", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } else if (role == "teacher") {
                // Teachers don't need to select batches during registration
                println("DEBUG: Teacher registration - batch selection not required")
            }

            // Disable button during registration
            binding.btnRegister.isEnabled = false
            binding.btnRegister.text = "Registering..."

            println("=== REGISTRATION DEBUG ===")
            println("Registering user with profile image: ${selectedImageUri != null}")
            println("School: ${selectedSchool?.schoolName}")
            println("Batch: ${selectedBatch?.batchName}")
            if (selectedImageUri != null) {
                println("Selected image URI: $selectedImageUri")
            } else {
                println("WARNING: No image selected!")
            }

            // NEW: For teachers, check if first teacher
            if (role == "teacher") {
                CoroutineScope(Dispatchers.IO).launch {
                    val isFirstTeacher = isFirstTeacherForSchool(selectedSchool!!.schoolId)
                    
                    withContext(Dispatchers.Main) {
                        viewModel.registerWithSchool(
                            name, email, password, role!!, phone, dob, gender,
                            selectedImageUri,
                            selectedSchool!!,
                            selectedBatch,
                            isFirstTeacher
                        )
                    }
                }
            } else {
                // Student registration
                viewModel.registerWithSchool(
                    name, email, password, role!!, phone, dob, gender,
                    selectedImageUri,
                    selectedSchool!!,
                    selectedBatch,
                    false // Students don't need this check
                )
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // NEW FUNCTIONS FOR MULTI-TENANCY
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Setup real-time school code validation
     */
    private fun setupSchoolCodeValidation() {
        // Validate only when user leaves the field (focus lost)
        // This prevents any warnings while the user is still typing
        binding.etSchoolCode.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // User finished typing and moved away from the field
                val code = binding.etSchoolCode.text.toString().trim().uppercase()
                binding.tilSchoolCode.error = null
                binding.tilSchoolCode.helperText = null

                when {
                    code.isEmpty() -> {
                        // Nothing typed — do nothing
                    }
                    code.length < 14 -> {
                        // Code is too short — show a simple hint, not an error
                        binding.tilSchoolCode.helperText = "School code must be 14 characters (${code.length}/14)"
                        binding.llSchoolConfirmation.visibility = View.GONE
                        binding.tilBatch.visibility = View.GONE
                        selectedSchool = null
                        if (role == "student") selectedBatch = null
                    }
                    else -> {
                        // Full code entered — validate against Firestore
                        validateSchoolCode(code)
                    }
                }
            } else {
                // User focused the field — clear any previous messages
                binding.tilSchoolCode.error = null
                binding.tilSchoolCode.helperText = null
            }
        }

        // Also validate when user presses Done/Enter on keyboard
        binding.etSchoolCode.setOnEditorActionListener { _, _, _ ->
            val code = binding.etSchoolCode.text.toString().trim().uppercase()
            binding.tilSchoolCode.error = null
            binding.tilSchoolCode.helperText = null
            if (code.length >= 14) {
                validateSchoolCode(code)
            } else if (code.isNotEmpty()) {
                binding.tilSchoolCode.helperText = "School code must be 14 characters (${code.length}/14)"
            }
            false
        }

        // Clear messages while user is actively typing — no warnings mid-input
        binding.etSchoolCode.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Clear all messages while typing — no interruptions
                binding.tilSchoolCode.error = null
                binding.tilSchoolCode.helperText = null

                val code = s.toString().trim().uppercase()
                if (code.length >= 14) {
                    // Auto-validate as soon as 14 chars are reached
                    validateSchoolCode(code)
                } else {
                    // Still typing — hide confirmation silently
                    binding.llSchoolConfirmation.visibility = View.GONE
                    binding.tilBatch.visibility = View.GONE
                    selectedSchool = null
                    if (role == "student") selectedBatch = null
                }
            }
        })
    }
    
    /**
     * Validate school code against Firestore
     */
    private fun validateSchoolCode(code: String) {
        if (isValidatingCode) return // Prevent multiple simultaneous validations
        
        isValidatingCode = true
        binding.tilSchoolCode.isEnabled = false
        binding.tilSchoolCode.error = null
        
        // Show loading state
        binding.tilSchoolCode.helperText = "Validating school code..."
        
        println("🔍 Validating school code: '$code' (length: ${code.length})")
        
        CoroutineScope(Dispatchers.IO).launch {
            val school = SchoolRegistrationHelper.verifySchoolCode(code, firestore)
            
            withContext(Dispatchers.Main) {
                isValidatingCode = false
                binding.tilSchoolCode.isEnabled = true
                binding.tilSchoolCode.helperText = null
                
                if (school != null) {
                    // Valid school code
                    selectedSchool = school
                    binding.tvSchoolName.text = school.schoolName
                    binding.llSchoolConfirmation.visibility = View.VISIBLE
                    binding.tilSchoolCode.error = null
                    
                    println("✅ Valid school: ${school.schoolName}")
                    println("🔍 Current role: '$role'")
                    Toast.makeText(this@RegisterActivity, "✅ Valid school code", Toast.LENGTH_SHORT).show()
                    
                    // Only load batches for students, not teachers
                    if (role == "student") {
                        println("📚 Loading batches for student registration")
                        loadBatchesForSchool(school.schoolId)
                    } else {
                        println("👨‍🏫 Teacher registration - skipping batch loading")
                    }
                } else {
                    // Invalid school code
                    binding.llSchoolConfirmation.visibility = View.GONE
                    binding.tilBatch.visibility = View.GONE
                    binding.tilSchoolCode.error = "No school found with this code"
                    selectedSchool = null
                    // Only reset batch for students, teachers don't need batches
                    if (role == "student") {
                        selectedBatch = null
                    }
                    
                    println("❌ Invalid school code: '$code'")
                    Toast.makeText(this@RegisterActivity, "❌ No school found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Load available batches for the selected school (students only)
     */
    private fun loadBatchesForSchool(schoolId: String) {
        println("🔍 Loading batches for schoolId: '$schoolId'")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // First, let's check ALL batches to debug
                println("📡 DEBUG: Querying ALL batches first...")
                val allBatches = firestore.collection("Batches")
                    .get()
                    .await()
                
                println("📊 DEBUG: Total batches in database: ${allBatches.size()}")
                allBatches.documents.forEach { doc ->
                    println("  DEBUG Batch: ${doc.id}")
                    println("    - batchName: ${doc.getString("batchName")}")
                    println("    - schoolId: '${doc.getString("schoolId")}'")
                    println("    - isActive: ${doc.getBoolean("isActive")}")
                    println("    - teacherName: ${doc.getString("teacherName")}")
                }
                
                // Now query for specific school
                println("📡 Querying Firestore for batches with schoolId='$schoolId'...")
                val querySnapshot = firestore.collection("Batches")
                    .whereEqualTo("schoolId", schoolId)
                    .get()
                    .await()
                
                println("📊 Query completed. Found ${querySnapshot.size()} batches for schoolId='$schoolId'")
                
                val batches = querySnapshot.toObjects(Batch::class.java)
                
                batches.forEachIndexed { index, batch ->
                    println("  Batch $index: ${batch.batchName} (ID: ${batch.batchId})")
                    println("    - schoolId: '${batch.schoolId}'")
                    println("    - teacherId: ${batch.teacherId}")
                    println("    - isActive: ${batch.isActive}")
                }
                
                withContext(Dispatchers.Main) {
                    availableBatches = batches
                    
                    if (batches.isEmpty()) {
                        // For students: Show message that no batches are available
                        if (role == "student") {
                            println("⚠️ No batches found for this school")
                            println("⚠️ Expected schoolId: '$schoolId'")
                            Toast.makeText(
                                this@RegisterActivity,
                                "No classes available yet. Please ask your teacher to create a class first.",
                                Toast.LENGTH_LONG
                            ).show()
                            binding.tilBatch.visibility = View.GONE
                        }
                        // For teachers: This is normal, they don't need to select batches
                    } else {
                        // Only show batch selection for students
                        if (role == "student") {
                            println("✅ Setting up batch dropdown with ${batches.size} batches")
                            setupBatchDropdown(batches)
                        } else {
                            println("⚠️ Role is '$role', not showing batch dropdown")
                        }
                    }
                }
            } catch (e: Exception) {
                println("❌ Error loading batches: ${e.message}")
                e.printStackTrace()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Error loading batches: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Setup batch selection dropdown
     */
    private fun setupBatchDropdown(batches: List<Batch>) {
        println("🎨 Setting up batch dropdown UI")
        
        val batchNames = batches.map { 
            "${it.batchName} (Teacher: ${it.teacherName})" 
        }
        
        println("📝 Batch names: $batchNames")
        
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            batchNames
        )
        
        binding.actvBatch.setAdapter(adapter)
        binding.tilBatch.visibility = View.VISIBLE
        
        println("✅ Batch dropdown is now VISIBLE")
        
        binding.actvBatch.setOnItemClickListener { _, _, position, _ ->
            selectedBatch = batches[position]
            println("✅ Selected batch: ${selectedBatch?.batchName} (ID: ${selectedBatch?.batchId})")
        }
    }
    
    /**
     * Check if this is the first teacher for the school
     */
    private suspend fun isFirstTeacherForSchool(schoolId: String): Boolean {
        return try {
            val teachers = firestore.collection("Users")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("role", "teacher")
                .get()
                .await()
            
            val isFirst = teachers.isEmpty()
            println("Is first teacher for $schoolId: $isFirst")
            isFirst
        } catch (e: Exception) {
            println("Error checking first teacher: ${e.message}")
            false
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                selectedDob = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
                binding.etDob.setText(selectedDob)
            },
            year,
            month,
            day
        )
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun observeViewModel() {
        viewModel.registerState.observe(this) { result ->

            println("REGISTER RESULT: ${result.first}, ${result.second}")

            // Re-enable button
            binding.btnRegister.isEnabled = true
            binding.btnRegister.text = "Register"

            if (result.first) {
                // Check if this is an unapproved teacher
                if (role == "teacher" && selectedSchool != null) {
                    // Use the isFirstTeacher flag that was determined BEFORE registration
                    CoroutineScope(Dispatchers.IO).launch {
                        // Get the current user's document to check isApproved status
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                        if (currentUserId != null) {
                            val userDoc = firestore.collection("Users")
                                .document(currentUserId)
                                .get()
                                .await()
                            
                            val isApproved = userDoc.getBoolean("isApproved") ?: false
                            val isLeadTeacher = userDoc.getBoolean("isLeadTeacher") ?: false
                            
                            withContext(Dispatchers.Main) {
                                if (isApproved && isLeadTeacher) {
                                    // First teacher - auto-approved, can login
                                    Toast.makeText(this@RegisterActivity, "Registration Successful! You are the Lead Teacher.", Toast.LENGTH_LONG).show()
                                    startActivity(Intent(this@RegisterActivity, AdminMainActivity::class.java))
                                    finish()
                                } else {
                                    // Second+ teacher - needs approval, logout and show message
                                    Toast.makeText(
                                        this@RegisterActivity,
                                        "Registration Successful! Your account is pending approval from the Lead Teacher. You will be notified once approved.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    
                                    // Logout the user (they were auto-logged in during registration)
                                    FirebaseAuth.getInstance().signOut()
                                    
                                    // Navigate to login screen
                                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                                    finish()
                                }
                            }
                        }
                    }
                } else if (role == "student") {
                    // Students can login immediately
                    Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, StudentMainActivity::class.java))
                    finish()
                } else {
                    // Admin or other roles
                    Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, AdminMainActivity::class.java))
                    finish()
                }
            } else {
                Toast.makeText(
                    this,
                    result.second ?: "Registration Failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
