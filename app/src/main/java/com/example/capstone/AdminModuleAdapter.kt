package com.example.capstone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdminModuleAdapter(
    private val modules: List<AdminModule>,
    private val onEditClick: (AdminModule) -> Unit,
    private val onDeleteClick: (AdminModule) -> Unit,
    private val onViewContentClick: (AdminModule) -> Unit,
    private val onManageQuestionsClick: (AdminModule) -> Unit
) : RecyclerView.Adapter<AdminModuleAdapter.ModuleViewHolder>() {

    class ModuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOrder: TextView = itemView.findViewById(R.id.tvOrder)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val tvPoints: TextView = itemView.findViewById(R.id.tvPoints)
        val tvQuestionCount: TextView = itemView.findViewById(R.id.tvQuestionCount)
        val tvContentPreview: TextView = itemView.findViewById(R.id.tvContentPreview)
        val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
        val btnViewContent: Button = itemView.findViewById(R.id.btnViewContent)
        val btnManageQuestions: Button = itemView.findViewById(R.id.btnManageQuestions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_module, parent, false)
        return ModuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        val module = modules[position]

        holder.tvOrder.text = "#${module.order}"
        holder.tvTitle.text = module.title
        holder.tvDescription.text = module.description
        holder.tvPoints.text = "${module.points} pts"
        holder.tvQuestionCount.text = "${module.questionCount} questions"
        
        // Show content preview (first 100 characters)
        val contentPreview = if (module.content.length > 100) {
            "${module.content.take(100)}..."
        } else {
            module.content
        }
        holder.tvContentPreview.text = contentPreview

        // Set order badge color based on position
        val orderColor = when {
            module.order <= 2 -> holder.itemView.context.getColor(android.R.color.holo_green_dark)
            module.order <= 4 -> holder.itemView.context.getColor(android.R.color.holo_orange_dark)
            else -> holder.itemView.context.getColor(android.R.color.holo_blue_dark)
        }
        holder.tvOrder.setTextColor(orderColor)

        // Set points color based on value
        val pointsColor = when {
            module.points >= 30 -> holder.itemView.context.getColor(android.R.color.holo_red_dark)
            module.points >= 20 -> holder.itemView.context.getColor(android.R.color.holo_orange_dark)
            else -> holder.itemView.context.getColor(android.R.color.holo_green_dark)
        }
        holder.tvPoints.setTextColor(pointsColor)

        // Set question count color
        val questionColor = if (module.questionCount >= 5) {
            holder.itemView.context.getColor(android.R.color.holo_green_dark)
        } else {
            holder.itemView.context.getColor(android.R.color.holo_orange_dark)
        }
        holder.tvQuestionCount.setTextColor(questionColor)

        holder.btnEdit.setOnClickListener {
            onEditClick(module)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(module)
        }

        holder.btnViewContent.setOnClickListener {
            onViewContentClick(module)
        }

        holder.btnManageQuestions.setOnClickListener {
            onManageQuestionsClick(module)
        }
    }

    override fun getItemCount(): Int = modules.size
}
