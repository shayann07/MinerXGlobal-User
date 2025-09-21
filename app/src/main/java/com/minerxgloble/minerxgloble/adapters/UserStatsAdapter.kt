package com.minerxgloble.minerxgloble.adapters

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.models.UserStatCard
import kotlin.math.roundToInt

class UserStatsAdapter(
    private var items: List<UserStatCard>
) : RecyclerView.Adapter<UserStatsAdapter.StatsViewHolder>() {

    private val placeholder = UserStatCard("—", "—")

    private fun normalized(): List<UserStatCard> {
        // Always show 4 cards; fill missing with placeholders
        val list = items.toMutableList()
        while (list.size < 4) list.add(placeholder)
        return list.take(4)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_team_stats, parent, false)
        return StatsViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatsViewHolder, position: Int) {
        val item = normalized()[position]
        holder.title.text = item.title

        when (item.title) {
            // Integer animation (Tokens)
            "Tokens" -> {
                val newVal = item.value.toIntOrNull() ?: 0
                val oldVal = (holder.value.tag as? Int) ?: newVal
                if (oldVal == newVal) {
                    holder.value.text = newVal.toString()
                } else {
                    animateInt(holder.value, oldVal, newVal) { it.toString() }
                }
                holder.value.tag = newVal
            }

            // Money animation (Balance / Team Invested)
            "Balance", "Team Invested" -> {
                val newVal = parseNumber(item.value)
                val oldVal = (holder.value.tag as? Double) ?: newVal
                val prefix = item.value.takeWhile { !it.isDigit() && it != '-' } // e.g. "$"
                val decimals = 2

                if (oldVal == newVal) {
                    holder.value.text = formatMoney(prefix, newVal, decimals)
                } else {
                    animateDouble(holder.value, oldVal, newVal) {
                        formatMoney(prefix, it, decimals)
                    }
                }
                holder.value.tag = newVal
            }

            // Text (Team Size: "Direct: X | Indirect: Y") — no animation
            else -> {
                holder.value.text = item.value
                holder.value.tag = null
            }
        }
    }

    override fun getItemCount(): Int = 4

    fun submitList(newItems: List<UserStatCard>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class StatsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvTitle)
        val value: TextView = itemView.findViewById(R.id.value)
    }

    // ---------- Helpers ----------

    private fun animateInt(
        tv: TextView,
        from: Int,
        to: Int,
        format: (Int) -> String
    ) {
        val anim = ValueAnimator.ofInt(from, to).setDuration(600)
        anim.addUpdateListener { tv.text = format(it.animatedValue as Int) }
        anim.start()
    }

    private fun animateDouble(
        tv: TextView,
        from: Double,
        to: Double,
        format: (Double) -> String
    ) {
        val anim = ValueAnimator.ofFloat(from.toFloat(), to.toFloat()).setDuration(700)
        anim.addUpdateListener { tv.text = format((it.animatedValue as Float).toDouble()) }
        anim.start()
    }

    private fun parseNumber(s: String): Double {
        // strip everything except digits, decimal point, and minus sign
        return s.replace(Regex("[^\\d.-]"), "").toDoubleOrNull() ?: 0.0
    }

    private fun formatMoney(prefix: String, value: Double, decimals: Int): String {
        return prefix + "%,.${decimals}f".format(value)
    }
}
