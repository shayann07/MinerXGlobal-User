package com.minerxgloble.minerxgloble.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class TransactionModel(
    // Firestore document ID — must be set manually after toObject()
    var id: String = "",

    // Common
    var userId: String = "",
    var amount: Double = 0.0,
    var type: String = "",        // deposit or withdraw
    var status: String = "",      // approved, expired, rejected, etc.
    var timestamp: Timestamp? = null,



    // Optional / nullable fields for specific types
    var statusText: String? = null,
    var walletAddress: String? = null,
    var address: String? = null,
    var email: String? = null,
    var coinpaymentsId: String? = null,
    var balanceUpdated: Boolean? = null,
    var planName: String? = null,

    var triggeredBy: String? = null


)
