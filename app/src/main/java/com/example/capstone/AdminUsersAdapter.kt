package com.example.capstone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdminUsersAdapter(
    private val adminUsers: List<AdminUser>,
    private val onEditClick: (AdminUser) -> Unit,
    private val onDeleteClick: (AdminUser) -> Unit,
    private val onToggleStatusClick: (AdminUser) -> Unit,
    private val onViewActivityClick: (AdminUser) -> Unit
) : RecyclerView.Adapter<AdminUsersAdapter.AdminUserViewHolder>() {

    class AdminUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        val tvRole: TextView = itemView.findViewById(R.id.tvRole)
        val tvDepartment: TextView = itemView.findViewById(R.id.tvDepartment)
        val tvLastLogin: TextView = itemView.findViewById(R.id.tvLastLogin)
        val tvLoginCount: TextView = itemView.findViewById(R.id.tvLoginCount)
        val switchActive: Switch = itemView.findViewById(R.id.switchActive)
        val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
        val btnViewActivity: Button = itemView.findViewById(R.id.btnViewActivity)
        val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        val roleIcon: TextView = itemView.findViewById(R.id.roleIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_user, parent, false)
        return AdminUserViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdminUserViewHolder, position: Int) {
        val adminUser = adminUsers[position]

        holder.tvName.text = adminUser.name
        holder.tvEmail.text = adminUser.email
        holder.tvDepartment.text = adminUser.department.ifEmpty { "No Department" }
        holder.tvLastLogin.text = "Last: ${adminUser.lastLogin}"
        holder.tvLoginCount.text = "${adminUser.loginCount} logins"

        // Set role display and icon
        when (adminUser.role) {
            "super_admin" -> {
                holder.tvRole.text = "SUPER ADMIN"
                holder.roleIcon.text = "👑"
                holder.tvRole.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
            }
            "content_admin" -> {
                holder.tvRole.text = "CONTENT ADMIN"
                holder.roleIcon.text = "🎨"
                holder.tvRole.setTextColor(holder.itemView.context.getColor(android.R.color.holo_blue_dark))
            }
            "student_manager" -> {
                holder.tvRole.text = "STUDENT MANAGER"
                holder.roleIcon.text = "👥"
                holder.tvRole.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
            }
            "analyst" -> {
                holder.tvRole.text = "ANALYST"
                holder.roleIcon.text = "📊"
                holder.tvRole.setTextColor(holder.itemView.context.getColor(android.R.color.holo_orange_dark))
            }
            else -> {
                holder.tvRole.text = "UNKNOWN ROLE"
                holder.roleIcon.text = "❓"
                holder.tvRole.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
            }
        }

        // Set status indicator and switch
        holder.switchActive.isChecked = adminUser.active
        val statusColor = if (adminUser.active) {
            holder.itemView.context.getColor(android.R.color.holo_green_dark)
        } else {
            holder.itemView.context.getColor(android.R.color.holo_red_dark)
        }
        holder.statusIndicator.setBackgroundColor(statusColor)

        // Set card background based on status
        val cardBackground = if (adminUser.active) {
            holder.itemView.context.getColor(android.R.color.white)
        } else {
            holder.itemView.context.getColor(android.R.color.darker_gray)
        }
        holder.itemView.setBackgroundColor(cardBackground)

        // Disable switch listener temporarily to avoid triggering during bind
        holder.switchActive.setOnCheckedChangeListener(null)
        holder.switchActive.isChecked = adminUser.active
        
        // Set switch listener
        holder.switchActive.setOnCheckedChangeListener { _, _ ->
            onToggleStatusClick(adminUser)
        }

        holder.btnEdit.setOnClickListener {
            onEditClick(adminUser)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(adminUser)
        }

        holder.btnViewActivity.setOnClickListener {
            onViewActivityClick(adminUser)
        }
    }

    override fun getItemCount(): Int = adminUsers.size
}
