package com.example.capstone

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone.databinding.ItemQuizQuestionBinding

class QuizAdapter(
    private val questionList: List<QuizQuestion>
) : RecyclerView.Adapter<QuizAdapter.QuizViewHolder>() {

    // Stores selected answer for each question position
    private val selectedAnswers = mutableMapOf<Int, String>()

    inner class QuizViewHolder(val binding: ItemQuizQuestionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val binding = ItemQuizQuestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuizViewHolder(binding)
    }


    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) {
        val question = questionList[position]

        holder.binding.tvQuestion.text = question.question
        holder.binding.option1.text = question.option1
        holder.binding.option2.text = question.option2
        holder.binding.option3.text = question.option3
        holder.binding.option4.text = question.option4

        // Prevent old checked states due to RecyclerView reuse
        holder.binding.radioGroupOptions.setOnCheckedChangeListener(null)
        holder.binding.radioGroupOptions.clearCheck()

        // Restore selected answer if already chosen
        when (selectedAnswers[position]) {
            question.option1 -> holder.binding.option1.isChecked = true
            question.option2 -> holder.binding.option2.isChecked = true
            question.option3 -> holder.binding.option3.isChecked = true
            question.option4 -> holder.binding.option4.isChecked = true
        }

        holder.binding.radioGroupOptions.setOnCheckedChangeListener { _, checkedId ->
            val selectedOption = when (checkedId) {
                holder.binding.option1.id -> question.option1
                holder.binding.option2.id -> question.option2
                holder.binding.option3.id -> question.option3
                holder.binding.option4.id -> question.option4
                else -> ""
            }

            if (selectedOption.isNotEmpty()) {
                selectedAnswers[position] = selectedOption
            }
        }
    }

    override fun getItemCount(): Int = questionList.size

    // Return user selected answers
    fun getSelectedAnswers(): Map<Int, String> {
        return selectedAnswers
    }

    // Check if all questions are answered
    fun allQuestionsAnswered(): Boolean {
        return selectedAnswers.size == questionList.size
    }
}
