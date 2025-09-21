
package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.databinding.ItemAvailablePlans1Binding
import com.minerxgloble.minerxgloble.databinding.ItemAvailablePlans2Binding
import com.minerxgloble.minerxgloble.models.PlanModel

class AvailablePlansAdapter(
    private val plans: List<PlanModel>,
    private val onPlanClick: (PlanModel) -> Unit
) : RecyclerView.Adapter<AvailablePlansAdapter.PlanViewHolder>() {

    override fun getItemViewType(position: Int): Int = position % 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            val binding = ItemAvailablePlans1Binding.inflate(inflater, parent, false)
            PlanViewHolder.TypeOneViewHolder(binding)
        } else {
            val binding = ItemAvailablePlans2Binding.inflate(inflater, parent, false)
            PlanViewHolder.TypeTwoViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        holder.bind(plans[position], onPlanClick)
    }

    override fun getItemCount(): Int = plans.size

    sealed class PlanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(plan: PlanModel, onClick: (PlanModel) -> Unit)

        class TypeOneViewHolder(
            private val binding: ItemAvailablePlans1Binding
        ) : PlanViewHolder(binding.root) {
            override fun bind(plan: PlanModel, onClick: (PlanModel) -> Unit) {
              /*  binding.planTitle.text   = plan.name
                binding.amountValue.text = "$${plan.minInvestment}"
                binding.roiValue.text    = "${plan.percentage}%"
                binding.planDays.text    = "${plan.durationDays}d"
                binding.startLayout.visibility = View.GONE
                if (plan.bonusPercentage > 0) {
                    binding.bonusLayout.visibility = View.VISIBLE
                    binding.bonusText.text = "Bonus: ${plan.bonusPercentage}%"
                }*/
                binding.root.setOnClickListener { onClick(plan) }
            }
        }

        class TypeTwoViewHolder(
            private val binding: ItemAvailablePlans2Binding
        ) : PlanViewHolder(binding.root) {
            override fun bind(plan: PlanModel, onClick: (PlanModel) -> Unit) {
               /* binding.planTitle.text   = plan.name
                binding.amountValue.text = "$${plan.minInvestment}"
                binding.roiValue.text    = "${plan.percentage}%"
                binding.planDays.text    = "${plan.durationDays}d"
                binding.startDateLayout.visibility = View.GONE
                if (plan.bonusPercentage > 0) {
                    binding.bonusLayout.visibility = View.VISIBLE
                    binding.bonusText.text = "Bonus: ${plan.bonusPercentage}%"
                }
                binding.root.setOnClickListener { onClick(plan) }*/
            }
        }
    }
}
