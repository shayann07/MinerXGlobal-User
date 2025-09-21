package com.minerxgloble.minerxgloble.repos

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.tasks.await

/**
 * Thin repo that fetches the preview/snapshot from the Cloud Function:
 *  - getStarPreview({ userId, monthKey? })
 */
class SalaryRepo(context: Context) {
    private val functions = FirebaseFunctions.getInstance("us-central1")

    suspend fun getPreview(userId: String, monthKey: String? = null): Map<String, Any?> {
        val payload = buildMap<String, Any> {
            put("userId", userId)
            if (!monthKey.isNullOrBlank()) put("monthKey", monthKey)
        }
        val res: HttpsCallableResult =
            functions.getHttpsCallable("getStarPreview").call(payload).await()
        @Suppress("UNCHECKED_CAST")
        return (res.data as? Map<String, Any?>) ?: emptyMap()
    }
}
