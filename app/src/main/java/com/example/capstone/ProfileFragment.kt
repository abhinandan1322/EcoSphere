package com.example.capstone

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.capstone.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment(), NavigationAware {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var navigationController: NavigationController? = null
    
    override fun setNavigationController(controller: NavigationController) {
        navigationController = controller
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadUserProfile()
        setupClickListeners()
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser ?: return

        firestore.collection("Users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name") ?: "User"
                    val email = document.getString("email") ?: "N/A"
                    val role = document.getString("role") ?: "student"
                    val phone = document.getString("phone") ?: "Not provided"
                    val dob = document.getString("dob") ?: "Not provided"
                    val gender = document.getString("gender") ?: "Not provided"
                    val profilePictureUrl = document.getString("profilePictureUrl") ?: ""
                    val ecoPoints = document.getLong("ecoPoints") ?: 0
                    val batchId = document.getString("batchId")

                    binding.tvProfileName.text = name
                    binding.tvProfileRole.text = role.replaceFirstChar { it.uppercase() }
                    binding.tvEmail.text = email
                    binding.tvPhone.text = phone
                    binding.tvDob.text = dob
                    binding.tvGender.text = gender
                    binding.tvEcoPoints.text = ecoPoints.toString()

                    // Show level number using LevelCalculator
                    val levelInfo = com.example.capstone.utils.LevelCalculator.getLevelInfo(ecoPoints.toInt())
                    binding.tvLevel.text = "${levelInfo.level}"

                    // Load batch name if batchId exists
                    if (!batchId.isNullOrEmpty()) {
                        loadBatchName(batchId)
                    } else {
                        binding.tvBatch.text = "Not assigned"
                    }

                    // Load profile picture
                    if (profilePictureUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(profilePictureUrl)
                            .circleCrop()
                            .placeholder(android.R.drawable.ic_menu_myplaces)
                            .error(android.R.drawable.ic_menu_myplaces)
                            .into(binding.ivProfilePicture)
                    }
                }
            }
    }

    private fun loadBatchName(batchId: String) {
        firestore.collection("Batches")
            .document(batchId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val batchName = document.getString("batchName") ?: "Unknown Batch"
                    binding.tvBatch.text = batchName
                } else {
                    binding.tvBatch.text = "Not assigned"
                }
            }
            .addOnFailureListener {
                binding.tvBatch.text = "Not assigned"
            }
    }

    private fun calculateLevel(ecoPoints: Int): Int {
        return com.example.capstone.utils.LevelCalculator.getLevel(ecoPoints)
    }

    private fun setupClickListeners() {
        binding.cardMySubmissions.setOnClickListener {
            navigationController?.navigateToActivity(MySubmissionsActivity::class.java)
        }
        
        binding.cardSettings.setOnClickListener {
            navigationController?.navigateToActivity(SettingsActivity::class.java)
        }

        binding.cardAbout.setOnClickListener {
            navigationController?.navigateToActivity(AboutActivity::class.java)
        }

        binding.cardHelp.setOnClickListener {
            navigationController?.navigateToActivity(HelpFaqActivity::class.java)
        }

        binding.cardContact.setOnClickListener {
            navigationController?.navigateToActivity(ContactActivity::class.java)
        }

        binding.btnLogout.setOnClickListener {
            // Use navigation controller for proper back stack management
            navigationController?.navigateToRoleSelection()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh profile data when returning to tab — ensures EcoPoints and level are current
        loadUserProfile()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
