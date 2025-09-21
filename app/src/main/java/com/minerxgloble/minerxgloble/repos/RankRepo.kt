package com.minerxgloble.minerxgloble.repos

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.collections.get

class RankRepo(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1"),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun computeBusiness(userId: String): Pair<Double, Double> {
        val data = mapOf("userId" to userId, "maxDepth" to 15)
        val res = functions
            .getHttpsCallable("computeRanksBusiness")
            .call(data).await().data as Map<*, *>
        val direct = (res["directBusiness"] as Number).toDouble()
        val indirect = (res["indirectBusiness"] as Number).toDouble()
        return direct to indirect
    }

    /** Returns claimed rank ids for the user */
    suspend fun fetchClaimed(userId: String): Set<String> {
        val qs = db.collection("rankClaims").whereEqualTo("userId", userId).get().await()
        return qs.documents.mapNotNull { it.getString("rankId") }.toSet()
    }

    suspend fun claim(userId: String, rankId: String): Set<String> {
        val res = functions
            .getHttpsCallable("claimRank")
            .call(mapOf("userId" to userId, "rankId" to rankId))
            .await().data as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val claimed = (res["claimedIds"] as List<*>).map { it.toString() }.toSet()
        return claimed
    }
}
