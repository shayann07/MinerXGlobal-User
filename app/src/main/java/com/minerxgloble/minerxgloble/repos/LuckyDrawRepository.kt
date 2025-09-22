package com.minerxgloble.minerxgloble.repos

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.minerxgloble.minerxgloble.models.Winner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

sealed class InvestResult {
    object Success : InvestResult()
    data class Failure(val message: String) : InvestResult()
}

/**
 * Lucky Draw data access:
 * - Winners feed
 * - User total invested feed
 * - Atomic $1 weekly-limited investment
 *
 * Enforces: **only one $1 invest per user per week (Mon→Sun, Asia/Karachi)**.
 * Achieved via a per-(user, week) lock document written in the SAME transaction.
 */
class LuckyDrawRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val accounts      = db.collection("accounts")
    private val winners       = db.collection("lucky_draw_winners")   // Admin writes weekly
    private val entries       = db.collection("lucky_draw_entries")   // Every $1 invest
    private val drawUsers     = db.collection("lucky_draw_users")     // Per-user totals
    private val transactions  = db.collection("transactions")         // Global transactions
    private val weekLocks     = db.collection("lucky_draw_week_locks")// (uid, weekKey) lock

    private object Paths {
        val INVEST_CURRENT   = FieldPath.of("investment", "currentBalance")
        val INVEST_REMAINING = FieldPath.of("investment", "remainingBalance")
    }

    /** ISO-week info for Asia/Karachi, Monday→Sunday. */
    private data class WeekInfo(
        val weekKey: String,        // e.g., "2025-W39"
        val weekStartMillis: Long,  // Monday 00:00:00.000
        val weekEndMillis: Long     // Next Monday 00:00:00.000 - 1
    )

    private fun currentWeekInfo(zoneId: ZoneId = ZoneId.of("Asia/Karachi")): WeekInfo {
        val today = LocalDate.now(zoneId)
        val wf = WeekFields.ISO // Monday-based week
        val week = today.get(wf.weekOfWeekBasedYear())
        val weekYear = today.get(wf.weekBasedYear())

        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val nextMonday = monday.plusDays(7)

        val start = monday.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = nextMonday.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = endExclusive - 1

        val weekKey = "%04d-W%02d".format(weekYear, week)
        return WeekInfo(weekKey, start, end)
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
     * Live boolean: can this user invest this week?
     * True if the (uid, weekKey) lock doc does NOT exist.
     */
    fun observeCanInvestThisWeek(uid: String) = callbackFlow<Boolean> {
        if (uid.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val week = currentWeekInfo()
        val lockId = "${uid}_${week.weekKey}"
        val reg = weekLocks.document(lockId).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(false)
                return@addSnapshotListener
            }
            // can invest if lock does NOT exist
            trySend(!(snap?.exists() ?: false))
        }
        awaitClose { reg.remove() }
    }

    /**
     * Atomic $1 invest using custom MXG userId:
     *  - Enforce ONE per week via weekLocks (docId: "${uid}_${weekKey}")
     *  - Check BOTH balances >= 1
     *  - Deduct exactly $1 from BOTH (remaining & current)
     *  - Append entry in `lucky_draw_entries` (amountUsd = 1.0)
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

            val week = currentWeekInfo(ZoneId.of("Asia/Karachi"))
            val lockId = "${uid}_${week.weekKey}"
            val lockRef = weekLocks.document(lockId)

            db.runTransaction { tr ->
                // 1) Enforce weekly one-time rule (read lock)
                val lockSnap = tr.get(lockRef)
                if (lockSnap.exists()) {
                    throw IllegalStateException("You’ve already invested this week (${week.weekKey}).")
                }

                // 2) Read account & check balances
                val accSnap = tr.get(accRef)
                if (!accSnap.exists()) throw IllegalStateException("Account not found")

                val inv = accSnap.get("investment") as? Map<*, *>
                val remaining = (inv?.get("remainingBalance") as? Number)?.toDouble() ?: 0.0
                val current   = (inv?.get("currentBalance") as? Number)?.toDouble() ?: 0.0
                if (remaining < 1.0 || current < 1.0) {
                    throw IllegalStateException("Insufficient balance")
                }

                // 3) Deduct exactly $1 from both balances
                tr.update(
                    accRef,
                    Paths.INVEST_REMAINING, FieldValue.increment(-1.0),
                    Paths.INVEST_CURRENT,   FieldValue.increment(-1.0)
                )

                // 4) Create the week lock (idempotency & uniqueness within the week)
                tr.set(lockRef, mapOf(
                    "userId"          to uid,
                    "weekKey"         to week.weekKey,
                    "weekStartMillis" to week.weekStartMillis,
                    "createdAt"       to FieldValue.serverTimestamp()
                ))

                // 5) Append the entry (historical record, forced to $1)
                tr.set(entries.document(), mapOf(
                    "userId"       to uid,
                    "amountUsd"    to 1.0, // force $1
                    "investedAt"   to FieldValue.serverTimestamp(),
                    "weekKey"      to week.weekKey,
                    "weekStartMs"  to week.weekStartMillis
                ))

                // 6) Per-user totals
                tr.set(drawUsers.document(uid), mapOf(
                    "userId"        to uid,
                    "totalInvested" to FieldValue.increment(1.0),
                    "updatedAt"     to FieldValue.serverTimestamp(),
                    "lastWeekKey"   to week.weekKey
                ), SetOptions.merge())

                // 7) Global transaction log
                tr.set(transactions.document(), mapOf(
                    "userId"     to uid,                          // custom user id
                    "type"       to "Lucky Draw Investment",
                    "amount"     to 1.0,                          // force $1
                    "timestamp"  to FieldValue.serverTimestamp(),
                    "accountId"  to accRef.id,
                    "weekKey"    to week.weekKey
                ))
                null
            }.await()

            InvestResult.Success
        } catch (e: Exception) {
            InvestResult.Failure(e.message ?: "Failed")
        }
    }
}
