package com.minerxgloble.minerxgloble.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class UserPlan(
    @DocumentId var docId: String? = null,
    var pkgId: String = "",             // points to plans/{docId}
    var principal: Double = 0.0,
    var roiPercent: Double = 0.0,       // dailyPercentage copied at buy time
    var roiAmount: Double = 0.0,        // daily ROI amount computed at buy
    var totalPayoutAmount: Double = 0.0,
    var totalAccumulated: Double = 0.0, // incremented by jobs/collections
    var uplineReferralBonus: Double = 0.0,
    var status: String = "active",      // "active" | "expired" | ...
    var buyDate: Timestamp? = null,
    var lastCollectedDate: Timestamp? = null,
    var referrerId: String = "",
    var referralReceivedDirectProfit: Boolean = false,
    var userId: String = "",
    var accountId: String = ""
)

/** UI-ready model after joining with plan meta. */
data class UserPlanUi(
    val userPlan: UserPlan,
    val planName: String = "",
    val directPercent: Double? = null,  // from plans.directProfit (optional badge)
    val minAmount: Double? = null,
    val maxAmount: Double? = null
) {
    val isActive get() = userPlan.status.equals("active", ignoreCase = true)
    val progress
        get() = if (userPlan.totalPayoutAmount > 0) {
            (userPlan.totalAccumulated / userPlan.totalPayoutAmount).coerceIn(0.0, 1.0)
        } else 0.0
}
