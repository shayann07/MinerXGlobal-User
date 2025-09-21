package com.minerxgloble.minerxgloble.repos

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.minerxgloble.minerxgloble.models.Winner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await



sealed class InvestResult {
    object Success : InvestResult()
    data class Failure(val message: String) : InvestResult()
}

class LuckyDrawRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val accounts = db.collection("accounts")
    private val winners = db.collection("lucky_draw_winners")   // Admin writes weekly
    private val entries = db.collection("lucky_draw_entries")   // Every $1 invest
    private val drawUsers = db.collection("lucky_draw_users")   // Per-user totals
    private val transactions = db.collection("transactions")    // Global transactions

    private object Paths {
        val INVEST_CURRENT   = FieldPath.of("investment", "currentBalance")
        val INVEST_REMAINING = FieldPath.of("investment", "remainingBalance")
    }

    /** Live (hot) winners stream, most recent first */
    fun observeWinners(limit: Long = 100) = callbackFlow<List<Winner>> {
        val reg = winners
            .orderBy("announcedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { d ->
                    Winner(
                        userId = d.getString("userId") ?: "",
                        displayName = d.getString("displayName") ?: "",
                        prizeUsd = (d.getDouble("prizeUsd") ?: 0.0),
                        weekStartMillis = d.getTimestamp("weekStart")?.toDate()?.time,
                        weekEndMillis = d.getTimestamp("weekEnd")?.toDate()?.time,
                        announcedAtMillis = d.getTimestamp("announcedAt")?.toDate()?.time
                    )
                }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** Live (hot) per-user total invested for lucky draw */
    fun observeUserTotalInvested(uid: String) = callbackFlow<Double> {
        val reg = drawUsers.document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) {
                    trySend(0.0)
                    return@addSnapshotListener
                }
                val total = (snap.getDouble("totalInvested") ?: 0.0)
                trySend(total)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Atomic $1 invest using **custom MXG userId**:
     *  - Find account by accounts.userId == uid
     *  - Check BOTH balances >= 1
     *  - Deduct 1 from BOTH (remaining & current)
     *  - Append entry in `lucky_draw_entries`
     *  - Upsert & increment `lucky_draw_users/{uid}.totalInvested`
     *  - Append a transaction in `transactions` (type "Lucky Draw Investment")
     */
    suspend fun investOneDollar(uid: String): InvestResult {
        if (uid.isBlank()) return InvestResult.Failure("User id missing")

        return try {
            val accRef = accounts
                .whereEqualTo("userId", uid)
                .limit(1)
                .get().await()
                .documents
                .firstOrNull()
                ?.reference
                ?: return InvestResult.Failure("Account not found")

            db.runTransaction { tr ->
                val accSnap = tr.get(accRef)
                if (!accSnap.exists()) throw IllegalStateException("Account not found")

                val inv = accSnap.get("investment") as? Map<*, *>
                val remaining = (inv?.get("remainingBalance") as? Number)?.toDouble() ?: 0.0
                val current   = (inv?.get("currentBalance") as? Number)?.toDouble() ?: 0.0
                if (remaining < 1.0 || current < 1.0) {
                    throw IllegalStateException("Insufficient balance")
                }

                // Deduct 1 from both balances
                tr.update(
                    accRef,
                    Paths.INVEST_REMAINING, FieldValue.increment(-1.0),
                    Paths.INVEST_CURRENT,   FieldValue.increment(-1.0)
                )

                // Entry
                tr.set(entries.document(), mapOf(
                    "userId"     to uid,
                    "amountUsd"  to 1.0,
                    "investedAt" to FieldValue.serverTimestamp()
                ))

                // Per-user totals
                tr.set(drawUsers.document(uid), mapOf(
                    "userId"        to uid,
                    "totalInvested" to FieldValue.increment(1.0),
                    "updatedAt"     to FieldValue.serverTimestamp()
                ), com.google.firebase.firestore.SetOptions.merge())

                // Global transaction log
                tr.set(transactions.document(), mapOf(
                    "userId"    to uid,                          // custom user id
                    "type"      to "Lucky Draw Investment",
                    "amount"    to 1.0,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "accountId" to accRef.id
                ))

                null
            }.await()

            InvestResult.Success
        } catch (e: Exception) {
            InvestResult.Failure(e.message ?: "Failed")
        }
    }
}
