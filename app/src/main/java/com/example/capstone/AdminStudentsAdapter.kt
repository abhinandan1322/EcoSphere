package com.example.capstone

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.capstone.databinding.ItemAdminStudentBinding

class AdminStudentsAdapter(
    private var students: List<StudentData>,
    private val onStudentClick: (StudentData) -> Unit,
    private val onDeleteClick: (StudentData) -> Unit
) : RecyclerView.Adapter<AdminStudentsAdapter.StudentViewHolder>() {

    private var filteredStudents = students.toList()

    class StudentViewHolder(val binding: ItemAdminStudentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val binding = ItemAdminStudentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StudentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = filteredStudents[position]
        
        with(holder.binding) {
            tvStudentName.text = student.name
            tvStudentEmail.text = student.email
            tvEcoPoints.text = "${student.ecoPoints} pts"
            
            // Calculate level using centralized LevelCalculator
            val levelInfo = com.example.capstone.utils.LevelCalculator.getLevelInfo(student.ecoPoints.toInt())
            tvLevel.text = "Lvl ${levelInfo.level} ${levelInfo.emoji}"
            
            // Load profile picture
            if (student.profilePictureUrl.isNotEmpty()) {
                Glide.with(ivProfilePicture.context)
                    .load(student.profilePictureUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_myplaces)
                    .error(android.R.drawable.ic_menu_myplaces)
                    .into(ivProfilePicture)
            } else {
                ivProfilePicture.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
            
            // Set click listeners
            root.setOnClickListener {
                onStudentClick(student)
            }
            
            btnDelete.setOnClickListener {
                onDeleteClick(student)
            }
        }
    }

    override fun getItemCount(): Int = filteredStudents.size

    fun updateList(newList: List<StudentData>) {
        filteredStudents = newList
        notifyDataSetChanged()
    }
}
