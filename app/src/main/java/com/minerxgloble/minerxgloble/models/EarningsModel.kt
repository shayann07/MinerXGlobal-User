package com.minerxgloble.minerxgloble.models

data class EarningsModel(
    val dailyProfit: Double=0.0,      // Profit earned daily
    val totalRoi: Double=0.0,     // Total Roi
    val referralProfit: Double=0.0,   // Referral-based profit
    val totalEarned: Double=0.0,      // Total earnings accumulated
    val teamProfit: Double=0.0,
    val totalEarnedToDate: Double=0.0,
    val teamDaily: Double = 0.0,
    val lastTeamDate: String = ""
) // Profit from the user's referral team)
{
    fun toMap(): Map<String, Any> {
        return mapOf(
            "dailyProfit" to dailyProfit,
            "totalRoi" to totalRoi,
            "referralProfit" to referralProfit,
            "totalEarned" to totalEarned,
            "teamProfit" to teamProfit,
            "totalEarnedToDate" to totalEarnedToDate,
            "teamDaily" to teamDaily,
            "lastTeamDate" to lastTeamDate,
        )
    }
}