package com.example.capstone

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone.databinding.ItemLeaderboardUserBinding
import com.google.android.material.card.MaterialCardView

class LeaderboardAdapter(
    private var items: List<LeaderboardUser>
) : RecyclerView.Adapter<LeaderboardAdapter.VH>() {

    inner class VH(val binding: ItemLeaderboardUserBinding) :
        RecyclerView.ViewHolder(binding.root)

    fun setData(newItems: List<LeaderboardUser>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLeaderboardUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val user = items[position]
        val rank = position + 1

        // Set rank with medals for top 3
        when (rank) {
            1 -> {
                holder.binding.tvRank.visibility = View.GONE
                holder.binding.tvMedal.visibility = View.VISIBLE
                holder.binding.tvMedal.text = "🥇"
            }
            2 -> {
                holder.binding.tvRank.visibility = View.GONE
                holder.binding.tvMedal.visibility = View.VISIBLE
                holder.binding.tvMedal.text = "🥈"
            }
            3 -> {
                holder.binding.tvRank.visibility = View.GONE
                holder.binding.tvMedal.visibility = View.VISIBLE
                holder.binding.tvMedal.text = "🥉"
            }
            else -> {
                holder.binding.tvRank.visibility = View.VISIBLE
                holder.binding.tvMedal.visibility = View.GONE
                holder.binding.tvRank.text = rank.toString()
            }
        }
        
        holder.binding.tvName.text = if (user.name.isBlank()) "User" else user.name
        holder.binding.tvPoints.text = user.ecoPoints.toString()

        // Show level title in the subtitle label using LevelCalculator
        val levelInfo = com.example.capstone.utils.LevelCalculator.getLevelInfo(user.ecoPoints.toInt())
        holder.binding.tvPointsLabel.text = "${levelInfo.emoji} Lvl ${levelInfo.level} · ${levelInfo.title}"

        // ✅ Highlight Top 3 with better styling
        val card = holder.binding.root as MaterialCardView
        when (rank) {
            1 -> {
                card.setCardBackgroundColor(Color.parseColor("#FFF8E1")) // Gold-ish
                card.strokeColor = Color.parseColor("#FFD700")
                card.strokeWidth = 4
            }
            2 -> {
                card.setCardBackgroundColor(Color.parseColor("#F5F5F5")) // Silver-ish
                card.strokeColor = Color.parseColor("#C0C0C0")
                card.strokeWidth = 4
            }
            3 -> {
                card.setCardBackgroundColor(Color.parseColor("#FFF3E0")) // Bronze-ish
                card.strokeColor = Color.parseColor("#FF9800")
                card.strokeWidth = 4
            }
            else -> {
                card.setCardBackgroundColor(Color.WHITE)
                card.strokeColor = Color.parseColor("#E0E0E0")
                card.strokeWidth = 0
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
