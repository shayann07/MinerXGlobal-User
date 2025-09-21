package com.minerxgloble.minerxgloble.models

data class RankDef(
    val id: String,
    val title: String,
    val directRequired: Double,
    val indirectRequired: Double,
    val reward: Double
)

enum class RankUiStatus { LOCKED, CLAIMABLE, CLAIMED }

data class RankItemState(
    val def: RankDef,
    val status: RankUiStatus
)

object RankTable {
    val rows = listOf(
        RankDef("SILVER",   "Silver",   500.0,   3000.0,   100.0),
        RankDef("GOLD",     "Gold",     2000.0,  8000.0,   250.0),
        RankDef("PLATINUM", "Platinum", 5000.0,  15000.0,  600.0),
        RankDef("DIAMOND",  "Diamond",  10000.0, 30000.0, 1500.0),
        RankDef("MASTER",   "Master",   25000.0, 100000.0, 5000.0),
        RankDef("GRANDAM",  "Grandam",  60000.0, 200000.0,10000.0),
        RankDef("ELITE",    "Elite",    150000.0,500000.0,30000.0),
        RankDef("LEGEND",   "Legend",   500000.0,2000000.0,150000.0)
    )
}
