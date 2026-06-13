package com.example.capstone

/**
 * Interface for fragments that need navigation capabilities
 */
interface NavigationAware {
    fun setNavigationController(controller: NavigationController)
}

/**
 * Navigation interface to handle fragment-to-fragment navigation
 * without tight coupling between fragments and activities
 */
interface NavigationController {
    fun navigateToTab(tabIndex: Int)
    fun navigateToActivity(activityClass: Class<*>)
    fun navigateBack()
    fun navigateToRoleSelection()
}

/**
 * Admin navigation implementation
 */
class AdminNavigationController(private val activity: AdminMainActivity) : NavigationController {
    
    companion object {
        const val TAB_DASHBOARD = 0
        const val TAB_STUDENTS = 1
        const val TAB_CONTENT = 2
        const val TAB_SETTINGS = 3
    }
    
    override fun navigateToTab(tabIndex: Int) {
        if (tabIndex in 0..3) {
            activity.navigateToTab(tabIndex)
        }
    }
    
    override fun navigateToActivity(activityClass: Class<*>) {
        activity.startActivity(android.content.Intent(activity, activityClass))
    }
    
    override fun navigateBack() {
        activity.onBackPressed()
    }
    
    override fun navigateToRoleSelection() {
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        val intent = android.content.Intent(activity, RoleSelectionActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
        activity.finish()
    }
}

/**
 * Student navigation implementation
 */
class StudentNavigationController(private val activity: StudentMainActivity) : NavigationController {
    
    companion object {
        const val TAB_HOME = 0
        const val TAB_LEARN = 1
        const val TAB_COMPETE = 2
        const val TAB_PROFILE = 3
    }
    
    override fun navigateToTab(tabIndex: Int) {
        if (tabIndex in 0..3) {
            activity.navigateToTab(tabIndex)
        }
    }
    
    override fun navigateToActivity(activityClass: Class<*>) {
        activity.startActivity(android.content.Intent(activity, activityClass))
    }
    
    override fun navigateBack() {
        activity.onBackPressed()
    }
    
    override fun navigateToRoleSelection() {
        // Stop all Firestore listeners BEFORE signing out to prevent PERMISSION_DENIED errors
        com.example.capstone.services.PushNotificationService.stopListening()
        activity.stopListeners()
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        val intent = android.content.Intent(activity, RoleSelectionActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
        activity.finish()
    }
}
