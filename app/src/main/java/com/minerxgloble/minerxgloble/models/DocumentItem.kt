package com.minerxgloble.minerxgloble.models

import com.google.firebase.Timestamp

data class DocumentItem(
    val title: String = "",
    val description: String = "",
    val updatedAt: Timestamp? = null,
    val fileUrl: String = ""
)
