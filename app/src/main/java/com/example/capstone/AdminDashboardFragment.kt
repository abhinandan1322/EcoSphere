package com.example.capstone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.capstone.databinding.FragmentAdminDashboardBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AdminDashboardFragment : Fragment(), NavigationAware {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val firestore = FirebaseFirestore.getInstance()
    private var navigationController: NavigationController? = null
    
    // NEW: Multi-tenancy fields
    private var teacherSchoolId: String? = null
    private var assignedBatches: List<String> = emptyList()
    private var currentUserId: String? = null
    private var isLeadTeacher: Boolean = false

    override fun setNavigationController(controller: NavigationController) {
        navigationController = controller
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        displayCurrentDate()
        loadTeacherInfo()
        setupClickListeners()
    }
    
    /**
     * NEW: Load teacher information to get assigned batches
     */
    private fun loadTeacherInfo() {
        currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            loadAnalytics()
            return
        }
        
        firestore.collection("Users")
            .document(currentUserId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    teacherSchoolId = document.getString("schoolId")
                    val batchesArray = document.get("assignedBatches") as? List<*>
                    assignedBatches = batchesArray?.mapNotNull { it as? String } ?: emptyList()
                    isLeadTeacher = document.getBoolean("isLeadTeacher") ?: false
                    
                    android.util.Log.d("Dashboard", "Teacher school: $teacherSchoolId")
                    android.util.Log.d("Dashboard", "Assigned batches: $assignedBatches")
                    android.util.Log.d("Dashboard", "Is Lead Teacher: $isLeadTeacher")
                    
                    // Show/hide features based on lead teacher status
                    updateUIForTeacherRole()
                }
                loadAnalytics()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("Dashboard", "Failed to load teacher info", e)
                loadAnalytics()
            }
    }

    private fun displayCurrentDate() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        binding.tvCurrentDate.text = dateFormat.format(Date())
    }

    /**
     * Update UI based on teacher role (Lead Teacher vs Normal Teacher)
     */
    private fun updateUIForTeacherRole() {
        if (_binding == null) return
        
        // Hide Test Daily Reminder for Normal Teachers
        if (isLeadTeacher) {
            binding.cardTestDailyReminder.visibility = View.VISIBLE
        } else {
            binding.cardTestDailyReminder.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.cardLiveActivity.setOnClickListener {
            navigationController?.navigateToActivity(LiveActivityFeedActivity::class.java)
        }
        binding.cardTopPerformers.setOnClickListener {
            navigationController?.navigateToActivity(TopPerformersActivity::class.java)
        }
        binding.cardReviewChallenges.setOnClickListener {
            navigationController?.navigateToActivity(ReviewChallengesActivity::class.java)
        }
        binding.cardSendAnnouncement.setOnClickListener {
            navigationController?.navigateToActivity(SendAnnouncementActivity::class.java)
        }
        binding.cardPushNotification.setOnClickListener {
            navigationController?.navigateToActivity(AdminPushNotificationActivity::class.java)
        }
        binding.cardTestDailyReminder.setOnClickListener {
            navigationController?.navigateToActivity(TestDailyReminderActivity::class.java)
        }

        loadPendingChallengesCount()
    }

    private fun loadAnalytics() {
        loadTotalStudents()
        loadActiveTodayUsers()
        loadTotalEcoPoints()
        loadAverageCompletion()
    }

    // ─── Card 1: Total Students ───────────────────────────────────────────────

    private fun loadTotalStudents() {
        binding.tvTotalStudents.text = "..."
        android.util.Log.d("Dashboard", "Loading total students...")

        // NEW: Filter by batches or school
        val query = when {
            assignedBatches.isNotEmpty() -> {
                firestore.collection("Users")
                    .whereEqualTo("role", "student")
                    .whereIn("batchId", assignedBatches.take(10))
            }
            teacherSchoolId != null -> {
                firestore.collection("Users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", teacherSchoolId)
            }
            else -> {
                firestore.collection("Users")
                    .whereEqualTo("role", "student")
            }
        }

        query.get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener
                val count = documents.size()
                android.util.Log.d("Dashboard", "Total students: $count")
                binding.tvTotalStudents.text = count.toString()
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                android.util.Log.e("Dashboard", "Failed to load total students", e)
                binding.tvTotalStudents.text = "—"
            }
    }

    // ─── Card 2: Active Today ─────────────────────────────────────────────────
    // Uses lastLoginAt timestamp field to count students who logged in today.
    // Falls back to ecoPoints > 0 if lastLoginAt is not available.

    private fun loadActiveTodayUsers() {
        binding.tvActiveUsers.text = "..."
        android.util.Log.d("Dashboard", "Loading active today users...")

        // Get start of today (midnight)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = com.google.firebase.Timestamp(calendar.time)

        // NEW: Filter by batches or school
        val query = when {
            assignedBatches.isNotEmpty() -> {
                firestore.collection("Users")
                    .whereEqualTo("role", "student")
                    .whereIn("batchId", assignedBatches.take(10))
            }
            teacherSchoolId != null -> {
                firestore.collection("Users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", teacherSchoolId)
            }
            else -> {
                firestore.collection("Users")
                    .whereEqualTo("role", "student")
            }
        }

        query.whereGreaterThanOrEqualTo("lastLoginAt", startOfToday)
            .get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener
                val count = documents.size()
                android.util.Log.d("Dashboard", "Active today (by lastLoginAt): $count")

                if (count == 0) {
                    loadActiveTodayFallback()
                } else {
                    binding.tvActiveUsers.text = count.toString()
                }
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                android.util.Log.w("Dashboard", "lastLoginAt query failed, using fallback: ${e.message}")
                loadActiveTodayFallback()
            }
    }

    private fun loadActiveTodayFallback() {
        // lastLoginAt field not available — show 0 rather than misleading "has points" count
        if (_binding == null) return
        android.util.Log.d("Dashboard", "lastLoginAt not available, showing 0 for active today")
        binding.tvActiveUsers.text = "0"
    }

    // ─── Card 3: Total EcoPoints ──────────────────────────────────────────────

    private fun loadTotalEcoPoints() {
        binding.tvTotalEcoPoints.text = "..."
        android.util.Log.d("Dashboard", "Loading total EcoPoints...")

        // NEW: Filter by batches or school
        val query = when {
            assignedBatches.isNotEmpty() -> {
                firestore.collection("Users")
                    .whereEqualTo("role", "student")
                    .whereIn("batchId", assignedBatches.take(10))
            }
            teacherSchoolId != null -> {
                firestore.collection("Users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", teacherSchoolId)
            }
            else -> {
                firestore.collection("Users")
                    .whereEqualTo("role", "student")
            }
        }

        query.get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener
                var totalPoints = 0L
                for (doc in documents) {
                    totalPoints += doc.getLong("ecoPoints") ?: 0
                }
                android.util.Log.d("Dashboard", "Total EcoPoints: $totalPoints")
                binding.tvTotalEcoPoints.text = formatPoints(totalPoints)
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                android.util.Log.e("Dashboard", "Failed to load total EcoPoints", e)
                binding.tvTotalEcoPoints.text = "—"
            }
    }

    // Format large numbers: 1250 → "1.2K", 1000000 → "1M"
    private fun formatPoints(points: Long): String {
        return when {
            points >= 1_000_000 -> String.format("%.1fM pts", points / 1_000_000.0)
            points >= 1_000 -> String.format("%.1fK pts", points / 1_000.0)
            else -> "$points pts"
        }
    }

    // ─── Card 4: Average Completion Rate ─────────────────────────────────────
    // Counts actual completions subcollection entries per student.
    // This is accurate since quiz no longer awards points separately.

    private fun loadAverageCompletion() {
        binding.tvAverageCompletion.text = "..."
        android.util.Log.d("Dashboard", "Loading average completion...")

        // Filter modules by school
        val modulesQuery = when {
            teacherSchoolId != null ->
                firestore.collection("Modules").whereEqualTo("schoolId", teacherSchoolId)
            else ->
                firestore.collection("Modules")
        }

        modulesQuery.get()
            .addOnSuccessListener { moduleDocs ->
                if (_binding == null) return@addOnSuccessListener
                val totalModules = moduleDocs.size()
                android.util.Log.d("Dashboard", "Total modules: $totalModules")

                if (totalModules == 0) {
                    binding.tvAverageCompletion.text = "N/A"
                    return@addOnSuccessListener
                }

                val schoolModuleIds = moduleDocs.documents.map { it.id }.toSet()

                // Get students filtered by school/batch
                val query = when {
                    assignedBatches.isNotEmpty() -> {
                        firestore.collection("Users")
                            .whereEqualTo("role", "student")
                            .whereIn("batchId", assignedBatches.take(10))
                    }
                    teacherSchoolId != null -> {
                        firestore.collection("Users")
                            .whereEqualTo("role", "student")
                            .whereEqualTo("schoolId", teacherSchoolId)
                    }
                    else -> {
                        firestore.collection("Users")
                            .whereEqualTo("role", "student")
                    }
                }

                query.get()
                    .addOnSuccessListener { studentDocs ->
                        if (_binding == null) return@addOnSuccessListener

                        if (studentDocs.isEmpty) {
                            binding.tvAverageCompletion.text = "0%"
                            return@addOnSuccessListener
                        }

                        val studentIds = studentDocs.documents.map { it.id }
                        var totalCompletionRate = 0.0
                        var processedStudents = 0

                        for (studentId in studentIds) {
                            firestore.collection("Users").document(studentId)
                                .collection("completions").get()
                                .addOnSuccessListener { completions ->
                                    // Only count completions for existing school modules
                                    val validCompletions = completions.documents
                                        .count { it.id in schoolModuleIds }
                                    val rate = validCompletions.toDouble() / totalModules * 100
                                    totalCompletionRate += rate
                                    processedStudents++

                                    if (processedStudents == studentIds.size) {
                                        if (_binding == null) return@addOnSuccessListener
                                        val avgCompletion = (totalCompletionRate / studentIds.size)
                                            .toInt().coerceIn(0, 100)
                                        android.util.Log.d("Dashboard", "Avg completion: $avgCompletion%")
                                        binding.tvAverageCompletion.text = "$avgCompletion%"
                                    }
                                }
                                .addOnFailureListener {
                                    processedStudents++
                                    if (processedStudents == studentIds.size) {
                                        if (_binding == null) return@addOnFailureListener
                                        val avgCompletion = (totalCompletionRate / studentIds.size)
                                            .toInt().coerceIn(0, 100)
                                        binding.tvAverageCompletion.text = "$avgCompletion%"
                                    }
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        if (_binding == null) return@addOnFailureListener
                        android.util.Log.e("Dashboard", "Failed to load students for completion", e)
                        binding.tvAverageCompletion.text = "—"
                    }
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                android.util.Log.e("Dashboard", "Failed to load modules for completion", e)
                binding.tvAverageCompletion.text = "—"
            }
    }

    // ─── Pending Challenges Badge ─────────────────────────────────────────────

    private fun loadPendingChallengesCount() {
        // Filter pending challenges by school so teachers only see their school's submissions
        val query = when {
            teacherSchoolId != null ->
                firestore.collection("ChallengeSubmissions")
                    .whereEqualTo("status", "pending")
                    .whereEqualTo("schoolId", teacherSchoolId)
            else ->
                firestore.collection("ChallengeSubmissions")
                    .whereEqualTo("status", "pending")
        }

        query.get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener
                val count = documents.size()
                android.util.Log.d("Dashboard", "Pending challenges: $count")
                if (count > 0) {
                    binding.tvPendingCount.visibility = View.VISIBLE
                    binding.tvPendingCount.text = if (count > 99) "99+" else count.toString()
                } else {
                    binding.tvPendingCount.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                android.util.Log.e("Dashboard", "Failed to load pending count", e)
                binding.tvPendingCount.visibility = View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
