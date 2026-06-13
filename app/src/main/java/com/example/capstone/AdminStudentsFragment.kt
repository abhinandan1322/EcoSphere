package com.example.capstone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.capstone.databinding.FragmentAdminStudentsBinding
import com.example.capstone.ui.UiUtils
import com.google.firebase.firestore.FirebaseFirestore

class AdminStudentsFragment : Fragment() {

    private var _binding: FragmentAdminStudentsBinding? = null
    private val binding get() = _binding!!
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var studentsAdapter: AdminStudentsAdapter
    private val studentsList = mutableListOf<StudentData>()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    
    // NEW: Multi-tenancy fields
    private var teacherSchoolId: String? = null
    private var assignedBatches: List<String> = emptyList()
    private var currentUserId: String? = null
    private var hasLoadedData = false
    private var isLeadTeacher: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        android.util.Log.d("AdminStudents", "🎨 onCreateView called")
        _binding = FragmentAdminStudentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        android.util.Log.d("AdminStudents", "🚀 onViewCreated called")

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        
        android.util.Log.d("AdminStudents", "Current user ID: $currentUserId")

        setupSwipeRefresh()
        setupRecyclerView()
        setupSearchView()
        loadTeacherInfo()
    }
    
    /**
     * NEW: Load teacher information to get assigned batches
     */
    private fun loadTeacherInfo() {
        android.util.Log.d("AdminStudents", "📋 loadTeacherInfo called, userId: $currentUserId")
        
        if (currentUserId == null) {
            android.util.Log.w("AdminStudents", "⚠️ No current user, loading all students")
            loadStudents()
            return
        }
        
        android.util.Log.d("AdminStudents", "🔍 Fetching teacher info from Firestore...")
        firestore.collection("Users")
            .document(currentUserId!!)
            .get()
            .addOnSuccessListener { document ->
                android.util.Log.d("AdminStudents", "✅ Teacher document fetched, exists: ${document.exists()}")
                
                if (document.exists()) {
                    teacherSchoolId = document.getString("schoolId")
                    isLeadTeacher = document.getBoolean("isLeadTeacher") ?: false
                    val batchesArray = document.get("assignedBatches") as? List<*>
                    assignedBatches = batchesArray?.mapNotNull { it as? String } ?: emptyList()
                    
                    android.util.Log.d("AdminStudents", "🏫 Teacher school: $teacherSchoolId")
                    android.util.Log.d("AdminStudents", "👑 Is Lead Teacher: $isLeadTeacher")
                    android.util.Log.d("AdminStudents", "📚 Assigned batches: $assignedBatches")
                } else {
                    android.util.Log.w("AdminStudents", "⚠️ Teacher document doesn't exist")
                }
                loadStudents()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AdminStudents", "❌ Failed to load teacher info: ${e.message}", e)
                loadStudents()
            }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeColors(
            0xFF00897B.toInt(),
            0xFF4DB6AC.toInt(),
            0xFF00695C.toInt()
        )
        swipeRefreshLayout.setOnRefreshListener {
            android.util.Log.d("AdminStudents", "Swipe refresh triggered")
            // Clear search before refreshing
            binding.etSearch.setQuery("", false)
            loadStudents()
        }
    }

    private fun setupRecyclerView() {
        android.util.Log.d("AdminStudents", "🎯 Setting up RecyclerView")
        
        studentsAdapter = AdminStudentsAdapter(
            students = studentsList,
            onStudentClick = { student -> showStudentDetails(student) },
            onDeleteClick = { student -> showDeleteConfirmation(student) }
        )
        binding.recyclerViewStudents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = studentsAdapter
            // Ensure RecyclerView is properly measured
            setHasFixedSize(false)
        }
        
        android.util.Log.d("AdminStudents", "✅ RecyclerView setup complete")
    }

    private fun setupSearchView() {
        binding.etSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterStudents(newText ?: "")
                return true
            }
        })
    }

    private fun loadStudents() {
        android.util.Log.d("AdminStudents", "🔄 loadStudents called")
        
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
        binding.recyclerViewStudents.visibility = View.GONE
        binding.tvStudentCount.text = "Loading students..."

        android.util.Log.d("AdminStudents", "📊 Loading students from Firestore...")
        android.util.Log.d("AdminStudents", "   teacherSchoolId: $teacherSchoolId")
        android.util.Log.d("AdminStudents", "   isLeadTeacher: $isLeadTeacher")
        android.util.Log.d("AdminStudents", "   assignedBatches: $assignedBatches")

        // NEW: Filter logic based on teacher type
        when {
            // Lead Teacher: Show ALL students from their school
            isLeadTeacher && teacherSchoolId != null -> {
                android.util.Log.d("AdminStudents", "👑 Lead Teacher mode: Loading ALL students from school")
                loadStudentsBySchool()
            }
            // Normal Teacher with batches: Show only students from assigned batches
            !isLeadTeacher && teacherSchoolId != null && assignedBatches.isNotEmpty() -> {
                android.util.Log.d("AdminStudents", "👨‍🏫 Normal Teacher mode: Loading students from assigned batches")
                loadStudentsByBatches()
            }
            // Normal Teacher without batches: Show all students from their school (fallback)
            !isLeadTeacher && teacherSchoolId != null -> {
                android.util.Log.d("AdminStudents", "👨‍🏫 Normal Teacher (no batches): Loading all students from school")
                loadStudentsBySchool()
            }
            // Admin or no school: Show all students (backward compatibility)
            else -> {
                android.util.Log.d("AdminStudents", "👑 Admin mode: Loading ALL students")
                loadAllStudents()
            }
        }
    }
    
    /**
     * NEW: Load students from teacher's assigned batches
     */
    private fun loadStudentsByBatches() {
        android.util.Log.d("AdminStudents", "🎯 Querying students by batches: $assignedBatches")
        
        firestore.collection("Users")
            .whereEqualTo("role", "student")
            .whereIn("batchId", assignedBatches.take(10)) // Firestore limit: 10 items in whereIn
            .get()
            .addOnSuccessListener { documents ->
                android.util.Log.d("AdminStudents", "✅ Query successful, documents: ${documents.size()}")
                processStudentDocuments(documents)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AdminStudents", "❌ Query failed: ${e.message}", e)
                handleLoadError(e)
            }
    }
    
    /**
     * NEW: Load students from teacher's school
     */
    private fun loadStudentsBySchool() {
        android.util.Log.d("AdminStudents", "🔍 Querying students by schoolId: $teacherSchoolId")
        
        firestore.collection("Users")
            .whereEqualTo("role", "student")
            .whereEqualTo("schoolId", teacherSchoolId)
            .get()
            .addOnSuccessListener { documents ->
                android.util.Log.d("AdminStudents", "✅ Query successful, documents: ${documents.size()}")
                processStudentDocuments(documents)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AdminStudents", "❌ Query failed: ${e.message}", e)
                handleLoadError(e)
            }
    }
    
    /**
     * Load all students (admin mode or backward compatibility)
     */
    private fun loadAllStudents() {
        firestore.collection("Users")
            .whereEqualTo("role", "student")
            .get()
            .addOnSuccessListener { documents ->
                processStudentDocuments(documents)
            }
            .addOnFailureListener { e ->
                handleLoadError(e)
            }
    }
    
    /**
     * Process student documents (common logic)
     */
    private fun processStudentDocuments(documents: com.google.firebase.firestore.QuerySnapshot) {
        if (_binding == null) {
            android.util.Log.w("AdminStudents", "⚠️ Binding is null, cannot process documents")
            return
        }
        
        android.util.Log.d("AdminStudents", "📦 Processing ${documents.size()} student documents")

        swipeRefreshLayout.isRefreshing = false
        studentsList.clear()

        for (document in documents) {
            try {
                val student = StudentData(
                    uid = document.id,
                    name = document.getString("name") ?: "Unknown",
                    email = document.getString("email") ?: "No email",
                    phone = document.getString("phone") ?: "No phone",
                    dob = document.getString("dob") ?: "Not provided",
                    gender = document.getString("gender") ?: "Not specified",
                    ecoPoints = document.getLong("ecoPoints") ?: 0,
                    profilePictureUrl = document.getString("profilePictureUrl") ?: "",
                    batchId = document.getString("batchId")
                )
                studentsList.add(student)
                android.util.Log.d("AdminStudents", "   ✓ Added student: ${student.name} (${student.email}) - Batch: ${student.batchId}")
            } catch (e: Exception) {
                android.util.Log.e("AdminStudents", "❌ Error parsing student doc ${document.id}", e)
            }
        }

        // Sort by EcoPoints (highest first)
        studentsList.sortByDescending { it.ecoPoints }

        binding.progressBar.visibility = View.GONE
        binding.tvStudentCount.text = "Total Students: ${studentsList.size}"

        if (studentsList.isEmpty()) {
            android.util.Log.d("AdminStudents", "📭 No students found, showing empty state")
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.tvEmptyState.text = when {
                isLeadTeacher -> "👥 No students in your school yet"
                assignedBatches.isNotEmpty() -> "👥 No students in your batches yet"
                else -> "👥 No students registered yet"
            }
            binding.recyclerViewStudents.visibility = View.GONE
        } else {
            android.util.Log.d("AdminStudents", "✅ Showing ${studentsList.size} students in RecyclerView")
            binding.tvEmptyState.visibility = View.GONE
            binding.recyclerViewStudents.visibility = View.VISIBLE
            
            // Update adapter with new list
            studentsAdapter.updateList(studentsList)
            
            // Force layout update
            binding.recyclerViewStudents.post {
                binding.recyclerViewStudents.requestLayout()
                android.util.Log.d("AdminStudents", "🔄 RecyclerView layout requested, adapter itemCount: ${studentsAdapter.itemCount}")
            }
        }
        
        // Mark data as loaded
        hasLoadedData = true
        android.util.Log.d("AdminStudents", "✅ Data loading complete, hasLoadedData set to true")
    }
    
    /**
     * Handle load error (common logic)
     */
    private fun handleLoadError(e: Exception) {
        if (_binding == null) return
        android.util.Log.e("AdminStudents", "❌ Failed to load students", e)

        swipeRefreshLayout.isRefreshing = false
        binding.progressBar.visibility = View.GONE
        binding.tvStudentCount.text = "Failed to load students"

        val errorMsg = when {
            e.message?.contains("PERMISSION_DENIED") == true -> "Permission denied. Check Firestore rules."
            e.message?.contains("network") == true || e.message?.contains("UNAVAILABLE") == true -> "Network error. Check your connection."
            else -> "Failed to load students"
        }

        UiUtils.showErrorSnackbar(binding.root, errorMsg, "Retry") {
            loadStudents()
        }
    }

    private fun filterStudents(query: String) {
        val filteredList = if (query.isEmpty()) {
            studentsList
        } else {
            studentsList.filter { student ->
                student.name.contains(query, ignoreCase = true) ||
                        student.email.contains(query, ignoreCase = true)
            }
        }

        studentsAdapter.updateList(filteredList)

        // Update count label consistently
        binding.tvStudentCount.text = if (query.isEmpty()) {
            "Total Students: ${studentsList.size}"
        } else {
            "Showing ${filteredList.size} of ${studentsList.size} students"
        }

        // Show/hide empty state for search results
        if (filteredList.isEmpty() && query.isNotEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.tvEmptyState.text = "🔍 No students found for \"$query\""
            binding.recyclerViewStudents.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.recyclerViewStudents.visibility = View.VISIBLE
        }
    }

    private fun showStudentDetails(student: StudentData) {
        android.util.Log.d("AdminStudents", "📋 Showing details for student: ${student.name}, batchId: ${student.batchId}")
        
        // Show loading dialog while fetching batch name
        val loadingDialog = AlertDialog.Builder(requireContext())
            .setTitle("Loading Student Details...")
            .setMessage("Please wait...")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Fetch batch name if batchId exists
        if (student.batchId != null) {
            android.util.Log.d("AdminStudents", "🔍 Fetching batch document: ${student.batchId}")
            
            firestore.collection("Batches")
                .document(student.batchId)
                .get()
                .addOnSuccessListener { batchDoc ->
                    loadingDialog.dismiss()
                    if (_binding == null) return@addOnSuccessListener
                    
                    val batchName = if (batchDoc.exists()) {
                        val name = batchDoc.getString("batchName") ?: "Unknown Batch"
                        android.util.Log.d("AdminStudents", "✅ Batch found: $name")
                        name
                    } else {
                        android.util.Log.w("AdminStudents", "⚠️ Batch document doesn't exist")
                        "Batch Not Found"
                    }
                    
                    displayStudentDetailsDialog(student, batchName)
                }
                .addOnFailureListener { e ->
                    loadingDialog.dismiss()
                    if (_binding == null) return@addOnFailureListener
                    
                    android.util.Log.e("AdminStudents", "❌ Failed to fetch batch: ${e.message}", e)
                    displayStudentDetailsDialog(student, "Error loading batch")
                }
        } else {
            loadingDialog.dismiss()
            android.util.Log.w("AdminStudents", "⚠️ Student has no batchId")
            displayStudentDetailsDialog(student, "No Batch Assigned")
        }
    }
    
    private fun displayStudentDetailsDialog(student: StudentData, batchName: String) {
        val levelInfo = com.example.capstone.utils.LevelCalculator.getLevelInfo(student.ecoPoints.toInt())
        val toNext = com.example.capstone.utils.LevelCalculator.pointsToNextLevel(student.ecoPoints.toInt())
        val nextLevelText = if (toNext > 0) "$toNext pts to next level" else "Max Level 🏆"

        val message = """
            👤 Name: ${student.name}
            📧 Email: ${student.email}
            📱 Phone: ${student.phone}
            🎂 Date of Birth: ${student.dob}
            ⚧ Gender: ${student.gender}
            📚 Batch: $batchName
            ⭐ EcoPoints: ${student.ecoPoints}
            🏅 Level: ${levelInfo.level} — ${levelInfo.title} ${levelInfo.emoji}
            📈 Progress: $nextLevelText
            
            🆔 Student ID: ${student.uid}
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Student Details")
            .setMessage(message)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("View Progress") { dialog, _ ->
                dialog.dismiss()
                showStudentProgressDialog(student)
            }
            .show()
    }

    private fun showStudentProgressDialog(student: StudentData) {
        // Show loading dialog while fetching real data
        val loadingDialog = AlertDialog.Builder(requireContext())
            .setTitle("Loading Progress...")
            .setMessage("Fetching real data for ${student.name}...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        // Fetch real data from Firestore
        var modulesCompleted = 0
        var challengesApproved = 0
        var quizCount = 0
        var totalScore = 0
        var pendingCount = 0

        // Step 1: Get completed modules
        firestore.collection("Users").document(student.uid)
            .collection("completions")
            .get()
            .addOnSuccessListener { completions ->
                modulesCompleted = completions.size()

                // Step 2: Get approved challenges
                firestore.collection("ChallengeSubmissions")
                    .whereEqualTo("studentId", student.uid)
                    .whereEqualTo("status", "approved")
                    .get()
                    .addOnSuccessListener { approved ->
                        challengesApproved = approved.size()

                        // Step 3: Get pending challenges
                        firestore.collection("ChallengeSubmissions")
                            .whereEqualTo("studentId", student.uid)
                            .whereEqualTo("status", "pending")
                            .get()
                            .addOnSuccessListener { pending ->
                                pendingCount = pending.size()

                                // Step 4: Get quiz attempts
                                firestore.collection("Users").document(student.uid)
                                    .collection("quizAttempts")
                                    .get()
                                    .addOnSuccessListener { quizzes ->
                                        for (quiz in quizzes) {
                                            val score = quiz.getLong("percentage")?.toInt() ?: 0
                                            if (score > 0) {
                                                totalScore += score
                                                quizCount++
                                            }
                                        }
                                        val avgScore = if (quizCount > 0) totalScore / quizCount else 0

                                        loadingDialog.dismiss()
                                        if (_binding == null) return@addOnSuccessListener

                                        showProgressResult(
                                            student, modulesCompleted,
                                            challengesApproved, pendingCount,
                                            quizCount, avgScore
                                        )
                                    }
                                    .addOnFailureListener {
                                        loadingDialog.dismiss()
                                        if (_binding == null) return@addOnFailureListener
                                        showProgressResult(student, modulesCompleted, challengesApproved, pendingCount, 0, 0)
                                    }
                            }
                            .addOnFailureListener {
                                loadingDialog.dismiss()
                                if (_binding == null) return@addOnFailureListener
                                showProgressResult(student, modulesCompleted, challengesApproved, 0, 0, 0)
                            }
                    }
                    .addOnFailureListener {
                        loadingDialog.dismiss()
                        if (_binding == null) return@addOnFailureListener
                        showProgressResult(student, modulesCompleted, 0, 0, 0, 0)
                    }
            }
            .addOnFailureListener {
                loadingDialog.dismiss()
                if (_binding == null) return@addOnFailureListener
                showProgressResult(student, 0, 0, 0, 0, 0)
            }
    }

    private fun showProgressResult(
        student: StudentData,
        modulesCompleted: Int,
        challengesApproved: Int,
        challengesPending: Int,
        quizzesTaken: Int,
        avgQuizScore: Int
    ) {
        val levelInfo = com.example.capstone.utils.LevelCalculator.getLevelInfo(student.ecoPoints.toInt())
        val toNext = com.example.capstone.utils.LevelCalculator.pointsToNextLevel(student.ecoPoints.toInt())
        val progress = com.example.capstone.utils.LevelCalculator.progressInCurrentLevel(student.ecoPoints.toInt())

        val nextLevelText = if (toNext > 0) "$toNext pts to next level" else "Max Level Reached 🏆"

        val progressDetails = """
📊 ${student.name}'s Learning Progress

🎯 Overall Performance:
• Total EcoPoints: ${student.ecoPoints}
• Level: ${levelInfo.level} — ${levelInfo.title} ${levelInfo.emoji}
• Level Progress: $progress% ($nextLevelText)

📚 Module Progress:
• Modules Completed: $modulesCompleted

📝 Quiz Performance:
• Quizzes Taken: $quizzesTaken
• Average Score: ${if (avgQuizScore > 0) "$avgQuizScore%" else "No quizzes yet"}

🏆 Challenge Activity:
• Challenges Approved: $challengesApproved
• Challenges Pending: $challengesPending
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Progress Report")
            .setMessage(progressDetails)
            .setPositiveButton("Close", null)
            .setNeutralButton("Recalculate Points") { _, _ ->
                recalculateStudentPoints(student)
            }
            .show()
    }

    /**
     * Recalculates a student's EcoPoints from scratch based on existing valid data.
     * Use this to fix orphaned points after modules/challenges are deleted.
     */
    private fun recalculateStudentPoints(student: StudentData) {
        val loadingDialog = AlertDialog.Builder(requireContext())
            .setTitle("Recalculating...")
            .setMessage("Recalculating EcoPoints for ${student.name}")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        val schoolId = teacherSchoolId ?: ""

        // Step 1: Get valid module IDs for this school
        firestore.collection("Modules")
            .whereEqualTo("schoolId", schoolId)
            .get()
            .addOnSuccessListener { allModules ->
                val validModuleIds = allModules.documents.map { it.id }.toSet()

                // Step 2: Get valid challenge IDs for this school
                firestore.collection("Challenges")
                    .whereEqualTo("schoolId", schoolId)
                    .get()
                    .addOnSuccessListener { allChallenges ->
                        val validChallengeIds = allChallenges.documents.map { it.id }.toSet()

                        var totalPoints = 0L

                        // Step 3: Count points from valid module completions
                        firestore.collection("Users").document(student.uid)
                            .collection("completions").get()
                            .addOnSuccessListener { completions ->
                                for (completion in completions.documents) {
                                    if (completion.id in validModuleIds) {
                                        totalPoints += completion.getLong("points") ?: 0
                                    }
                                }

                                // Step 4: Quiz no longer awards points directly — skip quiz attempts
                                // Points come only from: module completion + approved challenges + game

                                // Step 5: Count points from valid approved challenge submissions
                                firestore.collection("ChallengeSubmissions")
                                    .whereEqualTo("studentId", student.uid)
                                    .whereEqualTo("status", "approved")
                                    .get()
                                    .addOnSuccessListener { submissions ->
                                        for (submission in submissions.documents) {
                                            val challengeId = submission.getString("challengeId") ?: ""
                                            if (challengeId in validChallengeIds) {
                                                totalPoints += submission.getLong("challengePoints") ?: 0
                                            }
                                        }

                                                // Step 6: Update the student's ecoPoints
                                                firestore.collection("Users").document(student.uid)
                                                    .update("ecoPoints", totalPoints)
                                                    .addOnSuccessListener {
                                                        loadingDialog.dismiss()
                                                        if (_binding == null) return@addOnSuccessListener
                                                        AlertDialog.Builder(requireContext())
                                                            .setTitle("✅ Points Recalculated")
                                                            .setMessage("${student.name}'s EcoPoints updated:\n\nPrevious: ${student.ecoPoints}\nRecalculated: $totalPoints")
                                                            .setPositiveButton("OK") { _, _ -> loadStudents() }
                                                            .show()
                                                    }
                                                    .addOnFailureListener { e ->
                                                        loadingDialog.dismiss()
                                                        android.widget.Toast.makeText(
                                                            requireContext(),
                                                            "Failed to update points: ${e.message}",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                            }
                            }
                    }
            }
            .addOnFailureListener { e ->
                loadingDialog.dismiss()
                android.widget.Toast.makeText(
                    requireContext(),
                    "Failed to recalculate: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showDeleteConfirmation(student: StudentData) {
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Delete Student")
            .setMessage("Are you sure you want to delete ${student.name}?\n\nThis will remove their account data. This action cannot be undone.")
            .setPositiveButton("Delete") { dialog, _ ->
                dialog.dismiss()
                deleteStudent(student)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deleteStudent(student: StudentData) {
        android.util.Log.d("AdminStudents", "Deleting student: ${student.uid}")

        firestore.collection("Users")
            .document(student.uid)
            .delete()
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                android.util.Log.d("AdminStudents", "✅ Student deleted: ${student.uid}")

                studentsList.remove(student)
                studentsAdapter.updateList(studentsList)
                binding.tvStudentCount.text = "Total Students: ${studentsList.size}"

                if (studentsList.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "👥 No students registered yet"
                    binding.recyclerViewStudents.visibility = View.GONE
                }

                UiUtils.showSuccessSnackbar(binding.root, "${student.name} deleted successfully")
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                android.util.Log.e("AdminStudents", "❌ Failed to delete student", e)
                UiUtils.showErrorSnackbar(binding.root, "Failed to delete student. Try again.")
            }
    }

    private fun calculateLevel(ecoPoints: Int): Int {
        return com.example.capstone.utils.LevelCalculator.getLevel(ecoPoints)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        android.util.Log.d("AdminStudents", "🗑️ onDestroyView called")
        _binding = null
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("AdminStudents", "▶️ onResume called, hasLoadedData: $hasLoadedData, isVisible: $isVisible, userVisibleHint: $userVisibleHint")
        
        // Reload data when fragment becomes visible if not already loaded
        // Check both isVisible and userVisibleHint for ViewPager2 compatibility
        if (!hasLoadedData && _binding != null && (isVisible || userVisibleHint)) {
            android.util.Log.d("AdminStudents", "🔄 Fragment visible but data not loaded, loading now...")
            loadTeacherInfo()
        }
    }
    
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        android.util.Log.d("AdminStudents", "👁️ setUserVisibleHint: $isVisibleToUser, hasLoadedData: $hasLoadedData")
        
        if (isVisibleToUser && !hasLoadedData && _binding != null) {
            android.util.Log.d("AdminStudents", "🔄 Fragment became visible, loading data...")
            loadTeacherInfo()
        }
    }
    
    override fun onPause() {
        super.onPause()
        android.util.Log.d("AdminStudents", "⏸️ onPause called")
    }
}

data class StudentData(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String,
    val dob: String,
    val gender: String,
    val ecoPoints: Long,
    val profilePictureUrl: String,
    val batchId: String? = null
)
