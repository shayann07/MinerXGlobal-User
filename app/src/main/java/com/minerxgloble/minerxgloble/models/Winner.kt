package com.minerxgloble.minerxgloble.models

data class Winner(
    val userId: String = "",
    val displayName: String = "",
    val prizeUsd: Double = 0.0,
    val weekStartMillis: Long? = null,
    val weekEndMillis: Long? = null,
    val announcedAtMillis: Long? = null
)