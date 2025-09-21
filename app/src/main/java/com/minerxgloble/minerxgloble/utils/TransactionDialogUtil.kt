package com.minerxgloble.minerxgloble.utils

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.models.TransactionModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransactionDialogUtil {

    fun showTransactionDialog(context: Context, txn: TransactionModel) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialoge_recipt)
        dialog.setCancelable(true)
        val window = dialog.window
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(), // 90% of screen width
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setDimAmount(0.6f)
        window?.setGravity(Gravity.CENTER)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.6f)
            setGravity(Gravity.CENTER)
            attributes.y = 100
        }

        val tvAmount = dialog.findViewById<TextView>(R.id.invested_amount)

        // cards (max 6)
        val cardList = listOf(
            R.id.card_userId,
            R.id.card_paymentTime,
            R.id.card_planName,
            R.id.card_userName,

        )

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val formattedDate = sdf.format(txn.timestamp?.toDate() ?: Date())

        // normalize type
        val type = txn.type?.lowercase()?.replace(" ", "") ?: "unknown"

        val values = when (type) {
            // ─── Deposits ───
            "deposit" -> listOf(
                "Address" to (txn.address ?: "N/A"),
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Deposit"
            )

            // ─── Withdrawals ───
            "withdraw", "withdrawal" -> listOf(
                "Wallet Address" to (txn.walletAddress ?: "N/A"),
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Withdraw"
            )

            // ─── Plans ───
            "planpurchase" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Plan Name" to (txn.planName ?: "N/A"),
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Plan Purchase"
            )
            "planbonus" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "From" to (txn.triggeredBy ?: "N/A"),
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Plan Bonus"
            )

            // ─── ROI & Team ───
            "dailyroi" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Daily ROI"
            )
            "teamprofit" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Team Profit"
            )

            // ─── Ranks & Salary ───
            "rank-reward", "rankreward" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Rank Reward"
            )
            "star-salary", "starsalary" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Star Salary"
            )

            // ─── Other game/bonus txns ───
            "luckyspin" -> listOf(
                "Reward" to "$${"%.2f".format(txn.amount)}",
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Lucky Spin"
            )
            "salary" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Salary"
            )
            "achievement" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to "Achievement"
            )

            // ─── Default ───
            else -> listOf(
                "Transaction" to (txn.type ?: "Unknown"),
                "Status" to txn.status,
                "Time" to formattedDate
            )
        }

        tvAmount.text = "$${"%.2f".format(txn.amount)}"

        // Hide all cards first
        cardList.forEach { dialog.findViewById<View>(it)?.visibility = View.GONE }

        // Populate dynamic values
        values.forEachIndexed { index, (label, value) ->
            if (index < cardList.size) {
                val card = dialog.findViewById<View>(cardList[index])
                card.findViewById<TextView>(R.id.title).text = label
                card.findViewById<TextView>(R.id.value).text = value
                card.visibility = View.VISIBLE
            }
        }

        dialog.show()
    }
}
