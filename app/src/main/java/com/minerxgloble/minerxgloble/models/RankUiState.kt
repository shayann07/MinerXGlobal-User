package com.minerxgloble.minerxgloble.models



data class RankUiState(
    val direct: Double = 0.0,
    val indirect: Double = 0.0,
    val claimed: Set<String> = emptySet(),
    val items: List<RankItemState> = emptyList(),
    val filter: RankFilter = RankFilter.ALL,
    val loading: Boolean = false,
    val error: String? = null
)
enum class RankFilter { ALL, UNLOCKED, LOCKED, CLAIMED }