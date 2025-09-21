package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.databinding.ItemTeamLevelBinding
import com.minerxgloble.minerxgloble.models.TeamLevel
import java.util.Locale
import kotlin.math.floor

class TeamLevelAdapter(
    private val onClick: (TeamLevel) -> Unit = {}
) : ListAdapter<TeamLevel, TeamLevelAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTeamLevelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position), onClick)

    class VH(private val b: ItemTeamLevelBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: TeamLevel, onClick: (TeamLevel) -> Unit) = with(b) {
            levelTitle.text   = "Level ${item.level}"
            totalMembers.text = "Total Member : ${item.totalUsers}"
            activeMembers.text= "Active Member : ${item.activeUsers}"

            val raw = item.investedAmount
            val truncated = floor(raw * 100) / 100
            val show = if (truncated == truncated.toLong().toDouble())
                "%,d".format(Locale.getDefault(), truncated.toLong())
            else
                String.format(Locale.getDefault(), "%,.2f", truncated)
            investedAmount.text = "Invested Amount : $${show}"

            root.alpha = if (item.levelUnlocked) 1f else 0.4f
            root.setOnClickListener { onClick(item) }
        }
    }

    class Diff : DiffUtil.ItemCallback<TeamLevel>() {
        override fun areItemsTheSame(o: TeamLevel, n: TeamLevel) = o.level == n.level
        override fun areContentsTheSame(o: TeamLevel, n: TeamLevel) = o == n
    }
}
