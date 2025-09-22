package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.ItemStatSmallBinding
import com.minerxgloble.minerxgloble.models.UserStatCard

class StatGridAdapter(
    private var items: List<UserStatCard> = emptyList()
) : RecyclerView.Adapter<StatGridAdapter.VH>() {

    inner class VH(val b: ItemStatSmallBinding) : RecyclerView.ViewHolder(b.root)

    // Title → icon map (case-insensitive). Change drawables to yours.
    private val iconMap: Map<String, Int> = mapOf(
        "balance"        to R.drawable.ic_wallet,
        "mxgn tokens"    to R.drawable.ic_chip,
        "team size"      to R.drawable.ic_team_hollow,
        "team invested"  to R.drawable.ic_money
    )

    @DrawableRes
    private val defaultIcon: Int = R.drawable.ic_money

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        return VH(ItemStatSmallBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items.getOrNull(position) ?: return
        with(holder.b) {
            tvTitle.text = item.title
            tvValue.text = item.value

            // Resolve icon by normalized title
            val key = item.title.trim().lowercase()
            val iconRes = iconMap[key] ?: defaultIcon
            icon.setImageResource(iconRes)
            val orangeTint = ResourcesCompat.getColorStateList(
                root.resources, R.color.orange, root.context.theme
            )
            icon.imageTintList = orangeTint
        }
    }

    override fun getItemCount(): Int = items.size

    fun submit(list: List<UserStatCard>) {
        items = list
        notifyDataSetChanged()
    }
}
