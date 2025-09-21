package com.minerxgloble.minerxgloble.models

import androidx.annotation.Keep

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class User(
    var uid: String = "",
    var docId: String = "",
    var name: String? = null,
    var lastName: String? = null,
    var email: String? = null,
    var password: String? = null,
    var phoneNumber: String? = null,
    var referralCode: String? = null,
    var deviceToken: String? = null,
    var createdAt: Timestamp? = Timestamp.now(),
    val firebaseUid: String = "",
    var isBlocked: Boolean = false,
    val status: String = "",

) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "docId" to docId,
        "firebaseUid" to firebaseUid,
        "name" to (name ?: ""),
        "lastName" to (lastName ?: ""),
        "email" to (email ?: ""),
        "password" to (password ?: ""),
        "phoneNumber" to (phoneNumber ?: ""),
        "referralCode" to (referralCode ?: ""),
        "deviceToken" to (deviceToken ?: ""),
        "createdAt" to (createdAt ?: Timestamp.now()),
        "isBlocked" to isBlocked,
        "status" to status
    )
}
