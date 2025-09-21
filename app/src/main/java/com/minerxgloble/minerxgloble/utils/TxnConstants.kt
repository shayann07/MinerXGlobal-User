package com.minerxgloble.minerxgloble.utils

object TxnConstants {

    // 🔑 Firestore field names
    const val FIELD_ID = "transactionId"
    const val FIELD_USER_ID = "userId"
    const val FIELD_AMOUNT = "amount"
    const val FIELD_TYPE = "type"
    const val FIELD_ADDRESS = "address"
    const val FIELD_STATUS = "status"
    const val FIELD_BALANCE_UPDATED = "balanceUpdated"
    const val FIELD_TIMESTAMP = "timestamp"
    const val FIELD_PLAN_NAME = "planName"

    // 🔄 Transaction types
    const val TYPE_WITHDRAW = "withdraw"
    const val TYPE_DEPOSIT = "deposit"
    const val TYPE_ACHIEVEMENT = "achievement"
    const val TYPE_INVESTMENT_BOUGHT = "Plan Bought"
    const val TYPE_ROI = "roiReward"
    const val TYPE_TEAM = "teamReward"
    const val TYPE_LUCKY_SPIN = "luckySpin"
    const val TYPE_SALARY = "salaryLevel"
    const val TYPE_REFERRAL = "referralReward"

    // 🟡 Status values
    const val STATUS_PENDING = "pending"
    const val STATUS_APPROVED = "approved"
    const val STATUS_REJECTED = "rejected"
    const val STATUS_COLLECTED = "collected"
    const val STATUS_RECEIVED = "received"
    const val STATUS_BOUGHT = "bought"
    const val STATUS_SOLD = "sold"
    const val STATUS_CANCELLED = "cancelled"

    const val   TRIGGERED_BY = "triggeredBy"


}
