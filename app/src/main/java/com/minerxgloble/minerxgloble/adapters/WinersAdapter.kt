package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.models.Winner

import java.text.SimpleDateFormat
import java.util.*

class WinnersAdapter(
    private var items: List<Winner> = emptyList()
) : RecyclerView.Adapter<WinnersAdapter.VH>() {

    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun submit(list: List<Winner>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvWeek: TextView = v.findViewById(R.id.tvWeek)
        val tvPrize: TextView = v.findViewById(R.id.tvPrize)
        val tvUserId: TextView = v.findViewById(R.id.tvUserId)
        val ivMedal: ImageView = v.findViewById(R.id.ivMedal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_winner, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val w = items[pos]
        h.tvName.text = w.displayName.ifBlank { w.userId.take(6) }

        val start = w.weekStartMillis?.let { df.format(Date(it)) } ?: "—"
        val end   = w.weekEndMillis?.let { df.format(Date(it)) } ?: "—"
        h.tvWeek.text = "Week: $start → $end"

        h.tvPrize.text = if (w.prizeUsd > 0) "$${w.prizeUsd}" else "$—"
        h.tvUserId.text = "ID: ${w.userId}"

        // Icon/tint is set in XML; you can switch icons by rank here if needed
    }

    override fun getItemCount(): Int = items.size
}
