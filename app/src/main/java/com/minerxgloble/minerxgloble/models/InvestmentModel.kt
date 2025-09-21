package com.minerxgloble.minerxgloble.models

data class InvestmentModel(
    val totalDeposit: Double=0.0,     // Total amount deposited by the user
    val remainingBalance: Double=0.0, // The remaining balance in the account
    val currentBalance: Double=0.0,
    val depositFromEarnings: Double=0.0,
    val totalInvestedInPlans: Double=0.0
)     // The profit earned from staking
{
    fun toMap(): Map<String, Any> {
        return mapOf(
            "totalDeposit" to totalDeposit,
            "remainingBalance" to remainingBalance,
            "currentBalance" to currentBalance,
            "depositFromEarnings" to depositFromEarnings,
            "totalInvestedInPlans" to totalInvestedInPlans
        )
    }
}