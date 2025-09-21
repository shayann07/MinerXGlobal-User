package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.models.TransactionModel
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionAdapter(
    private var items: List<TransactionModel>, private val onClick: (TransactionModel) -> Unit = {}
) : RecyclerView.Adapter<TransactionAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvType: TextView = itemView.findViewById(R.id.tvType)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val ivIcon: ImageView? = itemView.findViewById(R.id.ivIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v =
            LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val txn = items[position]

        val rawType = txn.type.orEmpty()
        val labelType = prettyType(rawType)

        val isWithdraw = rawType.equals("withdraw", ignoreCase = true)
        val isDeposit = rawType.equals("deposit", ignoreCase = true)
        val isPlanPurchase = isPlanPurchase(rawType)
        val isLuckyDraw=rawType.equals("Lucky Draw Investment", ignoreCase = true)



        // Title (pretty label)
        holder.tvType.text = labelType

        // Amount: outflows negative (Withdraw, Plan Purchase), others positive
        val amount = txn.amount ?: 0.0
        val sign = if (isWithdraw || isPlanPurchase || isLuckyDraw) "-" else "+"
        holder.tvAmount.text = "$sign$${String.format(Locale.US, "%.2f", amount)}"

        // Content line
        holder.tvContent.text = when {
            isDeposit -> "Deposited $${String.format(Locale.US, "%.2f", amount)}"
            isWithdraw -> "Withdrew $${String.format(Locale.US, "%.2f", amount)}"
            isPlanPurchase -> "Invested $${String.format(Locale.US, "%.2f", amount)}"
            isLuckyDraw -> "Invested $${String.format(Locale.US, "%.2f", amount)}"
            else -> labelType
        }

        // Status + Date
        holder.tvStatus.text = prettyStatus(txn.status)
        holder.tvDate.text = formatDate(txn.timestamp)

        // Icon
        holder.ivIcon?.setImageResource(
            if (isWithdraw) R.drawable.ic_withdraw2 else R.drawable.profit_ic
        )

        holder.itemView.setOnClickListener { onClick(txn) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(list: List<TransactionModel>?) {
        items = list ?: emptyList()
        notifyDataSetChanged()
    }

    private fun formatDate(ts: Any?): String = try {
        val date = (ts as? Timestamp)?.toDate() ?: return ""
        SimpleDateFormat("dd MMM yyyy , hh:mm a", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        ""
    }

    private fun prettyStatus(status: String?): String {
        val s = status?.lowercase(Locale.ROOT) ?: return ""
        return when (s) {
            "pending_admin" -> "Pending"
            "success" -> "Success"
            "failed" -> "Failed"
            else -> status.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    /** Map raw keys to clean UI labels */
    private fun prettyType(type: String?): String {
        val t = type?.lowercase(Locale.ROOT) ?: return ""
        return when (t) {
            "deposit" -> "Deposit"
            "withdraw" -> "Withdraw"
            "dailyroi" -> "Daily ROI"
            "teamprofit" -> "Team Profit"
            "rank-reward", "rankreward", "rank_reward" -> "Rank Reward"

            "star-salary", "starsalary", "star_salary" -> "Star Salary"

            "plan purchase", "plan_purchase", "planpurchase" -> "Plan Purchase"


            "all" -> "All"
            else -> type.orEmpty().replace('-', ' ').replace('_', ' ')
                .replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    /** Treat any of these as "plan purchase" regardless of how the backend spelled it */
    private fun isPlanPurchase(type: String?): Boolean {
        val t = type?.lowercase(Locale.ROOT) ?: return false
        return t == "plan_purchase" || t == "plan purchase" || t == "planpurchase"
    }
}