package com.example.capstone

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.capstone.databinding.ActivityAdminMainBinding

class AdminMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminMainBinding
    private lateinit var navigationController: AdminNavigationController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navigationController = AdminNavigationController(this)
        setupViewPager()
        setupBottomNavigation()
        
        // School creation removed - use web portal instead
        // Schools should be registered via school-registration-portal
    }
    
    fun navigateToTab(tabIndex: Int) {
        binding.viewPager.currentItem = tabIndex
    }
    
    fun getNavigationController(): NavigationController = navigationController

    private fun setupViewPager() {
        val fragments = listOf(
            AdminDashboardFragment(),
            AdminStudentsFragment(),
            AdminContentFragment(),
            AdminSettingsFragment()
        )

        val adapter = ViewPagerAdapter(this, fragments)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = true // Enable swipe

        // Sync ViewPager with BottomNavigation and set navigation controller
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.bottomNavigation.menu.getItem(position).isChecked = true
                
                // Set navigation controller for the current fragment
                setNavigationControllerForCurrentFragment(position)
            }
        })
        
        // Set navigation controller for the initial fragment (position 0)
        binding.viewPager.post {
            setNavigationControllerForCurrentFragment(0)
        }
    }
    
    private fun setNavigationControllerForCurrentFragment(position: Int) {
        // Use a more reliable way to get the current fragment
        val fragmentTag = "f$position"
        val currentFragment = supportFragmentManager.findFragmentByTag(fragmentTag)
        if (currentFragment is NavigationAware) {
            currentFragment.setNavigationController(navigationController)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_admin_dashboard -> {
                    binding.viewPager.currentItem = 0
                    true
                }
                R.id.nav_admin_students -> {
                    binding.viewPager.currentItem = 1
                    true
                }
                R.id.nav_admin_content -> {
                    binding.viewPager.currentItem = 2
                    true
                }
                R.id.nav_admin_settings -> {
                    binding.viewPager.currentItem = 3
                    true
                }
                else -> false
            }
        }
    }
    
    override fun onBackPressed() {
        // Handle back button properly - show exit confirmation
        showExitConfirmation()
    }
    
    private fun showExitConfirmation() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Exit EcoSphere")
            .setMessage("Are you sure you want to exit the app?")
            .setPositiveButton("Exit") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
