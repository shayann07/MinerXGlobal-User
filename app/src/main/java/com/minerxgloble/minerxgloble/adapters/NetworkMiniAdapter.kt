package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.databinding.ItemNetworkSmallBinding
import com.minerxgloble.minerxgloble.models.NetworkStat

class NetworkMiniAdapter(
    private var items: List<NetworkStat> = emptyList()
) : RecyclerView.Adapter<NetworkMiniAdapter.VH>() {

    inner class VH(val b: ItemNetworkSmallBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        return VH(ItemNetworkSmallBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items.getOrNull(position) ?: return
        holder.b.tvDesc.text = it.desc
        holder.b.tvValue.text = it.value
    }

    override fun getItemCount(): Int = items.size

    fun submit(list: List<NetworkStat>) {
        items = list
        notifyDataSetChanged()
    }
}
