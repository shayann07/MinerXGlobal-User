package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.databinding.ItemTeamUserBinding
import com.minerxgloble.minerxgloble.models.TeamUser

class TeamUserAdapter : RecyclerView.Adapter<TeamUserAdapter.VH>() {

    private val data = mutableListOf<TeamUser>()

    /**
     * Submits and reorders the list so that all “active” users come first,
     * followed by “inactive” users (preserving relative order within each group).
     */
    fun submit(list: List<TeamUser>) {
        val reordered = list
            .filter { it.status == "active" } +
                list.filter { it.status != "active" }
        data.apply {
            clear()
            addAll(reordered)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(
            ItemTeamUserBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(data[position])

    override fun getItemCount() = data.size

    inner class VH(private val b: ItemTeamUserBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(u: TeamUser) = with(b) {
            userId.text = u.userId
            userName.text = u.name.ifBlank { "—" }

            // NEW: money formatting for active invested (USD)
            userActiveInvValue.text = formatMoney(u.activeInvested)

            val isActive = u.status.equals("active", ignoreCase = true)
            val colorHex = if (isActive) "#4CAF50" else "#F44336"  // green or red
            // tint the circle drawable
            statusDot.background.setTint(colorHex.toColorInt())
        }
    }
    // helper
    private fun formatMoney(v: Double): String = String.format("$%.2f", v)
}