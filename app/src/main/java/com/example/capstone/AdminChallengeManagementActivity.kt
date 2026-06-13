package com.example.capstone

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.capstone.databinding.ActivityAdminChallengeManagementBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore

class AdminChallengeManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminChallengeManagementBinding
    private lateinit var challengeAdapter: AdminChallengeAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val allChallenges = mutableListOf<AdminChallenge>()   // master list
    private val challenges = mutableListOf<AdminChallenge>()       // displayed list
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var currentFilter = "all"
    private var currentSearch = ""
    private var currentUserSchoolId: String = ""
    private var isAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminChallengeManagementBinding.inflate(layoutInflater)
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
            title = "Challenge Management"
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
            android.util.Log.d("ChallengeMgmt", "Swipe refresh triggered")
            loadAllChallenges()
        }
    }

    private fun setupRecyclerView() {
        challengeAdapter = AdminChallengeAdapter(
            challenges = challenges,
            onEditClick = { challenge -> showEditChallengeDialog(challenge) },
            onDeleteClick = { challenge -> showDeleteConfirmation(challenge) },
            onToggleActive = { challenge -> toggleChallengeStatus(challenge) }
        )
        binding.recyclerViewChallenges.apply {
            layoutManager = LinearLayoutManager(this@AdminChallengeManagementActivity)
            adapter = challengeAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddChallenge.setOnClickListener {
            showAddChallengeDialog()
        }

        // Filter buttons
        binding.btnFilterAll.setOnClickListener { applyFilter("all") }
        binding.btnFilterActive.setOnClickListener { applyFilter("active") }
        binding.btnFilterInactive.setOnClickListener { applyFilter("inactive") }

        // Search
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                currentSearch = s?.toString() ?: ""
                applyFilterAndSearch()
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
                
                android.util.Log.d("ChallengeMgmt", "User profile loaded: schoolId=$currentUserSchoolId, isAdmin=$isAdmin")
                loadAllChallenges()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChallengeMgmt", "Failed to load user profile", e)
                Snackbar.make(
                    binding.root,
                    "❌ Failed to load user profile: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).setAction("Retry") { loadUserProfile() }
                .show()
            }
    }

    private fun applyFilter(filter: String) {
        currentFilter = filter
        applyFilterAndSearch()
        updateFilterButtonStates()
    }

    private fun applyFilterAndSearch() {
        var filtered = when (currentFilter) {
            "active" -> allChallenges.filter { it.active }
            "inactive" -> allChallenges.filter { !it.active }
            else -> allChallenges.toList()
        }

        if (currentSearch.isNotEmpty()) {
            filtered = filtered.filter { c ->
                c.title.contains(currentSearch, ignoreCase = true) ||
                c.description.contains(currentSearch, ignoreCase = true)
            }
        }

        challenges.clear()
        challenges.addAll(filtered)
        challengeAdapter.notifyDataSetChanged()

        updateStats()
        updateEmptyState(filtered.isEmpty())
    }

    private fun updateFilterButtonStates() {
        val activeColor = 0xFF00897B.toInt()
        val inactiveColor = 0xFF666666.toInt()

        binding.btnFilterAll.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (currentFilter == "all") activeColor else inactiveColor)
        binding.btnFilterActive.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (currentFilter == "active") 0xFF4CAF50.toInt() else inactiveColor)
        binding.btnFilterInactive.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (currentFilter == "inactive") 0xFFFF5722.toInt() else inactiveColor)
    }

    private fun loadAllChallenges() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
        binding.recyclerViewChallenges.visibility = View.GONE
        binding.tvTotalChallenges.text = "Loading..."

        android.util.Log.d("ChallengeMgmt", "Loading challenges from Firestore...")

        // Build query based on user role
        val query = if (isAdmin) {
            // Admins see all challenges
            android.util.Log.d("ChallengeMgmt", "Loading all challenges (admin)")
            firestore.collection("Challenges")
        } else {
            // Teachers see only their school's challenges
            android.util.Log.d("ChallengeMgmt", "Loading challenges for school: $currentUserSchoolId")
            firestore.collection("Challenges")
                .whereEqualTo("schoolId", currentUserSchoolId)
        }

        query.get()
            .addOnSuccessListener { documents ->
                android.util.Log.d("ChallengeMgmt", "✅ Loaded ${documents.size()} challenges")
                swipeRefreshLayout.isRefreshing = false
                binding.progressBar.visibility = View.GONE

                allChallenges.clear()
                for (document in documents) {
                    try {
                        val id = document.getString("id")?.takeIf { it.isNotEmpty() } ?: document.id
                        val title = document.getString("title") ?: ""
                        val description = document.getString("description") ?: ""

                        if (title.isEmpty()) {
                            android.util.Log.w("ChallengeMgmt", "Skipping challenge ${document.id}: missing title")
                            continue
                        }

                        val challenge = AdminChallenge(
                            id = id,
                            title = title,
                            description = description,
                            points = document.getLong("points") ?: 0,
                            type = document.getString("type") ?: "one-time",
                            active = document.getBoolean("active") ?: true,
                            schoolId = document.getString("schoolId") ?: ""
                        )
                        allChallenges.add(challenge)
                    } catch (e: Exception) {
                        android.util.Log.e("ChallengeMgmt", "Error parsing challenge ${document.id}", e)
                    }
                }

                applyFilterAndSearch()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChallengeMgmt", "❌ Failed to load challenges", e)
                swipeRefreshLayout.isRefreshing = false
                binding.progressBar.visibility = View.GONE
                binding.tvTotalChallenges.text = "Failed to load"

                val msg = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> "Permission denied. Check Firestore rules."
                    e.message?.contains("network") == true || e.message?.contains("UNAVAILABLE") == true -> "Network error. Check your connection."
                    else -> "Failed to load challenges: ${e.message}"
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                    .setAction("Retry") { loadAllChallenges() }
                    .show()
            }
    }

    private fun updateStats() {
        val total = allChallenges.size
        val active = allChallenges.count { it.active }
        val inactive = total - active
        val totalPoints = allChallenges.sumOf { it.points }

        val displayCount = if (currentSearch.isNotEmpty() || currentFilter != "all") {
            "Showing ${challenges.size} of $total"
        } else {
            "Total: $total"
        }

        binding.tvTotalChallenges.text = displayCount
        binding.tvActiveChallenges.text = "Active: $active"
        binding.tvInactiveChallenges.text = "Inactive: $inactive"
        binding.tvTotalPoints.text = "Total Points: $totalPoints"
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.recyclerViewChallenges.visibility = View.GONE
            binding.tvEmptyStateMessage.text = when {
                currentSearch.isNotEmpty() -> "No challenges found for \"$currentSearch\""
                currentFilter == "active" -> "No active challenges"
                currentFilter == "inactive" -> "No inactive challenges"
                else -> "No challenges yet\nTap + to create your first challenge"
            }
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.recyclerViewChallenges.visibility = View.VISIBLE
        }
    }

    private fun showAddChallengeDialog() {
        showChallengeDetailsDialog(null)
    }

    private fun showEditChallengeDialog(challenge: AdminChallenge) {
        showChallengeDetailsDialog(challenge)
    }

    private fun showChallengeDetailsDialog(existingChallenge: AdminChallenge?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_challenge, null)

        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDescription)
        val etPoints = dialogView.findViewById<EditText>(R.id.etPoints)
        val switchActive = dialogView.findViewById<android.widget.Switch>(R.id.switchActive)

        existingChallenge?.let { c ->
            etTitle.setText(c.title)
            etDescription.setText(c.description)
            etPoints.setText(c.points.toString())
            switchActive.isChecked = c.active
        } ?: run {
            etPoints.setText("15")
            switchActive.isChecked = true
        }

        val dialogTitle = if (existingChallenge == null) "Add New Challenge" else "Edit Challenge"

        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton("Save", null) // null to prevent auto-dismiss
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Override positive button to prevent auto-dismiss on validation failure
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val pointsText = etPoints.text.toString().trim()
            val active = switchActive.isChecked

            if (validateChallengeData(etTitle, etDescription, etPoints, title, description, pointsText)) {
                dialog.dismiss()
                val points = pointsText.toLong()
                if (existingChallenge == null) {
                    addNewChallenge(title, description, points, active)
                } else {
                    updateChallenge(existingChallenge, title, description, points, active)
                }
            }
        }
    }

    private fun validateChallengeData(
        etTitle: EditText, etDescription: EditText, etPoints: EditText,
        title: String, description: String, pointsText: String
    ): Boolean {
        etTitle.error = null
        etDescription.error = null
        etPoints.error = null

        return when {
            title.isEmpty() -> {
                etTitle.error = "Title is required"
                etTitle.requestFocus()
                false
            }
            title.length < 3 -> {
                etTitle.error = "Title must be at least 3 characters"
                etTitle.requestFocus()
                false
            }
            description.isEmpty() -> {
                etDescription.error = "Description is required"
                etDescription.requestFocus()
                false
            }
            description.length < 10 -> {
                etDescription.error = "Description must be at least 10 characters"
                etDescription.requestFocus()
                false
            }
            pointsText.isEmpty() -> {
                etPoints.error = "Points is required"
                etPoints.requestFocus()
                false
            }
            else -> {
                val points = pointsText.toLongOrNull()
                when {
                    points == null -> {
                        etPoints.error = "Must be a valid number"
                        etPoints.requestFocus()
                        false
                    }
                    points < 1 || points > 100 -> {
                        etPoints.error = "Points must be between 1 and 100"
                        etPoints.requestFocus()
                        false
                    }
                    else -> true
                }
            }
        }
    }

    private fun addNewChallenge(title: String, description: String, points: Long, active: Boolean) {
        // Validate schoolId
        if (currentUserSchoolId.isEmpty() && !isAdmin) {
            Snackbar.make(
                binding.root,
                "❌ Cannot create challenge: No school associated with your account",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        
        // Use Firestore auto-generated ID to avoid collisions
        val newDocRef = firestore.collection("Challenges").document()

        val challengeData = hashMapOf(
            "id" to newDocRef.id,
            "title" to title,
            "description" to description,
            "points" to points,
            "type" to "one-time",
            "active" to active,
            "schoolId" to currentUserSchoolId
        )

        android.util.Log.d("ChallengeMgmt", "Adding new challenge: $title (schoolId=$currentUserSchoolId)")

        newDocRef.set(challengeData)
            .addOnSuccessListener {
                android.util.Log.d("ChallengeMgmt", "✅ Challenge added: ${newDocRef.id}")
                Snackbar.make(binding.root, "✅ Challenge added successfully", Snackbar.LENGTH_SHORT).show()
                loadAllChallenges()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChallengeMgmt", "❌ Failed to add challenge", e)
                Snackbar.make(binding.root, "❌ Failed to add challenge: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    private fun updateChallenge(
        existingChallenge: AdminChallenge,
        title: String,
        description: String,
        points: Long,
        active: Boolean
    ) {
        val challengeData = hashMapOf(
            "id" to existingChallenge.id,
            "title" to title,
            "description" to description,
            "points" to points,
            "type" to existingChallenge.type,
            "active" to active,
            "schoolId" to existingChallenge.schoolId
        )

        android.util.Log.d("ChallengeMgmt", "Updating challenge: ${existingChallenge.id}")

        firestore.collection("Challenges")
            .document(existingChallenge.id)
            .set(challengeData)
            .addOnSuccessListener {
                android.util.Log.d("ChallengeMgmt", "✅ Challenge updated: ${existingChallenge.id}")
                Snackbar.make(binding.root, "✅ Challenge updated successfully", Snackbar.LENGTH_SHORT).show()
                loadAllChallenges()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChallengeMgmt", "❌ Failed to update challenge", e)
                Snackbar.make(binding.root, "❌ Failed to update challenge: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    private fun toggleChallengeStatus(challenge: AdminChallenge) {
        val newStatus = !challenge.active
        android.util.Log.d("ChallengeMgmt", "Toggling challenge ${challenge.id} to active=$newStatus")

        firestore.collection("Challenges")
            .document(challenge.id)
            .update("active", newStatus)
            .addOnSuccessListener {
                android.util.Log.d("ChallengeMgmt", "✅ Challenge status toggled")
                val statusText = if (newStatus) "activated ✅" else "deactivated ⛔"
                Snackbar.make(binding.root, "Challenge $statusText", Snackbar.LENGTH_SHORT).show()
                // Update locally instead of full reload
                val index = allChallenges.indexOfFirst { it.id == challenge.id }
                if (index != -1) {
                    allChallenges[index] = challenge.copy(active = newStatus)
                    applyFilterAndSearch()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChallengeMgmt", "❌ Failed to toggle status", e)
                Snackbar.make(binding.root, "❌ Failed to update status: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    private fun showDeleteConfirmation(challenge: AdminChallenge) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Delete Challenge")
            .setMessage("Are you sure you want to delete:\n\n\"${challenge.title}\"\n\nThis will:\n• Remove all student submissions\n• Reverse EcoPoints earned by approved students\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteChallenge(challenge) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteChallenge(challenge: AdminChallenge) {
        android.util.Log.d("ChallengeMgmt", "Deleting challenge: ${challenge.id}")

        // Step 1: Find all APPROVED submissions for this challenge to reverse points
        firestore.collection("ChallengeSubmissions")
            .whereEqualTo("challengeId", challenge.id)
            .get()
            .addOnSuccessListener { submissions ->
                android.util.Log.d("ChallengeMgmt", "Found ${submissions.size()} submissions to clean up")

                val approvedSubmissions = submissions.documents.filter {
                    it.getString("status") == "approved"
                }

                android.util.Log.d("ChallengeMgmt", "${approvedSubmissions.size} were approved — reversing points")

                // Step 2: Delete all submissions
                val deleteBatch = firestore.batch()
                for (doc in submissions) {
                    deleteBatch.delete(doc.reference)
                }

                // Step 3: Reverse EcoPoints for approved submissions
                for (submission in approvedSubmissions) {
                    val studentId = submission.getString("studentId") ?: continue
                    val pointsAwarded = submission.getLong("challengePoints") ?: challenge.points
                    val userRef = firestore.collection("Users").document(studentId)
                    deleteBatch.update(userRef, "ecoPoints",
                        com.google.firebase.firestore.FieldValue.increment(-pointsAwarded))
                    android.util.Log.d("ChallengeMgmt", "Reversing $pointsAwarded pts for student $studentId")
                }

                // Step 4: Delete the challenge document itself
                deleteBatch.delete(firestore.collection("Challenges").document(challenge.id))

                deleteBatch.commit()
                    .addOnSuccessListener {
                        android.util.Log.d("ChallengeMgmt", "✅ Challenge deleted, ${approvedSubmissions.size} students' points reversed")
                        allChallenges.removeAll { it.id == challenge.id }
                        applyFilterAndSearch()
                        val msg = if (approvedSubmissions.isNotEmpty()) {
                            "✅ Challenge deleted — ${approvedSubmissions.size} students' points reversed"
                        } else {
                            "✅ Challenge deleted successfully"
                        }
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("ChallengeMgmt", "❌ Failed to delete challenge", e)
                        Snackbar.make(binding.root, "❌ Failed to delete challenge: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ChallengeMgmt", "❌ Failed to load submissions for cleanup", e)
                // Fallback: delete challenge without reversing points
                firestore.collection("Challenges").document(challenge.id).delete()
                    .addOnSuccessListener {
                        allChallenges.removeAll { it.id == challenge.id }
                        applyFilterAndSearch()
                        Snackbar.make(binding.root, "✅ Challenge deleted (points not reversed)", Snackbar.LENGTH_SHORT).show()
                    }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

data class AdminChallenge(
    val id: String,
    val title: String,
    val description: String,
    val points: Long,
    val type: String,
    val active: Boolean,
    val schoolId: String = ""
)
