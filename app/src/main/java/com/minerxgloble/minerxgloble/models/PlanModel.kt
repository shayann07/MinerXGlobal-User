package com.minerxgloble.minerxgloble.models

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import java.io.Serializable

@Keep
@IgnoreExtraProperties
data class PlanModel(
    val docId: String = "",
    val planName: String = "",
    val minAmount: Int = 0,
    val maxAmount: Int? = null,
    val dailyPercentage: Float = 0f,
    val directProfit: Float = 0f,
    val totalPayout: Float = 0f,
    val timestamp: Timestamp = Timestamp.now()
) : Serializable

fun DocumentSnapshot.toPlanModel(): PlanModel = PlanModel(
    docId           = id,
    planName        = getString("planName") ?: "",
    minAmount       = (get("minAmount") as? Number)?.toInt() ?: 0,
    maxAmount       = (get("maxAmount") as? Number)?.toInt(),
    dailyPercentage = (get("dailyPercentage") as? Number)?.toFloat() ?: 0f,
    directProfit    = (get("directProfit") as? Number)?.toFloat() ?: 0f,
    totalPayout     = (get("totalPayout") as? Number)?.toFloat() ?: 0f,
    timestamp       = getTimestamp("timestamp") ?: Timestamp.now()
)
