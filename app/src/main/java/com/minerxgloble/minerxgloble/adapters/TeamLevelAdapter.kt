package com.minerxgloble.minerxgloble.adapters

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.databinding.ItemTeamLevelBinding
import com.minerxgloble.minerxgloble.models.TeamLevel
import java.util.Locale
import kotlin.math.floor

class TeamLevelAdapter(
    private val onClick: (TeamLevel) -> Unit = {}
) : ListAdapter<TeamLevel, TeamLevelAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTeamLevelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position), onClick)

    class VH(private val b: ItemTeamLevelBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: TeamLevel, onClick: (TeamLevel) -> Unit) = with(b) {
            levelTitle.text    = "Level ${item.level}"
            totalMembers.text  = "Total Member : ${item.totalUsers}"
            activeMembers.text = "Active Member : ${item.activeUsers}"

            val raw = item.investedAmount
            val truncated = floor(raw * 100) / 100
            val show = if (truncated == truncated.toLong().toDouble())
                "%,d".format(Locale.getDefault(), truncated.toLong())
            else
                String.format(Locale.getDefault(), "%,.2f", truncated)
            investedAmount.text = "Invested Amount : $${show}"

            // Visual state: blur when LOCKED, clear when UNLOCKED
            val isLocked = !item.levelUnlocked
            applyLockedVisuals(root, isLocked)

            // Click only when unlocked
            root.isEnabled = !isLocked
            root.setOnClickListener {
                if (!isLocked) onClick(item)
            }
        }

        private fun applyLockedVisuals(target: View, locked: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // True blur on Android 12+
                target.setRenderEffect(
                    if (locked) RenderEffect.createBlurEffect(16f, 16f, Shader.TileMode.CLAMP)
                    else null
                )
                // Slight dim to emphasize state
                target.alpha = if (locked) 0.9f else 1f
            } else {
                // Fallback: desaturate + dim (fast + compatible)
                val cm = ColorMatrix().apply { setSaturation(if (locked) 0f else 1f) }
                // Apply to the whole card view hierarchy via layer paint
                target.alpha = if (locked) 0.7f else 1f
                target.setLayerType(
                    if (locked) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
                    if (locked) android.graphics.Paint().apply {
                        colorFilter = ColorMatrixColorFilter(cm)
                    } else null
                )
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<TeamLevel>() {
        override fun areItemsTheSame(o: TeamLevel, n: TeamLevel) = o.level == n.level
        override fun areContentsTheSame(o: TeamLevel, n: TeamLevel) = o == n
    }
}
