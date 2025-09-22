package com.minerxgloble.minerxgloble.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.ItemNetworkSmallBinding
import com.minerxgloble.minerxgloble.models.NetworkStat
import java.util.Locale
class NetworkMiniAdapter(
    private var items: MutableList<NetworkStat> = mutableListOf()
) : RecyclerView.Adapter<NetworkMiniAdapter.VH>() {

    inner class VH(val b: ItemNetworkSmallBinding) : RecyclerView.ViewHolder(b.root)

    // keep last 3 stats + optional token rate
    private var lastStats: List<NetworkStat> = emptyList()
    private var currentTokenRate: Double? = null

    @DrawableRes private val defaultIcon: Int = R.drawable.ic_money

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        return VH(ItemNetworkSmallBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items.getOrNull(position) ?: return
        with(holder.b) {
            tvDesc.text = item.desc
            tvValue.text = item.value

            val iconRes = getIconFor(item.desc)
            icon.setImageResource(iconRes)
            icon.contentDescription = item.desc

            val cs = ResourcesCompat.getColorStateList(
                root.resources, R.color.orange, root.context.theme
            ) ?: ColorStateList.valueOf(
                ContextCompat.getColor(root.context, R.color.orange)
            )
            icon.imageTintList = cs
        }
    }

    override fun getItemCount(): Int = items.size

    // called by your VM observer (3 items)
    fun submit(list: List<NetworkStat>) {
        lastStats = list ?: emptyList()
        rebuild()
    }

    // called by your token-rate observer
    fun setTokenRate(rate: Double) {
        if (currentTokenRate != rate) {
            currentTokenRate = rate
            rebuild()
        }
    }

    private fun rebuild() {
        val merged = ArrayList<NetworkStat>(lastStats.size + 1)
        merged.addAll(lastStats)

        currentTokenRate?.let { r ->
            val pretty = if (r % 1.0 == 0.0) r.toInt().toString()
            else String.format(Locale.US, "%.2f", r)
            merged.add(NetworkStat(desc = "MXGN Rate", value = pretty))
        }

        items.clear()
        items.addAll(merged)
        notifyDataSetChanged()
    }

    @DrawableRes
    private fun getIconFor(label: String): Int {
        val key = label.trim().lowercase(Locale.ROOT)
        return when {
            "member" in key           -> R.drawable.ic_team_hollow
            "withdraw" in key         -> R.drawable.ic_withdraw
            "invest" in key           -> R.drawable.ic_deposit
            "token" in key || "rate" in key || "price" in key || "mxg" in key
                -> R.drawable.ic_chip /* fallback to ic_deposit if missing */
            else                       -> defaultIcon
        }
    }
}

