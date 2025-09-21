package com.minerxgloble.minerxgloble.models

data class TeamUser(
    val userId: String,
    val name: String,
    val status: String   // "active" | "inactive"
)