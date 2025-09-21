package com.minerxgloble.minerxgloble.models

data class AchievementLevel(
    val index: Int,                     // 0‥9
    val name: String,                   // “Starter Squad” …
    val salary: Double,                 // reward amount (0 if locked)
    val directThreshold: Double,
    val indirectThreshold: Double,
    val isUnlocked: Boolean,
    var isCollected: Boolean            // mutable so adapter can toggle instantly
)