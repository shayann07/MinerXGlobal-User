// TeamLevelRepo.kt  (drop-in)
package com.minerxgloble.minerxgloble.repos

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.minerxgloble.minerxgloble.models.Account
import com.minerxgloble.minerxgloble.models.TeamLevel
import com.minerxgloble.minerxgloble.models.TeamUser
import com.minerxgloble.minerxgloble.utils.PrefService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TeamLevelRepo(private val context: Context) {

    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    /** Read-only: fetches levels from computeTeamLevelsAndCreditProfit */
    suspend fun fetchTeamLevels(): List<TeamLevel> = withContext(Dispatchers.IO) {
        val userId = PrefService(context).getUserId() ?: error("User ID not found in Prefs")

        val res = functions
            .getHttpsCallable("computeTeamLevelsAndCreditProfit")
            .call(mapOf("userId" to userId))
            .await()
            .data as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val levels = (res["levels"] as? List<Map<String, Any?>>).orEmpty()

        // Map to your existing TeamLevel model
        levels.map { m ->
            val levelNum          = (m["level"] as Number).toInt()
            val totalUsers        = (m["totalUsers"] as? Number)?.toInt() ?: 0
            val activeUsers       = (m["activeUsers"] as? Number)?.toInt() ?: 0
            val inactiveUsers     = (m["inactiveUsers"] as? Number)?.toInt() ?: 0
            val totalDeposit      = (m["totalDeposit"] as? Number)?.toDouble() ?: 0.0
            val totalInvestedActive = (m["levelActiveInvestedTotal"] as? Number)?.toDouble() ?: 0.0
            val levelUnlocked     = (m["levelUnlocked"] as? Boolean) ?: false
            // 🔽 Map the per-level users to your minimal model (userId, name, status)
            @Suppress("UNCHECKED_CAST")
            val rawUsers = (m["users"] as? List<Map<String, Any?>>).orEmpty()
            val users: List<TeamUser> = rawUsers.map { u ->
                val uid   = (u["uid"] as? String).orEmpty()
                val first = (u["firstName"] as? String).orEmpty()
                val last  = (u["lastName"] as? String).orEmpty()
                val name  = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { uid }
                val status = ((u["status"] as? String) ?: "").lowercase()
                val activeInv = (u["activeInvested"] as? Number)?.toDouble() ?: 0.0  // NEW
                TeamUser(userId = uid, name = name, status = status,activeInv)
            }
            // Your adapter expects 'investedAmount' → use totalDeposit for display
            TeamLevel(
                level = levelNum,
                totalUsers = totalUsers,
                activeUsers = activeUsers,
                inactiveUsers = inactiveUsers,
                totalDeposit = totalDeposit,
                totalBuyingProfit = 0.0,          // not provided by UI callable
                investedAmount = totalInvestedActive,    // show as "Invested Amount"
                levelUnlocked = levelUnlocked,
                users = users
            )
        }.sortedBy { it.level }
    }

    suspend fun fetchAccount(): Account = withContext(Dispatchers.IO) {
              val uid = PrefService(context).getUserId() ?: error("User ID not found in Prefs")
               val snap = db.collection("accounts").whereEqualTo("userId", uid).limit(1).get().await()
               if (snap.isEmpty) return@withContext Account(userId = uid)
               Account.fromDocument(snap.documents.first())
           }
}
