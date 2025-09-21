// TeamLevelRepo.kt  (drop-in)
package com.minerxgloble.minerxgloble.repos

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import com.minerxgloble.minerxgloble.models.TeamLevel
import com.minerxgloble.minerxgloble.utils.PrefService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TeamLevelRepo(private val context: Context) {

    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

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
            val levelUnlocked     = (m["levelUnlocked"] as? Boolean) ?: false

            // Your adapter expects 'investedAmount' → use totalDeposit for display
            TeamLevel(
                level = levelNum,
                totalUsers = totalUsers,
                activeUsers = activeUsers,
                inactiveUsers = inactiveUsers,
                totalDeposit = totalDeposit,
                totalBuyingProfit = 0.0,          // not provided by UI callable
                investedAmount = totalDeposit,    // show as "Invested Amount"
                levelUnlocked = levelUnlocked
            )
        }.sortedBy { it.level }
    }
}
