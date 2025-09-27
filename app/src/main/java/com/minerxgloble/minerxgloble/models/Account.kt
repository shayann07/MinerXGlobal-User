package com.minerxgloble.minerxgloble.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class Account(
    val userId: String = "",
    val accountId: String = "",
    val status: String = "",
    val createdAt: Timestamp? = Timestamp.now(),
    val investment: InvestmentModel = InvestmentModel(),
    val earnings: EarningsModel = EarningsModel()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "accountId" to accountId,
        "status" to status,
        "createdAt" to (createdAt ?: Timestamp.now()),
        "investment" to investment.toMap(),
        "earnings" to earnings.toMap(),
    )

    companion object {
        fun fromDocument(doc: DocumentSnapshot): Account {
            val userId = doc.getString("userId") ?: ""
            val accountId = doc.getString("accountId") ?: doc.id
            val status = doc.getString("status") ?: ""
            val createdAt = doc.getTimestamp("createdAt")

            val invMap = doc.get("investment") as? Map<*, *>
            val earnMap = doc.get("earnings") as? Map<*, *>

            val investment = InvestmentModel(
                totalDeposit        = (invMap?.get("totalDeposit") as? Number)?.toDouble() ?: 0.0,
                remainingBalance    = (invMap?.get("remainingBalance") as? Number)?.toDouble() ?: 0.0,
                currentBalance      = (invMap?.get("currentBalance") as? Number)?.toDouble() ?: 0.0,
                depositFromEarnings = (invMap?.get("depositFromEarnings") as? Number)?.toDouble() ?: 0.0,
                totalInvestedInPlans= (invMap?.get("totalInvestedInPlans") as? Number)?.toDouble() ?: 0.0
            )

            val earnings = EarningsModel(
                dailyProfit        = (earnMap?.get("dailyProfit") as? Number)?.toDouble() ?: 0.0,
                totalRoi           = (earnMap?.get("totalRoi") as? Number)?.toDouble() ?: 0.0,
                referralProfit     = (earnMap?.get("referralProfit") as? Number)?.toDouble() ?: 0.0,
                totalEarned        = (earnMap?.get("totalEarned") as? Number)?.toDouble() ?: 0.0,
                teamProfit         = (earnMap?.get("teamProfit") as? Number)?.toDouble() ?: 0.0,
                totalEarnedToDate  = (earnMap?.get("totalEarnedToDate") as? Number)?.toDouble() ?: 0.0,
                teamDaily          = (earnMap?.get("teamDaily") as? Number)?.toDouble() ?: 0.0,
                lastTeamDate       = (earnMap?.get("lastTeamDate") as? String).orEmpty()
            )

            return Account(
                userId = userId,
                accountId = accountId,
                status = status,
                createdAt = createdAt,
                investment = investment,
                earnings = earnings
            )
        }
    }
}
