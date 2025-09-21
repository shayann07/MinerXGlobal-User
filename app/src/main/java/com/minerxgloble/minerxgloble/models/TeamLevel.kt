package com.minerxgloble.minerxgloble.models

data class TeamLevel(
    val level: Int,
    val totalUsers: Int,
    val activeUsers: Int,
    val inactiveUsers: Int,
    val totalDeposit: Double,
    val totalBuyingProfit: Double,
    val investedAmount: Double,    // ← NEW
    val levelUnlocked: Boolean
)
