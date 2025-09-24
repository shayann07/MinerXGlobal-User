package com.minerxgloble.minerxgloble.models

// ADD: rich, write-accurate preview for confirmation UI
data class LightPlanPreview(
    val planId: String,
    val planName: String,
    val minAmount: Double,
    val maxAmount: Double?,     // null = unlimited
    val payoutPercent: Double
)