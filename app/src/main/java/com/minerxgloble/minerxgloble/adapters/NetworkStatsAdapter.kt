// NetworkStatsAdapter.kt
package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.databinding.ItemNetworkStatsBinding
import com.minerxgloble.minerxgloble.models.NetworkStat

class NetworkStatsAdapter(
    private var items: List<NetworkStat>
) : RecyclerView.Adapter<NetworkStatsAdapter.StatVH>() {

    private val placeholder = NetworkStat(
        value = "—",
        desc = "No network stats yet"
    )

    inner class StatVH(val binding: ItemNetworkStatsBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatVH {
        val binding = ItemNetworkStatsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StatVH(binding)
    }

    override fun onBindViewHolder(holder: StatVH, position: Int) {
        val item = if (items.isEmpty()) placeholder else items[position]
        holder.binding.value.text = item.value
        holder.binding.descTv.text = item.desc
    }

    override fun getItemCount(): Int = if (items.isEmpty()) 1 else items.size

    fun submitList(newItems: List<NetworkStat>) {
        items = newItems
        notifyDataSetChanged()
    }
}
