package com.example.capstone

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.capstone.databinding.FragmentAdminSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AdminSettingsFragment : Fragment(), NavigationAware {

    private var _binding: FragmentAdminSettingsBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var navigationController: NavigationController? = null
    
    private var isLeadTeacher = false
    private var teacherSchoolId: String? = null
    private var pendingCountListener: ListenerRegistration? = null
    
    override fun setNavigationController(controller: NavigationController) {
        navigationController = controller
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadAdminProfile()
        setupClickListeners()
    }

    private fun loadAdminProfile() {
        val currentUser = auth.currentUser ?: return

        firestore.collection("Users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name") ?: "Admin"
                    val email = document.getString("email") ?: "N/A"
                    
                    binding.tvAdminName.text = name
                    binding.tvAdminEmail.text = email
                    
                    // Check if lead teacher
                    isLeadTeacher = document.getBoolean("isLeadTeacher") ?: false
                    teacherSchoolId = document.getString("schoolId")
                    
                    android.util.Log.d("SettingsFragment", "Is Lead Teacher: $isLeadTeacher")
                    
                    // Update UI based on role
                    updateUIForTeacherRole()
                    
                    if (isLeadTeacher && teacherSchoolId != null) {
                        // Show teacher approval card for lead teachers
                        binding.cardTeacherApproval.visibility = View.VISIBLE
                        // Start listening for pending teachers count
                        listenForPendingTeachers()
                    }
                }
            }
    }
    
    /**
     * Update UI based on teacher role (Lead Teacher vs Normal Teacher)
     */
    private fun updateUIForTeacherRole() {
        // Hide User Roles card for Normal Teachers
        if (isLeadTeacher) {
            binding.cardUserRoles.visibility = View.VISIBLE
        } else {
            binding.cardUserRoles.visibility = View.GONE
        }
    }
    
    /**
     * Listen for pending teachers count in real-time
     */
    private fun listenForPendingTeachers() {
        if (teacherSchoolId == null) return
        
        // Only show truly pending teachers — exclude rejected ones (isRejected = true)
        pendingCountListener = firestore.collection("Users")
            .whereEqualTo("schoolId", teacherSchoolId)
            .whereEqualTo("role", "teacher")
            .whereEqualTo("isApproved", false)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                
                if (snapshots != null) {
                    // Filter out rejected teachers — only count genuinely pending ones
                    val pendingCount = snapshots.documents.count { doc ->
                        val isRejected = doc.getBoolean("isRejected") ?: false
                        !isRejected
                    }
                    
                    if (pendingCount > 0) {
                        binding.tvPendingBadge.text = pendingCount.toString()
                        binding.tvPendingBadge.visibility = View.VISIBLE
                    } else {
                        binding.tvPendingBadge.visibility = View.GONE
                    }
                }
            }
    }

    private fun setupClickListeners() {
        binding.cardPlatformSettings.setOnClickListener {
            navigationController?.navigateToActivity(AdminPlatformSettingsActivity::class.java)
        }

        binding.cardUserRoles.setOnClickListener {
            navigationController?.navigateToActivity(AdminUserRolesActivity::class.java)
        }

        // Batch Management
        binding.cardBatchManagement.setOnClickListener {
            navigationController?.navigateToActivity(BatchManagementActivity::class.java)
        }
        
        // NEW: Teacher Approval (Lead Teachers Only)
        binding.cardTeacherApproval.setOnClickListener {
            if (isLeadTeacher) {
                navigationController?.navigateToActivity(TeacherApprovalActivity::class.java)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Only lead teachers can access this feature",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnLogout.setOnClickListener {
            // Use navigation controller for proper back stack management
            navigationController?.navigateToRoleSelection()
        }
    }
    
    // School creation function removed - use web portal instead
    // Schools should be registered via school-registration-portal

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove listener
        pendingCountListener?.remove()
        _binding = null
    }
}
