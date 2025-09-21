package com.trustledger.aitrustledger.models.chat

import com.google.firebase.Timestamp

data class Message(
    var id: String = "",
    var message: String = "",
    var senderId: String = "",
    var receiverId: String = "",
    var status: String = STATUS_SENT,
    var sender: String = "",
    val createdAt: Timestamp? = Timestamp.now()
) {
    companion object {
        const val STATUS_SENT = "sent"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_SEEN = "seen"
    }
}