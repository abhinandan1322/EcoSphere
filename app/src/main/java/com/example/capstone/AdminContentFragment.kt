package com.example.capstone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.capstone.databinding.FragmentAdminContentBinding

class AdminContentFragment : Fragment(), NavigationAware {

    private var _binding: FragmentAdminContentBinding? = null
    private val binding get() = _binding!!
    private var navigationController: NavigationController? = null
    private var isLeadTeacher: Boolean = false

    override fun setNavigationController(controller: NavigationController) {
        navigationController = controller
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminContentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadTeacherRole()
        setupClickListeners()
    }

    /**
     * Load teacher role to determine if user is Lead Teacher
     */
    private fun loadTeacherRole() {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            updateUIForTeacherRole()
            return
        }

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("Users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    isLeadTeacher = document.getBoolean("isLeadTeacher") ?: false
                    android.util.Log.d("ContentFragment", "Is Lead Teacher: $isLeadTeacher")
                }
                updateUIForTeacherRole()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ContentFragment", "Failed to load teacher role", e)
                updateUIForTeacherRole()
            }
    }

    /**
     * Update UI based on teacher role (Lead Teacher vs Normal Teacher)
     */
    private fun updateUIForTeacherRole() {
        if (_binding == null) return
        
        // Hide Data Seeding buttons for Normal Teachers
        if (isLeadTeacher) {
            binding.btnSeedModules?.visibility = View.VISIBLE
            binding.btnSeedChallenges?.visibility = View.VISIBLE
            binding.btnSeedStudents?.visibility = View.VISIBLE
            binding.btnSeedAllData?.visibility = View.VISIBLE
        } else {
            binding.btnSeedModules?.visibility = View.GONE
            binding.btnSeedChallenges?.visibility = View.GONE
            binding.btnSeedStudents?.visibility = View.GONE
            binding.btnSeedAllData?.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        // Management Cards
        binding.cardQuizManagement?.setOnClickListener {
            navigationController?.navigateToActivity(AdminQuizManagementActivity::class.java)
        }

        binding.cardChallengeManagement?.setOnClickListener {
            navigationController?.navigateToActivity(AdminChallengeManagementActivity::class.java)
        }

        binding.cardModuleManagement?.setOnClickListener {
            navigationController?.navigateToActivity(AdminModuleManagementActivity::class.java)
        }

        // Leaderboard View Button
        binding.cardLeaderboardView?.setOnClickListener {
            navigationController?.navigateToActivity(LeaderboardActivity::class.java)
        }

        // Seed Modules Button
        binding.btnSeedModules?.setOnClickListener {
            binding.btnSeedModules?.isEnabled = false
            binding.btnSeedModules?.text = "Seeding Modules..."
            
            DataSeeder.seedModules { success, failed ->
                activity?.runOnUiThread {
                    binding.btnSeedModules?.isEnabled = true
                    binding.btnSeedModules?.text = "Seed Modules"
                    
                    Toast.makeText(
                        requireContext(),
                        "✅ Seeded $success modules, $failed failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Seed Challenges Button
        binding.btnSeedChallenges?.setOnClickListener {
            binding.btnSeedChallenges?.isEnabled = false
            binding.btnSeedChallenges?.text = "Seeding Challenges..."
            
            DataSeeder.seedChallenges { success, failed ->
                activity?.runOnUiThread {
                    binding.btnSeedChallenges?.isEnabled = true
                    binding.btnSeedChallenges?.text = "Seed Challenges"
                    
                    Toast.makeText(
                        requireContext(),
                        "✅ Seeded $success challenges, $failed failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Seed Sample Students Button
        binding.btnSeedStudents?.setOnClickListener {
            binding.btnSeedStudents?.isEnabled = false
            binding.btnSeedStudents?.text = "Seeding Students..."
            
            DataSeeder.seedSampleStudents { success, failed ->
                activity?.runOnUiThread {
                    binding.btnSeedStudents?.isEnabled = true
                    binding.btnSeedStudents?.text = "Seed Sample Students"
                    
                    Toast.makeText(
                        requireContext(),
                        "✅ Seeded $success students, $failed failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Seed All Data Button
        binding.btnSeedAllData?.setOnClickListener {
            binding.btnSeedAllData?.isEnabled = false
            binding.btnSeedAllData?.text = "Seeding All Data..."
            
            DataSeeder.seedAllData { success, failed ->
                activity?.runOnUiThread {
                    binding.btnSeedAllData?.isEnabled = true
                    binding.btnSeedAllData?.text = "Seed All Data"
                    
                    Toast.makeText(
                        requireContext(),
                        "✅ Seeded $success items total, $failed failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // School creation button removed - use web portal instead
        // Schools should be registered via school-registration-portal
        binding.btnCreateSchools?.visibility = android.view.View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
