package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.databinding.ItemStatSmallBinding
import com.minerxgloble.minerxgloble.models.UserStatCard

class StatGridAdapter(
    private var items: List<UserStatCard> = emptyList()
) : RecyclerView.Adapter<StatGridAdapter.VH>() {

    inner class VH(val b: ItemStatSmallBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        return VH(ItemStatSmallBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items.getOrNull(position) ?: return
        holder.b.tvTitle.text = item.title
        holder.b.tvValue.text = item.value
    }

    override fun getItemCount(): Int = items.size

    fun submit(list: List<UserStatCard>) {
        items = list
        notifyDataSetChanged()
    }
}
