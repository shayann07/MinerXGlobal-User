package com.minerxgloble.minerxgloble.models

/** All numbers the Dashboard needs in one place */
data class DashboardMetrics(
    val balance: Double = .0,    // My Balance
    val roiIncome: Double = .0,    // ROI income
    val rankReward: Double = .0,    // Sum of collected rank rewards
    val directActiveDeposit: Double = .0,  // L-1 active users’ deposit
    val teamIncome: Double = .0,    // lifetime_team_income
    val salaryBonus: Double = .0,    // 0 for now
    val totalEarned: Double = .0,    // Total Earnings
    val dailyEarnings: Double = .0,     // Today’s ROI + Team txns
    val referralIncome: Double = .0
)