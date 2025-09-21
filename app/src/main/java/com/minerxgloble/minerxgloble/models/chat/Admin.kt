package com.trustledger.aitrustledger.models.chat

data class Admin(
    var id: String = "",
    val deviceToken: String = "",
    val email: String = "",
    val name: String = "",
    val password: String = "",
    val phoneNumber: String = ""
)
