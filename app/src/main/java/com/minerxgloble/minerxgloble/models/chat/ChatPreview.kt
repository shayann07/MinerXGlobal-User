package com.trustledger.aitrustledger.models.chat


data class ChatPreview(
    val userId: String,
    val userName: String,
    val lastMessage: String,
    val timestamp: Long,
    var sender: String = "",

) {
    @JvmName("getUserIdCustom")
    fun getUserId(): String {
        return userId
    }
}
