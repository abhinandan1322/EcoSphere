package com.example.capstone

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.capstone.databinding.ActivityAdminModuleManagementBinding
import com.example.capstone.ui.UiUtils
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore

class AdminModuleManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminModuleManagementBinding
    private lateinit var moduleAdapter: AdminModuleAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val allModules = mutableListOf<AdminModule>()   // master list
    private val modules = mutableListOf<AdminModule>()       // displayed list
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var currentSortBy = "order"
    private var currentSearch = ""
    private var currentUserSchoolId: String = ""
    private var isAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminModuleManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupClickListeners()
        loadUserProfile()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Module Management"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeColors(
            0xFF00897B.toInt(),
            0xFF4DB6AC.toInt(),
            0xFF00695C.toInt()
        )
        swipeRefreshLayout.setOnRefreshListener {
            android.util.Log.d("ModuleMgmt", "Swipe refresh triggered")
            loadAllModules()
        }
    }

    private fun setupRecyclerView() {
        moduleAdapter = AdminModuleAdapter(
            modules = modules,
            onEditClick = { module -> showEditModuleDialog(module) },
            onDeleteClick = { module -> showDeleteConfirmation(module) },
            onViewContentClick = { module -> showModuleContentDialog(module) },
            onManageQuestionsClick = { module -> navigateToQuizManagement(module) }
        )
        binding.recyclerViewModules.apply {
            layoutManager = LinearLayoutManager(this@AdminModuleManagementActivity)
            adapter = moduleAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddModule.setOnClickListener { showAddModuleDialog() }

        binding.btnSortByOrder.setOnClickListener { applySort("order") }
        binding.btnSortByPoints.setOnClickListener { applySort("points") }
        binding.btnSortByTitle.setOnClickListener { applySort("title") }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                currentSearch = s?.toString() ?: ""
                applySortAndSearch()
            }
        })
    }

    private fun loadUserProfile() {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        
        if (userId == null) {
            Snackbar.make(binding.root, "❌ User not authenticated", Snackbar.LENGTH_LONG).show()
            return
        }
        
        firestore.collection("Users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                currentUserSchoolId = document.getString("schoolId") ?: ""
                isAdmin = document.getString("role") == "admin"
                
                if (currentUserSchoolId.isEmpty() && !isAdmin) {
                    Snackbar.make(
                        binding.root,
                        "❌ Your account is not associated with a school. Please contact your administrator.",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }
                
                android.util.Log.d("ModuleMgmt", "User profile loaded: schoolId=$currentUserSchoolId, isAdmin=$isAdmin")
                loadAllModules()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ModuleMgmt", "Failed to load user profile", e)
                Snackbar.make(
                    binding.root,
                    "❌ Failed to load user profile: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).setAction("Retry") { loadUserProfile() }
                .show()
            }
    }

    private fun applySort(sortBy: String) {
        currentSortBy = sortBy
        applySortAndSearch()
        updateSortButtonStates()
    }

    private fun applySortAndSearch() {
        var filtered = if (currentSearch.isEmpty()) {
            allModules.toList()
        } else {
            allModules.filter { m ->
                m.title.contains(currentSearch, ignoreCase = true) ||
                m.description.contains(currentSearch, ignoreCase = true)
            }
        }

        filtered = when (currentSortBy) {
            "points" -> filtered.sortedByDescending { it.points }
            "title" -> filtered.sortedBy { it.title }
            else -> filtered.sortedBy { it.order }
        }

        modules.clear()
        modules.addAll(filtered)
        moduleAdapter.notifyDataSetChanged()
        updateStats()
        updateEmptyState(filtered.isEmpty())
    }

    private fun updateSortButtonStates() {
        val activeColor = 0xFF00897B.toInt()
        val defaultColor = 0xFF666666.toInt()

        binding.btnSortByOrder.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (currentSortBy == "order") activeColor else defaultColor)
        binding.btnSortByPoints.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (currentSortBy == "points") activeColor else defaultColor)
        binding.btnSortByTitle.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (currentSortBy == "title") activeColor else defaultColor)
    }

    private fun loadAllModules() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewModules.visibility = View.GONE
        binding.tvEmptyState.visibility = View.GONE
        binding.tvTotalModules.text = "Loading..."

        android.util.Log.d("ModuleMgmt", "Loading modules from Firestore...")

        // Build query based on user role
        val query = if (isAdmin) {
            // Admins see all modules
            android.util.Log.d("ModuleMgmt", "Loading all modules (admin)")
            firestore.collection("Modules")
        } else {
            // Teachers see only their school's modules
            android.util.Log.d("ModuleMgmt", "Loading modules for school: $currentUserSchoolId")
            firestore.collection("Modules")
                .whereEqualTo("schoolId", currentUserSchoolId)
        }

        query.get()
            .addOnSuccessListener { documents ->
                android.util.Log.d("ModuleMgmt", "✅ Loaded ${documents.size()} modules")
                swipeRefreshLayout.isRefreshing = false
                binding.progressBar.visibility = View.GONE

                allModules.clear()
                for (document in documents) {
                    try {
                        val id = document.getString("id")?.takeIf { it.isNotEmpty() } ?: document.id
                        val title = document.getString("title") ?: ""
                        if (title.isEmpty()) {
                            android.util.Log.w("ModuleMgmt", "Skipping module ${document.id}: missing title")
                            continue
                        }
                        val module = AdminModule(
                            id = id,
                            title = title,
                            description = document.getString("description") ?: "",
                            content = document.getString("content") ?: "",
                            points = document.getLong("points") ?: 0,
                            order = document.getLong("order")?.toInt() ?: 0,
                            schoolId = document.getString("schoolId") ?: ""
                        )
                        allModules.add(module)
                    } catch (e: Exception) {
                        android.util.Log.e("ModuleMgmt", "Error parsing module ${document.id}", e)
                    }
                }

                applySortAndSearch()
                updateSortButtonStates()
                loadQuestionCounts()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ModuleMgmt", "❌ Failed to load modules", e)
                swipeRefreshLayout.isRefreshing = false
                binding.progressBar.visibility = View.GONE
                binding.tvTotalModules.text = "Failed to load"

                val msg = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> "Permission denied. Check Firestore rules."
                    e.message?.contains("network") == true || e.message?.contains("UNAVAILABLE") == true -> "Network error. Check your connection."
                    else -> "Failed to load modules: ${e.message}"
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                    .setAction("Retry") { loadAllModules() }
                    .show()
            }
    }

    private fun loadQuestionCounts() {
        // Load question counts for each module and update allModules
        allModules.forEachIndexed { index, module ->
            firestore.collection("Modules")
                .document(module.id)
                .collection("questions")
                .get()
                .addOnSuccessListener { questions ->
                    // Update in allModules (master list)
                    allModules[index] = module.copy(questionCount = questions.size())
                    // Update in displayed modules list too
                    val displayIndex = modules.indexOfFirst { it.id == module.id }
                    if (displayIndex != -1) {
                        modules[displayIndex] = allModules[index]
                        moduleAdapter.notifyItemChanged(displayIndex)
                    }
                    // Update stats after each count loads
                    updateStats()
                }
                .addOnFailureListener { e ->
                    android.util.Log.w("ModuleMgmt", "Failed to load question count for ${module.id}", e)
                }
        }
    }

    private fun updateStats() {
        val total = allModules.size
        val totalPoints = allModules.sumOf { it.points }
        val totalQuestions = allModules.sumOf { it.questionCount }

        binding.tvTotalModules.text = if (currentSearch.isNotEmpty()) {
            "Showing ${modules.size} of $total"
        } else {
            "Modules: $total"
        }
        binding.tvTotalPoints.text = "Points: $totalPoints"
        binding.tvTotalQuestions.text = "Questions: $totalQuestions"
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.recyclerViewModules.visibility = View.GONE
            binding.tvEmptyStateMessage.text = if (currentSearch.isNotEmpty()) {
                "No modules found for \"$currentSearch\""
            } else {
                "No modules yet\nTap + to create your first module"
            }
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.recyclerViewModules.visibility = View.VISIBLE
        }
    }

    private fun showAddModuleDialog() {
        showModuleDetailsDialog(null)
    }

    private fun showEditModuleDialog(module: AdminModule) {
        showModuleDetailsDialog(module)
    }

    private fun showModuleDetailsDialog(existingModule: AdminModule?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_module, null)

        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDescription)
        val etContent = dialogView.findViewById<EditText>(R.id.etContent)
        val etPoints = dialogView.findViewById<EditText>(R.id.etPoints)
        val etOrder = dialogView.findViewById<EditText>(R.id.etOrder)

        existingModule?.let { m ->
            etTitle.setText(m.title)
            etDescription.setText(m.description)
            etContent.setText(m.content)
            etPoints.setText(m.points.toString())
            etOrder.setText(m.order.toString())
        } ?: run {
            etPoints.setText("20")
            etOrder.setText((allModules.size + 1).toString())
        }

        val dialogTitle = if (existingModule == null) "Add New Module" else "Edit Module"

        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val content = etContent.text.toString().trim()
            val pointsText = etPoints.text.toString().trim()
            val orderText = etOrder.text.toString().trim()

            if (validateModuleData(etTitle, etDescription, etContent, etPoints, etOrder,
                    title, description, content, pointsText, orderText)) {
                // Disable buttons during save
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "Saving..."

                val points = pointsText.toLong()
                val order = orderText.toInt()

                if (existingModule == null) {
                    addNewModule(title, description, content, points, order) { dialog.dismiss() }
                } else {
                    updateModule(existingModule, title, description, content, points, order) { dialog.dismiss() }
                }
            }
        }
    }

    private fun validateModuleData(
        etTitle: EditText, etDescription: EditText, etContent: EditText,
        etPoints: EditText, etOrder: EditText,
        title: String, description: String, content: String,
        pointsText: String, orderText: String
    ): Boolean {
        etTitle.error = null
        etDescription.error = null
        etContent.error = null
        etPoints.error = null
        etOrder.error = null

        return when {
            title.isEmpty() -> {
                etTitle.error = "Title is required"
                etTitle.requestFocus(); false
            }
            title.length < 3 -> {
                etTitle.error = "Title must be at least 3 characters"
                etTitle.requestFocus(); false
            }
            description.isEmpty() -> {
                etDescription.error = "Description is required"
                etDescription.requestFocus(); false
            }
            description.length < 10 -> {
                etDescription.error = "Description must be at least 10 characters"
                etDescription.requestFocus(); false
            }
            content.isEmpty() -> {
                etContent.error = "Content is required"
                etContent.requestFocus(); false
            }
            content.length < 50 -> {
                etContent.error = "Content must be at least 50 characters"
                etContent.requestFocus(); false
            }
            pointsText.isEmpty() -> {
                etPoints.error = "Points is required"
                etPoints.requestFocus(); false
            }
            orderText.isEmpty() -> {
                etOrder.error = "Order is required"
                etOrder.requestFocus(); false
            }
            else -> {
                val points = pointsText.toLongOrNull()
                val order = orderText.toIntOrNull()
                when {
                    points == null -> {
                        etPoints.error = "Must be a valid number"
                        etPoints.requestFocus(); false
                    }
                    points < 1 || points > 100 -> {
                        etPoints.error = "Points must be between 1 and 100"
                        etPoints.requestFocus(); false
                    }
                    order == null -> {
                        etOrder.error = "Must be a valid number"
                        etOrder.requestFocus(); false
                    }
                    order < 1 || order > 20 -> {
                        etOrder.error = "Order must be between 1 and 20"
                        etOrder.requestFocus(); false
                    }
                    else -> true
                }
            }
        }
    }

    private fun addNewModule(
        title: String, description: String, content: String,
        points: Long, order: Int, onComplete: () -> Unit
    ) {
        // Validate schoolId
        if (currentUserSchoolId.isEmpty() && !isAdmin) {
            Snackbar.make(
                binding.root,
                "❌ Cannot create module: No school associated with your account",
                Snackbar.LENGTH_LONG
            ).show()
            onComplete()
            return
        }
        
        // Use Firestore auto-generated ID to avoid collisions
        val newDocRef = firestore.collection("Modules").document()

        val moduleData = hashMapOf(
            "id" to newDocRef.id,
            "title" to title,
            "description" to description,
            "content" to content,
            "points" to points,
            "order" to order.toLong(),
            "schoolId" to currentUserSchoolId
        )

        android.util.Log.d("ModuleMgmt", "Adding new module: $title (schoolId=$currentUserSchoolId)")

        newDocRef.set(moduleData)
            .addOnSuccessListener {
                android.util.Log.d("ModuleMgmt", "✅ Module added: ${newDocRef.id}")
                Snackbar.make(binding.root, "✅ Module added successfully", Snackbar.LENGTH_SHORT).show()
                onComplete()
                loadAllModules()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ModuleMgmt", "❌ Failed to add module", e)
                Snackbar.make(binding.root, "❌ Failed to add module: ${e.message}", Snackbar.LENGTH_LONG).show()
                onComplete()
            }
    }

    private fun updateModule(
        existingModule: AdminModule,
        title: String, description: String, content: String,
        points: Long, order: Int, onComplete: () -> Unit
    ) {
        val moduleData = hashMapOf(
            "id" to existingModule.id,
            "title" to title,
            "description" to description,
            "content" to content,
            "points" to points,
            "order" to order.toLong(),
            "schoolId" to existingModule.schoolId
        )

        android.util.Log.d("ModuleMgmt", "Updating module: ${existingModule.id}")

        firestore.collection("Modules")
            .document(existingModule.id)
            .set(moduleData)
            .addOnSuccessListener {
                android.util.Log.d("ModuleMgmt", "✅ Module updated: ${existingModule.id}")
                Snackbar.make(binding.root, "✅ Module updated successfully", Snackbar.LENGTH_SHORT).show()
                onComplete()
                loadAllModules()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ModuleMgmt", "❌ Failed to update module", e)
                Snackbar.make(binding.root, "❌ Failed to update module: ${e.message}", Snackbar.LENGTH_LONG).show()
                onComplete()
            }
    }

    private fun showDeleteConfirmation(module: AdminModule) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Delete Module")
            .setMessage("Are you sure you want to delete:\n\n\"${module.title}\"\n\nThis will:\n• Delete all ${module.questionCount} quiz questions\n• Remove completion records for all students\n• Reverse EcoPoints earned by students\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteModule(module) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteModule(module: AdminModule) {
        android.util.Log.d("ModuleMgmt", "Deleting module: ${module.id}")

        // Step 1: Find all students who completed this module
        firestore.collection("Users")
            .whereEqualTo("schoolId", currentUserSchoolId)
            .whereEqualTo("role", "student")
            .get()
            .addOnSuccessListener { students ->
                val studentIds = students.documents.map { it.id }
                android.util.Log.d("ModuleMgmt", "Found ${studentIds.size} students to check for completions")

                // Step 2: For each student, check if they completed this module and reverse points
                var pendingOps = studentIds.size + 1 // +1 for the module+questions delete
                var completedOps = 0

                fun onOpComplete() {
                    completedOps++
                    if (completedOps >= pendingOps) {
                        // All done — remove from UI
                        allModules.removeAll { it.id == module.id }
                        applySortAndSearch()
                        Snackbar.make(
                            binding.root,
                            "✅ Module deleted and student records updated",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }

                // Step 3: Delete module + questions
                firestore.collection("Modules")
                    .document(module.id)
                    .collection("questions")
                    .get()
                    .addOnSuccessListener { questions ->
                        val batch = firestore.batch()
                        for (question in questions) {
                            batch.delete(question.reference)
                        }
                        batch.delete(firestore.collection("Modules").document(module.id))
                        batch.commit()
                            .addOnSuccessListener {
                                android.util.Log.d("ModuleMgmt", "✅ Module and ${questions.size()} questions deleted")
                                onOpComplete()
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("ModuleMgmt", "❌ Failed to delete module", e)
                                Snackbar.make(binding.root, "❌ Failed to delete module: ${e.message}", Snackbar.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Snackbar.make(binding.root, "❌ Failed to delete module: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }

                // Step 4: For each student, reverse points and delete completion + quiz attempt
                if (studentIds.isEmpty()) {
                    onOpComplete() // no students, count the pending op as done
                } else {
                    for (studentId in studentIds) {
                        val completionRef = firestore.collection("Users")
                            .document(studentId)
                            .collection("completions")
                            .document(module.id)

                        completionRef.get()
                            .addOnSuccessListener { completionDoc ->
                                if (completionDoc.exists()) {
                                    val pointsAwarded = completionDoc.getLong("points") ?: module.points
                                    android.util.Log.d("ModuleMgmt", "Reversing $pointsAwarded pts for student $studentId")

                                    val studentBatch = firestore.batch()

                                    // Remove completion record
                                    studentBatch.delete(completionRef)

                                    // Remove quiz attempt record
                                    val quizAttemptRef = firestore.collection("Users")
                                        .document(studentId)
                                        .collection("quizAttempts")
                                        .document(module.id)
                                    studentBatch.delete(quizAttemptRef)

                                    // Deduct EcoPoints
                                    val userRef = firestore.collection("Users").document(studentId)
                                    studentBatch.update(userRef, "ecoPoints",
                                        com.google.firebase.firestore.FieldValue.increment(-pointsAwarded))

                                    studentBatch.commit()
                                        .addOnSuccessListener {
                                            android.util.Log.d("ModuleMgmt", "✅ Reversed points for student $studentId")
                                            onOpComplete()
                                        }
                                        .addOnFailureListener { e ->
                                            android.util.Log.e("ModuleMgmt", "❌ Failed to reverse points for $studentId", e)
                                            onOpComplete() // continue even if one fails
                                        }
                                } else {
                                    // Student didn't complete this module — nothing to reverse
                                    onOpComplete()
                                }
                            }
                            .addOnFailureListener {
                                onOpComplete() // continue even if one fails
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ModuleMgmt", "❌ Failed to load students for cleanup", e)
                // Fallback: delete module without reversing points
                firestore.collection("Modules").document(module.id).delete()
                    .addOnSuccessListener {
                        allModules.removeAll { it.id == module.id }
                        applySortAndSearch()
                        Snackbar.make(binding.root, "✅ Module deleted (student records not updated)", Snackbar.LENGTH_SHORT).show()
                    }
            }
    }

    private fun showModuleContentDialog(module: AdminModule) {
        AlertDialog.Builder(this)
            .setTitle("📖 ${module.title}")
            .setMessage(module.content.ifEmpty { "No content available" })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun navigateToQuizManagement(module: AdminModule) {
        // Navigate to Quiz Management with the specific moduleId pre-selected
        val intent = Intent(this, AdminQuizManagementActivity::class.java)
        intent.putExtra("moduleId", module.id)
        intent.putExtra("moduleTitle", module.title)
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

data class AdminModule(
    val id: String,
    val title: String,
    val description: String,
    val content: String,
    val points: Long,
    val order: Int,
    val schoolId: String = "",
    val questionCount: Int = 0
)
