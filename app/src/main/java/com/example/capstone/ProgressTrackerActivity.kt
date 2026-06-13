package com.example.capstone

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone.databinding.ActivityProgressTrackerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProgressTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressTrackerBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadProgressData()
    }

    private fun loadProgressData() {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val userRef = db.collection("Users").document(uid)

        // Load user's total EcoPoints — use Source.SERVER to always get fresh data
        userRef.get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener { userDoc ->
            val ecoPoints = userDoc.getLong("ecoPoints") ?: 0
            binding.tvTotalPoints.text = "$ecoPoints"

            val schoolId = userDoc.getString("schoolId") ?: ""

            // Load school-specific totals then compute progress
            // Also pass schoolModuleIds to quiz section for cross-referencing
            loadSchoolStats(uid, schoolId, userRef)
        }

        // Load recent achievement (most recent completion)
        userRef.collection("completions")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { recentDocs ->
                if (!recentDocs.isEmpty) {
                    val recentModule = recentDocs.documents[0]
                    val title = recentModule.getString("title") ?: "Module"
                    val points = recentModule.getLong("points") ?: 0
                    binding.tvRecentAchievement.text = "Completed: $title\n+$points EcoPoints"
                } else {
                    binding.tvRecentAchievement.text = "Start learning to earn achievements!"
                }
            }
    }

    private fun loadSchoolStats(
        uid: String,
        schoolId: String,
        userRef: com.google.firebase.firestore.DocumentReference
    ) {
        if (schoolId.isEmpty()) {
            binding.tvModulesCompleted.text = "0/0"
            binding.progressModules.progress = 0
            binding.tvModulesPercentage.text = "0% complete"
            binding.tvChallengesCompleted.text = "0/0"
            binding.progressChallenges.progress = 0
            binding.tvChallengesPercentage.text = "0% complete"
            binding.tvQuizzesPassed.text = "0/0"
            binding.progressQuizzes.progress = 0
            binding.tvQuizzesPercentage.text = "0% pass rate"
            binding.progressOverall.progress = 0
            binding.tvOverallPercentage.text = "0%"
            binding.tvMilestone.text = "🌍 Begin your eco journey today!"
            return
        }

        // Force fresh data from server — no cache
        val source = com.google.firebase.firestore.Source.SERVER

        // Fetch total modules for this school
        db.collection("Modules").whereEqualTo("schoolId", schoolId).get(source)
            .addOnSuccessListener { allModules ->
                val totalModules = allModules.size()
                val schoolModuleIds = allModules.documents.map { it.id }.toSet()

                // Fetch total challenges for this school
                db.collection("Challenges").whereEqualTo("schoolId", schoolId).get(source)
                    .addOnSuccessListener { allChallenges ->
                        val totalChallenges = allChallenges.size()
                        val schoolChallengeIds = allChallenges.documents.map { it.id }.toSet()

                        // Fetch user completions — only count those matching existing school modules
                        userRef.collection("completions").get(source)
                            .addOnSuccessListener { completions ->
                                val completedModules = completions.documents
                                    .count { it.id in schoolModuleIds }

                                val moduleProgress = if (totalModules > 0)
                                    (completedModules * 100) / totalModules else 0

                                binding.tvModulesCompleted.text = "$completedModules/$totalModules"
                                binding.progressModules.progress = moduleProgress
                                binding.tvModulesPercentage.text = "$moduleProgress% complete"

                                // Fetch quiz attempts — only count those for existing school modules
                                userRef.collection("quizAttempts").get(source)
                                    .addOnSuccessListener { attempts ->
                                        // Filter: only count quiz attempts for modules that still exist
                                        val validAttempts = attempts.documents
                                            .filter { it.id in schoolModuleIds }
                                        val totalAttempts = validAttempts.size
                                        val passedAttempts = validAttempts
                                            .count { it.getBoolean("passed") == true }

                                        binding.tvQuizzesPassed.text = "$passedAttempts/$totalAttempts"

                                        if (totalAttempts > 0) {
                                            val passRate = (passedAttempts * 100) / totalAttempts
                                            binding.progressQuizzes.progress = passRate
                                            binding.tvQuizzesPercentage.text = "$passRate% pass rate"
                                        } else {
                                            binding.progressQuizzes.progress = 0
                                            binding.tvQuizzesPercentage.text = "0% pass rate"
                                        }

                                        // Fetch challenge submissions — only count approved ones for existing challenges
                                        db.collection("ChallengeSubmissions")
                                            .whereEqualTo("studentId", uid)
                                            .whereEqualTo("status", "approved")
                                            .get(source)
                                            .addOnSuccessListener { submissions ->
                                                val completedChallenges = submissions.documents
                                                    .map { it.getString("challengeId") ?: it.id }
                                                    .count { it in schoolChallengeIds }

                                                val challengeProgress = if (totalChallenges > 0)
                                                    (completedChallenges * 100) / totalChallenges else 0

                                                binding.tvChallengesCompleted.text =
                                                    "$completedChallenges/$totalChallenges"
                                                binding.progressChallenges.progress = challengeProgress
                                                binding.tvChallengesPercentage.text =
                                                    "$challengeProgress% complete"

                                                // Overall progress across modules + challenges
                                                val totalItems = totalModules + totalChallenges
                                                val totalCompleted = completedModules + completedChallenges
                                                val overallProgress = if (totalItems > 0)
                                                    (totalCompleted * 100) / totalItems else 0

                                                binding.progressOverall.progress = overallProgress
                                                binding.tvOverallPercentage.text = "$overallProgress%"

                                                binding.tvMilestone.text = when {
                                                    overallProgress >= 100 ->
                                                        "🏆 Eco Master! You've completed everything!"
                                                    overallProgress >= 75 ->
                                                        "🌟 Almost there! Keep going!"
                                                    overallProgress >= 50 ->
                                                        "🌱 Halfway to Eco Expert!"
                                                    overallProgress >= 25 ->
                                                        "🌿 Great start! Keep learning!"
                                                    else ->
                                                        "🌍 Begin your eco journey today!"
                                                }
                                            }
                                    }
                            }
                    }
            }
    }
}
