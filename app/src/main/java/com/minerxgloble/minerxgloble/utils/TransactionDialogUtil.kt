package com.minerxgloble.minerxgloble.utils

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
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

        val dm = context.resources.displayMetrics
        fun dp(v: Int) = (v * dm.density + 0.5f).toInt()
        val maxWindowHeight = (dm.heightPixels * 0.85f).toInt()
        val targetWidth = minOf((dm.widthPixels * 0.98f).toInt(), dp(600))

        // ⬇️ Adaptive: width fixed, height wraps content initially
        dialog.window?.apply {
            setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.6f)
            setGravity(Gravity.CENTER)
        }

        // ── Type/sign detection
        val isWithdraw      = equalsIgnoreCase(txn.type, "withdraw")
        val isPlanPurchase  = isPlanPurchase(txn.type)
        val isLuckyDraw     = equalsIgnoreCase(txn.type, "Lucky Draw Investment")
        val isDirectProfit  = equalsIgnoreCase(txn.type, "Direct Profit")

        // ── Amount on top
        val amount = txn.amount ?: 0.0
        val sign = if (isWithdraw || isPlanPurchase || isLuckyDraw) "-" else "+"
        dialog.findViewById<TextView>(R.id.invested_amount)?.text =
            "$sign$${String.format(Locale.US, "%.2f", amount)}"

        // ── Date & labels
        val sdf = SimpleDateFormat("dd MMM yyyy , hh:mm a", Locale.getDefault())
        val formattedDate = try { txn.timestamp?.toDate()?.let { sdf.format(it) } ?: sdf.format(Date()) }
        catch (_: Exception) { sdf.format(Date()) }

        val labelType   = prettyType(txn.type)
        val labelStatus = prettyStatus(txn.status)

        // Show status only when it exists and type supports it
        val shouldShowStatus = !txn.status.isNullOrBlank() && !isDirectProfit && !isLuckyDraw

        // ── Status Chip
        dialog.findViewById<Chip>(R.id.chipStatus)?.apply {
            if (!shouldShowStatus) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = labelStatus
                try {
                    when (labelStatus.lowercase(Locale.ROOT)) {
                        "complete","approved","collected","success" -> setChipBackgroundColorResource(R.color.green)
                        "pending" -> setChipBackgroundColorResource(R.color.orange)
                        "failed","canceled","expired" -> setChipBackgroundColorResource(R.color.red)
                        else -> chipBackgroundColor = ColorStateList.valueOf(0x332D2140.toInt())
                    }
                } catch (_: Exception) { /* ignore if color not present */ }
            }
        }

        // ── Build info rows (no Amount row; add Status only if allowed)
        val rows: List<Pair<String, String?>> = when {
            equalsIgnoreCase(txn.type, "deposit") -> buildList<Pair<String, String?>> {
                maybeAdd("Address", txn.address)
                maybeAdd("Status", if (shouldShowStatus) labelStatus else null)
                add("Time" to formattedDate)
                add("Transaction" to "Deposit")
            }
            isWithdraw -> buildList<Pair<String, String?>> {
                val withdrawAddress = txn.address?.takeIf { it.isNotBlank() }
                maybeAdd("Wallet Address", withdrawAddress)
                maybeAdd("Status", if (shouldShowStatus) labelStatus else null)
                add("Time" to formattedDate)
                add("Transaction" to "Withdraw")
            }
            isPlanPurchase -> buildList<Pair<String, String?>> {
                maybeAdd("Plan Name", txn.planName)
                maybeAdd("Status", if (shouldShowStatus) labelStatus else null)
                add("Time" to formattedDate)
                add("Transaction" to "Plan Purchase")
            }
            isLuckyDraw -> buildList<Pair<String, String?>> {
                add("Time" to formattedDate)
                add("Transaction" to "Lucky Draw Investment")
            }
            equalsIgnoreCase(txn.type, "dailyroi") -> buildList<Pair<String, String?>> {
                maybeAdd("Status", if (shouldShowStatus) labelStatus else null)
                add("Time" to formattedDate)
                add("Transaction" to "Daily Income")
            }
            equalsIgnoreCase(txn.type, "teamprofit") -> buildList<Pair<String, String?>> {
                maybeAdd("Status", if (shouldShowStatus) labelStatus else null)
                add("Time" to formattedDate)
                add("Transaction" to "Team Income")
            }
            isRankReward(txn.type) -> buildList<Pair<String, String?>> {
                maybeAdd("Status", if (shouldShowStatus) labelStatus else null)
                add("Time" to formattedDate)
                add("Transaction" to "Rank Income")
            }
            isStarSalary(txn.type) -> buildList<Pair<String, String?>> {
                maybeAdd("Status", if (shouldShowStatus) labelStatus else null)
                add("Time" to formattedDate)
                add("Transaction" to "Monthly Salary")
            }
            isDirectProfit -> buildList<Pair<String, String?>> {
                add("Time" to formattedDate)
                add("Transaction" to "Direct Income")
            }
            else -> buildList<Pair<String, String?>> {
                add("Transaction" to labelType.ifBlank { "Unknown" })
                maybeAdd("Status", if (shouldShowStatus) labelStatus else null)
                add("Time" to formattedDate)
            }
        }

        // ── Fill the 4 include cards
        val cardIds = listOf(R.id.card_userId, R.id.card_paymentTime, R.id.card_planName, R.id.card_userName)
        cardIds.forEach { id -> dialog.findViewById<View>(id)?.visibility = View.GONE }
        rows.take(cardIds.size).forEachIndexed { i, (title, value) ->
            val card = dialog.findViewById<View>(cardIds[i]) ?: return@forEachIndexed
            card.findViewById<TextView>(R.id.title)?.text = title
            card.findViewById<TextView>(R.id.value)?.text = value ?: "N/A"
            card.visibility = View.VISIBLE
        }

        // Close button
        dialog.findViewById<MaterialButton>(R.id.btnSecondary)?.setOnClickListener { dialog.dismiss() }

        // ⬇️ Adaptive height pass after layout: keeps center and adjusts size to content
        val contentRoot = dialog.findViewById<ViewGroup>(android.R.id.content)
        contentRoot?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val window = dialog.window ?: return
                val measured = contentRoot.measuredHeight
                val desiredHeight = if (measured > maxWindowHeight) maxWindowHeight else ViewGroup.LayoutParams.WRAP_CONTENT
                window.setLayout(targetWidth, desiredHeight)
                window.setGravity(Gravity.CENTER)
                // If capped, ensure inner ScrollView fills and scrolls nicely
                if (desiredHeight == maxWindowHeight) {
                    findScrollView(contentRoot)?.isFillViewport = true
                }
                // run once for this dialog
                contentRoot.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        dialog.show()
    }

    private fun findScrollView(root: View): ScrollView? {
        if (root is ScrollView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findScrollView(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────────

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
            else -> type.orEmpty()
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun prettyStatus(status: String?): String {
        val s = status?.lowercase(Locale.ROOT) ?: return ""
        return when (s) {
            "pending_admin" -> "Pending"
            "success" -> "Success"
            "failed" -> "Failed"
            "canceled_by_user" -> "Canceled"
            else -> status.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    private fun isPlanPurchase(type: String?): Boolean {
        val t = type?.lowercase(Locale.ROOT) ?: return false
        return t == "plan_purchase" || t == "plan purchase" || t == "planpurchase"
    }

    private fun isRankReward(type: String?): Boolean {
        val t = type?.lowercase(Locale.ROOT) ?: return false
        return t == "rank-reward" || t == "rankreward" || t == "rank_reward"
    }

    private fun isStarSalary(type: String?): Boolean {
        val t = type?.lowercase(Locale.ROOT) ?: return false
        return t == "star-salary" || t == "starsalary" || t == "star_salary"
    }

    private fun equalsIgnoreCase(a: String?, b: String): Boolean =
        a?.equals(b, ignoreCase = true) == true

    private fun MutableList<Pair<String, String?>>.maybeAdd(label: String, value: String?) {
        if (!value.isNullOrBlank()) add(label to value)
    }
}
