package com.minerxgloble.minerxgloble.repos

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.minerxgloble.minerxgloble.fcm.AccessToken
import com.minerxgloble.minerxgloble.fcm.Fcm
import com.minerxgloble.minerxgloble.models.UserPlan
import com.minerxgloble.minerxgloble.models.UserPlanUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

class BuyPlanRepo(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    sealed class BuyResult {
        data class Success(
            val planName: String,
            val amount: Double,
            val firstPlanBonus: Boolean   // used by UI to show the 50-token congrats
        ) : BuyResult()
        object MinInvestError : BuyResult()
        object InsufficientBalance : BuyResult()
        object Failure : BuyResult()
    }

    companion object {
        private const val TAG = "BuyPlanRepo"
        private const val FIRST_PLAN_TOKEN_BONUS = 50 // tokens
    }

    private object Paths {
        val INVEST_CUR_BAL        = FieldPath.of("investment", "currentBalance")
        val INVEST_REMAINING_BAL  = FieldPath.of("investment", "remainingBalance")
        val INVEST_TOTAL_IN_PLANS = FieldPath.of("investment", "totalInvestedInPlans")
        val REFERRAL_PROFIT       = FieldPath.of("earnings", "referralProfit")
        val TOTAL_EARNED          = FieldPath.of("earnings", "totalEarned")
        val TOTAL_EARNED_TO_DATE  = FieldPath.of("earnings", "totalEarnedToDate")
        val TOKENS                = FieldPath.of("earnings", "tokens")
    }

    private data class PlanDoc(
        val ref: DocumentReference,
        val id: String,
        val name: String,
        val minAmount: Double,
        val maxAmount: Double?,           // null = unlimited
        val dailyPct: Double,
        val directPct: Double,
        val payoutPct: Double
    )

    /**
     * Buy a plan or top-up an existing active one (plan auto-selected by amount).
     * - Picks plan by amount range; highest min wins when multiple match.
     * - Deducts buyer balances, activates user when cumulative invested ≥ 50.
     * - First plan ever → +50 tokens to accounts.earnings.tokens (increment uses Long).
     * - Referrer: **earnings only** (no per-plan totalAccumulated updates).
     * - Sends **FCM pushes directly** (no notifications collection):
     *    • Buyer (if first plan) — “You received 50 free tokens…”
     *    • Referrer — “Referral bonus received …” (or “Your referral bought a plan.”)
     * - All lookups use the **custom user id** stored in `users.uid` (not Firebase Auth UID).
     */
    suspend fun buyPlan(uid: String, amount: Double): BuyResult = withContext(Dispatchers.IO) {
        val trace = UUID.randomUUID().toString().take(8)
        val t0 = System.nanoTime()
        Log.d(TAG, "[$trace] buyPlan START uid=$uid amount=$amount")

        if (uid.isBlank() || amount <= 0) return@withContext BuyResult.Failure

        try {
            // ───── resolve buyer documents ─────
            val buyerAccRef = db.collection("accounts")
                .whereEqualTo("userId", uid).limit(1).get().await()
                .documents.firstOrNull()?.reference ?: return@withContext BuyResult.Failure

            val buyerUserSnap = db.collection("users")
                .whereEqualTo("uid", uid).limit(1).get().await()
                .documents.firstOrNull() ?: return@withContext BuyResult.Failure
            val buyerUserRef = buyerUserSnap.reference

            // Buyer device token for push (optional)
            val buyerToken: String? =
                buyerUserSnap.getString("fcmToken")
                    ?: buyerUserSnap.getString("deviceToken")

            // referralCode must contain the SAME custom id used in users.uid / accounts.userId
            val refCode = buyerUserSnap.getString("referralCode")?.takeIf { it.isNotBlank() }

            // ───── select plan by amount ─────
            val selected = db.collection("plans").get().await()
                .documents.mapNotNull { d ->
                    val minAmt = d.getDouble("minAmount") ?: return@mapNotNull null
                    val maxAmt = d.getDouble("maxAmount")
                    val daily  = d.getDouble("dailyPercentage") ?: return@mapNotNull null
                    val direct = d.getDouble("directProfit")    ?: return@mapNotNull null
                    val payout = d.getDouble("totalPayout")     ?: return@mapNotNull null
                    val name   = d.getString("planName")        ?: return@mapNotNull null
                    PlanDoc(d.reference, d.id, name, minAmt, maxAmt, daily, direct, payout)
                }
                .filter { p -> amount >= p.minAmount && (p.maxAmount == null || amount <= p.maxAmount) }
                .maxByOrNull { it.minAmount }
                ?: return@withContext BuyResult.MinInvestError

            // ───── referrer prelim (outside transaction) ─────
            var refAcctRef: DocumentReference? = null
            var refUserRef: DocumentReference? = null
            var refUserToken: String? = null
            var refIsActiveOutside = false
            var refDirectBlockedOutside = false // ← NEW
            if (refCode != null) {
                // accounts.userId == custom id; users.uid == custom id
                refAcctRef = db.collection("accounts")
                    .whereEqualTo("userId", refCode).limit(1).get().await()
                    .documents.firstOrNull()?.reference
                refUserRef = db.collection("users")
                    .whereEqualTo("uid", refCode).limit(1).get().await()
                    .documents.firstOrNull()?.reference

                // prefetch token + status for push (safe outside txn)
                val refSnap = refUserRef?.get()?.await()
                refIsActiveOutside = (refSnap?.getString("status") == "active")
                refUserToken = refSnap?.getString("fcmToken") ?: refSnap?.getString("deviceToken")
                refDirectBlockedOutside = refSnap?.getBoolean("directProfitBlock") == true // ← NEW
            }

            // ───── existing ACTIVE userPlan? (only active; ignore expired) ─────
            val existingActiveRef = db.collection("userPlans")
                .whereEqualTo("userId", uid)
                .whereEqualTo("planName", selected.name)
                .whereEqualTo("status", "active")
                .limit(1).get().await()
                .documents.firstOrNull()?.reference

            // Precompute potential bonus (for push text later)
            val potentialBonus = amount * selected.directPct / 100.0

            // ───── transaction ─────
            var firstPlanEverFlag = false

            val result = db.runTransaction { tr ->

                // 1) READS FIRST ─────────────────────────────────────────────
                val buyerAccSnap = tr.get(buyerAccRef)
                val balances = buyerAccSnap.get("investment") as? Map<*, *>
                val curBal = (balances?.get("currentBalance") as? Number)?.toDouble()
                val remBal = (balances?.get("remainingBalance") as? Number)?.toDouble()
                if (curBal == null || remBal == null || curBal < amount || remBal < amount)
                    return@runTransaction BuyResult.InsufficientBalance

                // Referrer user (if any) — read inside txn to gate earnings writes
                var refActive = false
                var refUid: String? = null   // ← custom id from users.uid
                var refDirectBlocked = false // ← NEW
                if (refUserRef != null && refAcctRef != null) {
                    val ru = tr.get(refUserRef!!)
                    refActive        = ru.getString("status") == "active"
                    refUid           = ru.getString("uid")   // IMPORTANT: this is the CUSTOM id
                    refDirectBlocked = ru.getBoolean("directProfitBlock") == true // ← NEW
                }

                // Plan percentages + direct bonus
                val roiPctPlan    = selected.dailyPct
                val dirPctPlan    = selected.directPct
                val payoutPctPlan = selected.payoutPct
                val bonus         = amount * dirPctPlan / 100.0

                // Active userPlan snapshot (NEW vs TOP_UP decision)
                val activeSnap = existingActiveRef?.let { tr.get(it) }
                val isNew      = existingActiveRef == null

                // Effective percents: preserve on top-up; use plan defaults on NEW
                val roiPctEff    = activeSnap?.getDouble("roiPercent")    ?: roiPctPlan
                val payoutPctEff = activeSnap?.getDouble("payoutPercent") ?: payoutPctPlan

                // 2) WRITES AFTER ALL READS ──────────────────────────────────
                val nowTs = FieldValue.serverTimestamp()

                // cumulative invested total → activation threshold & first-plan detection
                val prevTotalInPlans = (balances["totalInvestedInPlans"] as? Number)?.toDouble() ?: 0.0
                val newTotalInPlans  = prevTotalInPlans + amount
                val shouldActivate   = newTotalInPlans >= 10.0
                val isFirstPlanEver  = prevTotalInPlans == 0.0
                firstPlanEverFlag = isFirstPlanEver

                // Buyer: deduct balances + track total invested in plans
                tr.update(
                    buyerAccRef,
                    Paths.INVEST_CUR_BAL,        FieldValue.increment(-amount),
                    Paths.INVEST_REMAINING_BAL,  FieldValue.increment(-amount),
                    Paths.INVEST_TOTAL_IN_PLANS, FieldValue.increment(amount)
                )

                // First plan ever → award tokens (use Long overload)
                if (isFirstPlanEver) {
                    tr.update(buyerAccRef, Paths.TOKENS, FieldValue.increment(FIRST_PLAN_TOKEN_BONUS.toLong()))
                }

                // Activate user on threshold
                if (shouldActivate) {
                    tr.update(buyerUserRef, mapOf("status" to "active"))
                }

                // REFERRER: earnings only when eligible (gated by directProfitBlock) ── NEW CONDITION
                if (refActive && !refDirectBlocked && refAcctRef != null && bonus > 0.0) {
                    tr.update(
                        refAcctRef,
                        Paths.REFERRAL_PROFIT,      FieldValue.increment(bonus),
                        Paths.TOTAL_EARNED,         FieldValue.increment(bonus),
                        Paths.TOTAL_EARNED_TO_DATE, FieldValue.increment(bonus)
                    )

                    // Referrer transaction only when there is an earning
                    val txRef = db.collection("transactions").document()
                    tr.set(txRef, mapOf(
                        "userId"       to (refUid ?: ""), // CUSTOM id (not Firebase Auth)
                        "type"         to "Direct Profit",
                        "amount"       to bonus,
                        "sourceUserId" to uid,            // buyer custom id
                        "planId"       to selected.id,
                        "percentage"   to dirPctPlan,
                        "timestamp"    to nowTs,
                        "accountId"    to refAcctRef.id
                    ))
                }

                // Upsert userPlan
                val upRef = existingActiveRef ?: db.collection("userPlans").document()
                if (isNew) {
                    tr.set(upRef, mapOf(
                        "planId"              to selected.id,
                        "planName"            to selected.name,
                        "pkgId"               to selected.id,
                        "principal"           to amount,
                        "roiPercent"          to roiPctPlan,
                        "payoutPercent"       to payoutPctPlan,
                        "directPercent"       to dirPctPlan,
                        "roiAmount"           to amount * roiPctPlan / 100.0,
                        "totalPayoutAmount"   to amount * payoutPctPlan / 100.0,
                        "uplineReferralBonus" to bonus,
                        "status"              to "active",
                        "buyDate"             to nowTs,
                        "totalAccumulated"    to 0.0,
                        "lastCollectedDate"   to nowTs,
                        "referrerId"          to (refUid ?: ""),
                        // reflect if referrer actually got direct profit (respecting block)  ← NEW
                        "referralReceivedDirectProfit" to (refActive && !refDirectBlocked),
                        "userId"              to uid,
                        "accountId"           to buyerAccRef.id
                    ))
                } else {
                    val currPrincipal = activeSnap!!.getDouble("principal") ?: 0.0
                    val newPrincipal  = currPrincipal + amount
                    val newRoiAmount  = newPrincipal * roiPctEff    / 100.0
                    val newPayoutAmt  = newPrincipal * payoutPctEff / 100.0

                    tr.update(upRef, mapOf(
                        "principal"           to newPrincipal,
                        "roiPercent"          to roiPctEff,
                        "payoutPercent"       to payoutPctEff,
                        "directPercent"       to dirPctPlan,
                        "roiAmount"           to newRoiAmount,
                        "totalPayoutAmount"   to newPayoutAmt,
                        "uplineReferralBonus" to FieldValue.increment(bonus),
                        "status"              to "active",
                        "lastTopUpDate"       to nowTs,
                        // reflect if referrer actually got direct profit (respecting block)  ← NEW
                        "referralReceivedDirectProfit" to (refActive && !refDirectBlocked),
                        "referrerId"          to (refUid ?: "")
                    ))
                }

                // Buyer transaction log
                val buyerTxRef = db.collection("transactions").document()
                tr.set(buyerTxRef, mapOf(
                    "userId"    to uid,            // buyer custom id
                    "type"      to "Plan Purchase",
                    "amount"    to amount,
                    "timestamp" to nowTs,
                    "planId"    to selected.id,
                    "planName"  to selected.name,
                    "accountId" to buyerAccRef.id
                ))

                // Change log (kept for audit)
                val beforeMap: Map<String, Any?> = activeSnap?.let {
                    mapOf(
                        "principal"          to (it.getDouble("principal") ?: 0.0),
                        "totalAccumulated"   to (it.getDouble("totalAccumulated") ?: 0.0),
                        "roiPercent"         to (it.getDouble("roiPercent") ?: 0.0),
                        "roiAmount"          to (it.getDouble("roiAmount") ?: 0.0),
                        "totalPayoutAmount"  to (it.getDouble("totalPayoutAmount") ?: 0.0),
                        "status"             to (it.getString("status") ?: "unknown")
                    )
                } ?: emptyMap()

                val afterMap: Map<String, Any?> =
                    if (isNew) {
                        mapOf(
                            "principal"          to amount,
                            "totalAccumulated"   to 0.0,
                            "roiPercent"         to roiPctPlan,
                            "roiAmount"          to amount * roiPctPlan / 100.0,
                            "totalPayoutAmount"  to amount * payoutPctPlan / 100.0,
                            "status"             to "active"
                        )
                    } else {
                        val oldPrincipal = activeSnap!!.getDouble("principal") ?: 0.0
                        val newPrincipal = oldPrincipal + amount
                        mapOf(
                            "principal"          to newPrincipal,
                            "totalAccumulated"   to (activeSnap.getDouble("totalAccumulated") ?: 0.0),
                            "roiPercent"         to roiPctEff,
                            "roiAmount"          to newPrincipal * roiPctEff / 100.0,
                            "totalPayoutAmount"  to newPrincipal * payoutPctEff / 100.0,
                            "status"             to "active"
                        )
                    }

                tr.set(db.collection("userPlanChangeLogs").document(), mapOf(
                    "action"        to if (isNew) "NEW" else "TOP_UP",
                    "userId"        to uid,           // buyer custom id
                    "planId"        to selected.id,
                    "planName"      to selected.name,
                    "amount"        to amount,
                    "roiPercent"    to roiPctPlan,
                    "directPercent" to dirPctPlan,
                    "payoutPercent" to payoutPctPlan,
                    "bonusApplied"  to amount * dirPctPlan / 100.0,
                    "traceId"       to trace,
                    "timestamp"     to FieldValue.serverTimestamp(),
                    "before"        to beforeMap,
                    "after"         to afterMap
                ))

                BuyResult.Success(selected.name, amount, isFirstPlanEver)
            }.await()

            Log.d(TAG, "[$trace] Transaction result=$result in ${(System.nanoTime() - t0) / 1e6} ms")

            // ───── POST-TXN: FCM pushes (no network inside transaction) ─────
            if (result is BuyResult.Success) {
                val accessToken = fetchFcmAccessToken() // uses your AccessToken helper
                if (!accessToken.isNullOrBlank()) {
                    // Buyer first-plan congratulations
                    if (result.firstPlanBonus && !buyerToken.isNullOrBlank()) {
                        Fcm().sendFCMNotification(
                            targetDeviceToken = buyerToken,
                            title = "Congratulations!",
                            body = "You received $FIRST_PLAN_TOKEN_BONUS free MXGN tokens on your first plan purchase.",
                            accessToken = accessToken
                        )
                    }

                    // Notify only eligible referrers (active + not blocked + positive bonus + token)
                    if (refIsActiveOutside && !refDirectBlockedOutside && potentialBonus > 0.0 && !refUserToken.isNullOrBlank()) {
                        val rounded = String.format("%,.2f", potentialBonus)
                        val title = "Referral bonus received"
                        val body  = "You earned $rounded from your referral's plan purchase."

                        Fcm().sendFCMNotification(
                            targetDeviceToken = refUserToken!!,
                            title = title,
                            body = body,
                            accessToken = accessToken
                        )
                    }
                    // else: don't send any notification
                } else {
                    Log.w(TAG, "[$trace] Skipping FCM push: access token is null/blank")
                }
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "[$trace] buyPlan EXCEPTION", e)
            BuyResult.Failure
        }
    }

    // Fetch ALL purchased plans for a user
    suspend fun fetchPurchasedPlans(uid: String): List<UserPlanUi> =
        withContext(Dispatchers.IO) {
            try {
                val snaps = db.collection("userPlans")
                    .whereEqualTo("userId", uid)
                    .get().await()

                return@withContext snaps.documents.mapNotNull { d ->
                    val base = d.toObject(UserPlan::class.java)?.apply { docId = d.id } ?: return@mapNotNull null
                    val planName = d.getString("planName") ?: ""
                    val directPct = d.getDouble("directPercent")
                    UserPlanUi(
                        userPlan = base,
                        planName = planName,
                        directPercent = directPct
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchPurchasedPlans EXCEPTION", e)
                emptyList()
            }
        }


    // ------- internal helpers -------

    /** Wrap your AsyncTask-based AccessToken helper into coroutines. */
    private suspend fun fetchFcmAccessToken(): String? =
        suspendCancellableCoroutine { cont ->
            AccessToken.getAccessTokenAsync(object : AccessToken.AccessTokenCallback {
                override fun onAccessTokenReceived(token: String?) {
                    if (cont.isActive) cont.resume(token)
                }
            })
        }
}
