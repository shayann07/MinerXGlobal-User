package com.minerxgloble.minerxgloble.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.models.RankItemState
import com.minerxgloble.minerxgloble.models.RankUiStatus

class RankAdapter(
    private val onOpenDialog: (RankItemState) -> Unit
) : ListAdapter<RankItemState, RankAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<RankItemState>() {
        override fun areItemsTheSame(o: RankItemState, n: RankItemState) = o.def.id == n.def.id
        override fun areContentsTheSame(o: RankItemState, n: RankItemState) = o == n
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.rankTitle)
        val teamBiz: TextView = v.findViewById(R.id.teamBusiness)
        val directBiz: TextView = v.findViewById(R.id.directBusiness)
        val reward: TextView = v.findViewById(R.id.rewardAmount)
        val chip: Chip = v.findViewById(R.id.statusChip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_rank, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = getItem(pos)
        h.title.text = item.def.title
        h.teamBiz.text = "Team Business Req: ${fmt(item.def.indirectRequired)}$"
        h.directBiz.text = "Direct Business Req: ${fmt(item.def.directRequired)}$"
        h.reward.text = "Reward: ${fmt(item.def.reward)}$"

        // Chip text + tint
        when (item.status) {
            RankUiStatus.CLAIMED -> {
                setChip(h.chip, "Claimed", Color.parseColor("#22C55E")) // green
            }
            RankUiStatus.CLAIMABLE -> {
                setChip(h.chip, "Claim", Color.parseColor("#FD9409")) // your accent orange
            }
            RankUiStatus.LOCKED -> {
                setChip(h.chip, "Locked", Color.parseColor("#6B7280")) // gray
            }
        }

        h.itemView.setOnClickListener { onOpenDialog(item) }
        // Ensure the chip doesn't hijack the row click:
        h.chip.isCheckable = false
        h.chip.isFocusable = false
        h.chip.setOnClickListener { onOpenDialog(item) } // or h.itemView.performClick()
    }

    private fun setChip(chip: Chip, text: String, bgColor: Int) {
        chip.text = text
        chip.chipBackgroundColor = ColorStateList.valueOf(bgColor)
        chip.setTextColor(Color.WHITE)
    }

    private fun fmt(d: Double): String {
        val n = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
        return n
    }
}
