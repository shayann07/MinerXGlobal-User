package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.models.UserPlanUi
import kotlin.math.roundToInt

class PurchasedPlansAdapter(
    private val onItemClick: (UserPlanUi) -> Unit
) : ListAdapter<UserPlanUi, PurchasedPlansAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<UserPlanUi>() {
        override fun areItemsTheSame(o: UserPlanUi, n: UserPlanUi) = o.userPlan.docId == n.userPlan.docId
        override fun areContentsTheSame(o: UserPlanUi, n: UserPlanUi) = o == n
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvName   = v.findViewById<TextView>(R.id.tvName)
        private val tvRange  = v.findViewById<TextView>(R.id.tvRange)
        private val tvDaily  = v.findViewById<TextView>(R.id.tvDaily)
        private val tvDirect = v.findViewById<TextView>(R.id.tvDirect)
        private val tvPayout = v.findViewById<TextView>(R.id.tvPayout)
        private val ivPlan   = v.findViewById<ImageView>(R.id.ivPlan)

        // Optional (present in updated layout). If not found, remains null and is ignored.
        private val chipExpired: Chip? = v.findViewById(R.id.chipExpired)

        fun bind(ui: UserPlanUi) {
            val up = ui.userPlan
            val planName = ui.planName.ifBlank { "Plan" }
            tvName.text = planName

            // ----- expired logic -----
            val isExpired = up.status.equals("expired", ignoreCase = true)
            chipExpired?.isVisible = isExpired
            // -------------------------

            val payout = up.totalPayoutAmount
            val accum  = up.totalAccumulated
            val progressPct = if (payout > 0) ((accum / payout) * 100.0).coerceIn(0.0, 100.0) else 0.0

            // Range text:
            tvRange.text = if (isExpired) {
                // expired → only show principal
                "Principal \$${up.principal.roundToInt()}"
            } else {
                // active/other → principal + progress
                "Principal \$${up.principal.roundToInt()} • \$${accum.roundToInt()} / \$${payout.roundToInt()} (${progressPct.roundToInt()}%)"
            }

            tvDaily.text  = fmtPct(up.roiPercent)
            tvDirect.text = ui.directPercent?.let { fmtPct(it) } ?: "—"
            tvPayout.text = "\$${payout.roundToInt()}"

            // Pick image based on plan name (ignore case)
            val imageRes = when (planName.trim().lowercase()) {
                "crypto forge" -> R.drawable.mining_1
                "hash power"   -> R.drawable.mining_2
                "block pulse"  -> R.drawable.mining_3
                "core miner"   -> R.drawable.mining_4
                "quantum rig"  -> R.drawable.mining_5
                else           -> R.drawable.mining_1
            }
            ivPlan.setImageResource(imageRes)

            itemView.setOnClickListener { onItemClick(ui) }
        }

        private fun fmtPct(d: Double): String =
            if (d % 1.0 == 0.0) "${d.toInt()}%" else "${d}%"
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_plan, p, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}
