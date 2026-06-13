package com.example.capstone.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.capstone.R
import com.google.android.material.snackbar.Snackbar

/**
 * Utility class for consistent UI components and interactions
 */
object UiUtils {
    
    /**
     * Show success Snackbar with consistent styling
     */
    fun showSuccessSnackbar(view: View, message: String, actionText: String? = null, action: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.success_green))
        snackbar.setTextColor(ContextCompat.getColor(view.context, R.color.white))
        
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
            snackbar.setActionTextColor(ContextCompat.getColor(view.context, R.color.white))
        }
        
        snackbar.show()
    }
    
    /**
     * Show error Snackbar with consistent styling
     */
    fun showErrorSnackbar(view: View, message: String, actionText: String? = null, action: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.error_red))
        snackbar.setTextColor(ContextCompat.getColor(view.context, R.color.white))
        
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
            snackbar.setActionTextColor(ContextCompat.getColor(view.context, R.color.white))
        }
        
        snackbar.show()
    }
    
    /**
     * Show warning Snackbar with consistent styling
     */
    fun showWarningSnackbar(view: View, message: String, actionText: String? = null, action: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.warning_orange))
        snackbar.setTextColor(ContextCompat.getColor(view.context, R.color.white))
        
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
            snackbar.setActionTextColor(ContextCompat.getColor(view.context, R.color.white))
        }
        
        snackbar.show()
    }
    
    /**
     * Show info Snackbar with consistent styling
     */
    fun showInfoSnackbar(view: View, message: String, actionText: String? = null, action: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, R.color.info_blue))
        snackbar.setTextColor(ContextCompat.getColor(view.context, R.color.white))
        
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
            snackbar.setActionTextColor(ContextCompat.getColor(view.context, R.color.white))
        }
        
        snackbar.show()
    }
    
    /**
     * Configure empty state view with consistent styling
     */
    fun configureEmptyState(
        container: View,
        iconView: ImageView,
        titleView: TextView,
        subtitleView: TextView,
        emptyStateType: EmptyStateType
    ) {
        container.visibility = View.VISIBLE
        
        when (emptyStateType) {
            EmptyStateType.STUDENTS -> {
                iconView.setImageResource(android.R.drawable.ic_menu_myplaces)
                titleView.text = container.context.getString(R.string.empty_state_students)
                subtitleView.text = container.context.getString(R.string.empty_state_students_subtitle)
            }
            EmptyStateType.MODULES -> {
                iconView.setImageResource(android.R.drawable.ic_menu_info_details)
                titleView.text = container.context.getString(R.string.empty_state_modules)
                subtitleView.text = container.context.getString(R.string.empty_state_modules_subtitle)
            }
            EmptyStateType.CHALLENGES -> {
                iconView.setImageResource(android.R.drawable.ic_menu_compass)
                titleView.text = container.context.getString(R.string.empty_state_challenges)
                subtitleView.text = container.context.getString(R.string.empty_state_challenges_subtitle)
            }
            EmptyStateType.QUIZZES -> {
                iconView.setImageResource(android.R.drawable.ic_menu_help)
                titleView.text = container.context.getString(R.string.empty_state_quizzes)
                subtitleView.text = container.context.getString(R.string.empty_state_quizzes_subtitle)
            }
            EmptyStateType.LEADERBOARD -> {
                iconView.setImageResource(android.R.drawable.ic_menu_sort_by_size)
                titleView.text = container.context.getString(R.string.empty_state_leaderboard)
                subtitleView.text = container.context.getString(R.string.empty_state_leaderboard_subtitle)
            }
            EmptyStateType.GENERIC -> {
                iconView.setImageResource(android.R.drawable.ic_menu_search)
                titleView.text = container.context.getString(R.string.error_no_data)
                subtitleView.text = "Please try again later"
            }
        }
        
        // Apply consistent styling
        iconView.setColorFilter(ContextCompat.getColor(container.context, R.color.text_hint))
        titleView.setTextColor(ContextCompat.getColor(container.context, R.color.text_primary))
        subtitleView.setTextColor(ContextCompat.getColor(container.context, R.color.text_secondary))
    }
    
    /**
     * Hide empty state view
     */
    fun hideEmptyState(container: View) {
        container.visibility = View.GONE
    }
    
    /**
     * Show loading state with consistent styling
     */
    fun showLoadingState(progressBar: View, contentView: View? = null) {
        progressBar.visibility = View.VISIBLE
        contentView?.visibility = View.GONE
    }
    
    /**
     * Hide loading state
     */
    fun hideLoadingState(progressBar: View, contentView: View? = null) {
        progressBar.visibility = View.GONE
        contentView?.visibility = View.VISIBLE
    }
    
    /**
     * Apply consistent card styling
     */
    fun styleCard(cardView: View, isClickable: Boolean = true) {
        if (isClickable) {
            cardView.isClickable = true
            cardView.isFocusable = true
            cardView.foreground = ContextCompat.getDrawable(cardView.context, android.R.attr.selectableItemBackground)
        }
    }
    
    /**
     * Get status color based on type
     */
    fun getStatusColor(context: Context, statusType: StatusType): Int {
        return when (statusType) {
            StatusType.SUCCESS -> ContextCompat.getColor(context, R.color.success_green)
            StatusType.ERROR -> ContextCompat.getColor(context, R.color.error_red)
            StatusType.WARNING -> ContextCompat.getColor(context, R.color.warning_orange)
            StatusType.INFO -> ContextCompat.getColor(context, R.color.info_blue)
            StatusType.ACTIVE -> ContextCompat.getColor(context, R.color.primary_green)
            StatusType.INACTIVE -> ContextCompat.getColor(context, R.color.text_hint)
        }
    }
}

/**
 * Empty state types for consistent messaging
 */
enum class EmptyStateType {
    STUDENTS,
    MODULES,
    CHALLENGES,
    QUIZZES,
    LEADERBOARD,
    GENERIC
}

/**
 * Status types for consistent coloring
 */
enum class StatusType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO,
    ACTIVE,
    INACTIVE
}
