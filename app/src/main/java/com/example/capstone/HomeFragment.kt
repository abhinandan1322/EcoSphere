package com.example.capstone

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.capstone.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Fallback cache — only used when server fetch fails
    private var cachedUserData: UserData? = null

    data class UserData(
        val name: String,
        val ecoPoints: Long,
        val level: Int,
        val rank: Int = 0
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Hide badges immediately on load — they'll be shown only if data confirms it
        binding.tvUnreadBadge.visibility = View.GONE
        binding.tvNotificationBadge.visibility = View.GONE
        
        updateGreeting()
        loadUserData()
        loadDailyStreak()
        displayDailyTip()
        setupClickListeners()
        loadAnnouncementPreview()
        loadNotificationBadge()
    }
    
    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val greeting = when (hour) {
            in 5..11  -> "Good Morning 🌅"       // 5:00 AM – 11:59 AM
            in 12..16 -> "Good Afternoon ☀️"     // 12:00 PM – 4:59 PM
            in 17..20 -> "Good Evening 🌆"       // 5:00 PM – 8:59 PM
            else      -> "Have a Good Night 🌙"  // 9:00 PM – 4:59 AM
        }

        binding.tvGreeting.text = greeting
    }
    
    private fun loadDailyStreak() {
        val userId = auth.currentUser?.uid ?: return
        
        firestore.collection("Users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (_binding == null || !document.exists()) return@addOnSuccessListener
                
                val lastLoginAt = document.getTimestamp("lastLoginAt")
                val currentStreak = document.getLong("currentStreak")?.toInt() ?: 0
                
                // Calculate streak
                val streak = calculateStreak(lastLoginAt, currentStreak)
                
                // Update UI
                updateStreakUI(streak)
                
                // Update streak in Firestore if changed
                if (streak != currentStreak) {
                    updateStreakInFirestore(userId, streak)
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                // Show 0 streak on failure
                updateStreakUI(0)
            }
    }
    
    private fun calculateStreak(lastLoginAt: com.google.firebase.Timestamp?, currentStreak: Int): Int {
        if (lastLoginAt == null) return 1 // First login
        
        val lastLoginDate = Calendar.getInstance().apply {
            timeInMillis = lastLoginAt.toDate().time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val daysDifference = ((today.timeInMillis - lastLoginDate.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        
        return when {
            daysDifference == 0 -> currentStreak // Same day, keep current streak
            daysDifference == 1 -> currentStreak + 1 // Next day, increment streak
            else -> 1 // Streak broken, start over
        }
    }
    
    private fun updateStreakUI(streak: Int) {
        if (_binding == null) return
        
        when {
            streak == 0 -> {
                binding.tvStreakTitle.text = "Start Your Streak!"
                binding.tvStreakMessage.text = "Login daily to build your learning streak"
            }
            streak == 1 -> {
                binding.tvStreakTitle.text = "1 Day Streak!"
                binding.tvStreakMessage.text = "Great start! Come back tomorrow to continue"
            }
            streak < 7 -> {
                binding.tvStreakTitle.text = "$streak Day Streak!"
                binding.tvStreakMessage.text = "Keep learning to maintain your streak"
            }
            streak < 30 -> {
                binding.tvStreakTitle.text = "$streak Day Streak! 🔥"
                binding.tvStreakMessage.text = "Amazing! You're on fire!"
            }
            else -> {
                binding.tvStreakTitle.text = "$streak Day Streak! 🔥🔥"
                binding.tvStreakMessage.text = "Incredible dedication! Keep it up!"
            }
        }
    }
    
    private fun updateStreakInFirestore(userId: String, newStreak: Int) {
        firestore.collection("Users")
            .document(userId)
            .update(
                mapOf(
                    "currentStreak" to newStreak,
                    "lastStreakUpdate" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnSuccessListener {
                android.util.Log.d("HomeFragment", "Streak updated to $newStreak")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("HomeFragment", "Failed to update streak", e)
            }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser ?: return

        // Always fetch fresh from server — no cache
        // This ensures EcoPoints reflect immediately after any teacher action
        firestore.collection("Users")
            .document(currentUser.uid)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { document ->
                if (_binding == null) return@addOnSuccessListener
                if (document.exists()) {
                    val name = document.getString("name") ?: "Eco Warrior"
                    val ecoPoints = document.getLong("ecoPoints") ?: 0
                    val level = calculateLevel(ecoPoints.toInt())
                    
                    val userData = UserData(name, ecoPoints, level)
                    displayUserData(userData)
                    loadStats(currentUser.uid)
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                // On failure, try cache as fallback
                cachedUserData?.let { displayUserData(it) }
            }
    }
    
    private fun displayUserData(userData: UserData) {
        binding.tvWelcome.text = "Welcome Back!"
        binding.tvEcoPoints.text = userData.ecoPoints.toString()

        // Show level number + emoji using LevelCalculator
        val levelInfo = com.example.capstone.utils.LevelCalculator.getLevelInfo(userData.ecoPoints.toInt())
        binding.tvLevel.text = "${levelInfo.level}"
    }

    private fun calculateLevel(ecoPoints: Int): Int {
        return com.example.capstone.utils.LevelCalculator.getLevel(ecoPoints)
    }

    private fun loadStats(uid: String) {
        // First get the student's schoolId to fetch dynamic totals
        firestore.collection("Users").document(uid)
            .get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener

                val schoolId = userDoc.getString("schoolId") ?: ""

                // --- Modules ---
                // Get total modules for this school
                val modulesQuery = if (schoolId.isNotEmpty())
                    firestore.collection("Modules").whereEqualTo("schoolId", schoolId)
                else
                    null

                // Get total challenges for this school
                val challengesQuery = if (schoolId.isNotEmpty())
                    firestore.collection("Challenges").whereEqualTo("schoolId", schoolId)
                else
                    null

                // Load module stats
                if (modulesQuery != null) {
                    modulesQuery.get().addOnSuccessListener { allModules ->
                        if (_binding == null) return@addOnSuccessListener
                        val totalModules = allModules.size()
                        val schoolModuleIds = allModules.documents.map { it.id }.toSet()

                        firestore.collection("Users").document(uid)
                            .collection("completions")
                            .get()
                            .addOnSuccessListener { completions ->
                                if (_binding == null) return@addOnSuccessListener
                                // Only count completions that belong to this school's modules
                                val completedCount = completions.documents.count { it.id in schoolModuleIds }
                                binding.tvModulesCompleted.text = "$completedCount/$totalModules"
                            }
                            .addOnFailureListener {
                                if (_binding == null) return@addOnFailureListener
                                binding.tvModulesCompleted.text = "0/$totalModules"
                            }
                    }.addOnFailureListener {
                        if (_binding == null) return@addOnFailureListener
                        // Fallback: just show completions without total
                        firestore.collection("Users").document(uid)
                            .collection("completions").get()
                            .addOnSuccessListener { completions ->
                                if (_binding == null) return@addOnSuccessListener
                                binding.tvModulesCompleted.text = "${completions.size()}/-"
                            }
                    }
                } else {
                    binding.tvModulesCompleted.text = "0/0"
                }

                // Load challenge stats
                if (challengesQuery != null) {
                    challengesQuery.get().addOnSuccessListener { allChallenges ->
                        if (_binding == null) return@addOnSuccessListener
                        val totalChallenges = allChallenges.size()
                        val schoolChallengeIds = allChallenges.documents.map { it.id }.toSet()

                        // Read from top-level ChallengeSubmissions (approved only)
                        firestore.collection("ChallengeSubmissions")
                            .whereEqualTo("studentId", uid)
                            .whereEqualTo("status", "approved")
                            .get()
                            .addOnSuccessListener { submissions ->
                                if (_binding == null) return@addOnSuccessListener
                                val submittedCount = submissions.documents
                                    .map { it.getString("challengeId") ?: it.id }
                                    .count { it in schoolChallengeIds }
                                binding.tvChallengesCompleted.text = "$submittedCount/$totalChallenges"
                            }
                            .addOnFailureListener {
                                if (_binding == null) return@addOnFailureListener
                                binding.tvChallengesCompleted.text = "0/$totalChallenges"
                            }
                    }.addOnFailureListener {
                        if (_binding == null) return@addOnFailureListener
                        binding.tvChallengesCompleted.text = "0/0"
                    }
                } else {
                    binding.tvChallengesCompleted.text = "0/0"
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                // Fallback to plain counts with no total
                firestore.collection("Users").document(uid)
                    .collection("completions").get()
                    .addOnSuccessListener { c ->
                        if (_binding == null) return@addOnSuccessListener
                        binding.tvModulesCompleted.text = "${c.size()}/-"
                    }
                firestore.collection("ChallengeSubmissions")
                    .whereEqualTo("studentId", uid)
                    .whereEqualTo("status", "approved")
                    .get()
                    .addOnSuccessListener { c ->
                        if (_binding == null) return@addOnSuccessListener
                        binding.tvChallengesCompleted.text = "${c.size()}/-"
                    }
            }
    }

    private fun displayDailyTip() {
        val ecoTips = listOf(
            "Turn off lights when leaving a room to save energy and reduce your carbon footprint. Small actions make a big difference!",
            "Unplug chargers when not in use - they consume energy even when idle.",
            "Use a reusable water bottle instead of buying plastic bottles.",
            "Bring your own bags when shopping to reduce plastic waste.",
            "Take shorter showers to conserve water.",
            "Compost food scraps instead of throwing them in the trash.",
            "Use public transport, bike, or walk instead of driving alone.",
            "Buy local and seasonal produce to reduce carbon emissions from transport.",
            "Switch to LED bulbs - they use 75% less energy than traditional bulbs.",
            "Avoid single-use plastics like straws, cups, and cutlery.",
            "Recycle paper, glass, and metal to reduce landfill waste.",
            "Plant trees or support reforestation projects in your area.",
            "Fix leaky taps - a dripping tap wastes thousands of liters per year.",
            "Use both sides of paper before recycling it.",
            "Buy second-hand clothes instead of fast fashion.",
            "Reduce meat consumption - try Meatless Mondays!",
            "Use a reusable coffee cup instead of disposable ones.",
            "Turn off your computer and monitor when not in use.",
            "Use natural cleaning products to reduce chemical pollution.",
            "Support eco-friendly brands and businesses.",
            "Donate old items instead of throwing them away.",
            "Use a programmable thermostat to save energy on heating/cooling.",
            "Avoid food waste by planning meals and storing food properly.",
            "Use rechargeable batteries instead of disposable ones.",
            "Carpool with friends or colleagues to reduce emissions.",
            "Choose products with minimal or recyclable packaging.",
            "Air-dry clothes instead of using a dryer when possible.",
            "Use a rain barrel to collect water for your garden.",
            "Support renewable energy by choosing green energy providers.",
            "Educate others about environmental issues and solutions."
        )

        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val tipIndex = dayOfYear % ecoTips.size
        
        binding.tvDailyTip.text = ecoTips[tipIndex]
    }

    private fun setupClickListeners() {
        // Updated for new card-based buttons in premium UI
        binding.btnStartLearning.setOnClickListener {
            // Switch to Learn tab
            (activity as? StudentMainActivity)?.navigateToTab(1)
        }

        binding.btnViewProgress.setOnClickListener {
            // Open Progress Tracker activity
            startActivity(Intent(requireContext(), ProgressTrackerActivity::class.java))
        }

        binding.cardAnnouncements.setOnClickListener {
            startActivity(Intent(requireContext(), AnnouncementsActivity::class.java))
        }

        binding.btnNotificationBell.setOnClickListener {
            startActivity(Intent(requireContext(), InAppNotificationsActivity::class.java))
        }
    }

    private fun loadAnnouncementPreview() {
        val userId = auth.currentUser?.uid ?: return

        // First, get the student's schoolId
        firestore.collection("Users")
            .document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null || !userDoc.exists()) return@addOnSuccessListener
                
                val studentSchoolId = userDoc.getString("schoolId") ?: ""
                
                android.util.Log.d("HomeFragment", "Student schoolId: '$studentSchoolId'")
                
                if (studentSchoolId.isEmpty()) {
                    // Student has no school assigned
                    binding.tvAnnouncementPreview.text = "No announcements yet"
                    binding.tvUnreadBadge.visibility = View.GONE
                    return@addOnSuccessListener
                }
                
                // Load announcements filtered by schoolId
                // Force fresh data from server to avoid cache issues
                firestore.collection("Announcements")
                    .whereEqualTo("schoolId", studentSchoolId)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(10)
                    .get(com.google.firebase.firestore.Source.SERVER) // Force server fetch
                    .addOnSuccessListener { documents ->
                        if (_binding == null) return@addOnSuccessListener

                        android.util.Log.d("HomeFragment", "Total announcements found: ${documents.size()}")
                        
                        // Debug: Log each announcement
                        documents.forEachIndexed { index, doc ->
                            val readBy = doc.get("readBy") as? List<*> ?: emptyList<String>()
                            val isRead = readBy.contains(userId)
                            android.util.Log.d("HomeFragment", "Announcement $index: id=${doc.id}, schoolId=${doc.getString("schoolId")}, isRead=$isRead, readBy=$readBy")
                        }

                        val unreadCount = documents.count { doc ->
                            val readBy = doc.get("readBy") as? List<*> ?: emptyList<String>()
                            val isUnread = !readBy.contains(userId)
                            isUnread
                        }

                        android.util.Log.d("HomeFragment", "Unread count: $unreadCount for user: $userId")

                        if (documents.isEmpty()) {
                            binding.tvAnnouncementPreview.text = "No announcements yet"
                            binding.tvUnreadBadge.visibility = View.GONE
                        } else {
                            // Show latest announcement title as preview
                            val latestTitle = documents.first().getString("title") ?: "New announcement"
                            binding.tvAnnouncementPreview.text = "Latest: $latestTitle"

                            if (unreadCount > 0) {
                                binding.tvUnreadBadge.visibility = View.VISIBLE
                                binding.tvUnreadBadge.text = "$unreadCount new"
                            } else {
                                binding.tvUnreadBadge.visibility = View.GONE
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        if (_binding == null) return@addOnFailureListener
                        android.util.Log.e("HomeFragment", "Failed to load announcements: ${e.message}", e)
                        binding.tvAnnouncementPreview.text = "Tap to see latest updates"
                        binding.tvUnreadBadge.visibility = View.GONE
                    }
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                android.util.Log.e("HomeFragment", "Failed to load user: ${e.message}", e)
                binding.tvAnnouncementPreview.text = "Tap to see latest updates"
                binding.tvUnreadBadge.visibility = View.GONE
            }
    }

    private fun loadNotificationBadge() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("PushNotifications")
            .whereEqualTo("type", "admin_push")
            .whereEqualTo("status", "sent")
            .orderBy("sentAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener

                // Count unread notifications
                val prefs = requireContext().getSharedPreferences("notifications", android.content.Context.MODE_PRIVATE)
                val unreadCount = documents.count { doc ->
                    !prefs.getBoolean("read_${userId}_${doc.id}", false)
                }

                if (unreadCount > 0) {
                    binding.tvNotificationBadge.visibility = View.VISIBLE
                    binding.tvNotificationBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                } else {
                    binding.tvNotificationBadge.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.tvNotificationBadge.visibility = View.GONE
            }
    }

    override fun onResume() {
        super.onResume()
        // Reset badges immediately before re-querying to avoid stale counts
        if (_binding != null) {
            binding.tvUnreadBadge.visibility = View.GONE
            binding.tvNotificationBadge.visibility = View.GONE
        }
        // Refresh data when returning to home
        loadUserData()
        loadDailyStreak()
        loadNotificationBadge()
        loadAnnouncementPreview()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
