package com.minerxgloble.minerxgloble.models.chat

import com.google.firebase.Timestamp

data class User(
    val name: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val phoneNumber: String = "",
    val referralCode: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val isBlocked: Boolean = false,
    val deviceToken: String = "",
    val status: String = ""
)