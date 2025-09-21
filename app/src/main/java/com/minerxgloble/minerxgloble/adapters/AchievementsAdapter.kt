package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.ItemAchievementBinding
import com.minerxgloble.minerxgloble.models.AchievementLevel
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class AchievementsAdapter(
    private val onCollect: (AchievementLevel) -> Unit
) : ListAdapter<AchievementLevel, AchievementsAdapter.Holder>(DIFF) {

    /* ───── US-dollar formatters (shared) ───── */
    private val usdWhole: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        currency = Currency.getInstance("USD")
        maximumFractionDigits = 0      // $1,000
    }
    private val usdFull: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        currency = Currency.getInstance("USD")    // $1,234.56 if needed
    }

    /* ───── ViewHolder ───── */
    inner class Holder(private val b: ItemAchievementBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: AchievementLevel) = with(b) {
            tvTitle.text = item.name
            tvDirectValue.text   = usdWhole.format(item.directThreshold)
            tvIndirectValue.text = usdWhole.format(item.indirectThreshold)
            imgStar.setImageResource(R.drawable.ic_star)

            btnCollected.apply {
                text = when {
                    item.isCollected -> "Collected"
                    item.isUnlocked  -> "Collect ${usdFull.format(item.salary)}"
                    else             -> "🔒"
                }
                // always clickable, just change opacity
                isEnabled = true
                alpha = if (item.isUnlocked && !item.isCollected) 1f else .6f

                setOnClickListener {
                    onCollect(item)
                }
            }
        }
    }

    /* ───── Adapter plumbing ───── */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(
            ItemAchievementBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AchievementLevel>() {
            override fun areItemsTheSame(a: AchievementLevel, b: AchievementLevel) =
                a.index == b.index

            override fun areContentsTheSame(a: AchievementLevel, b: AchievementLevel) = a == b
        }
    }
}
