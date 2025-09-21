package com.minerxgloble.minerxgloble.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minerxgloble.minerxgloble.models.RankFilter
import com.minerxgloble.minerxgloble.models.RankItemState
import com.minerxgloble.minerxgloble.models.RankTable
import com.minerxgloble.minerxgloble.models.RankUiState
import com.minerxgloble.minerxgloble.models.RankUiStatus
import com.minerxgloble.minerxgloble.repos.RankRepo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch





class RankViewModel(private val repo: RankRepo, private val userId: String): ViewModel() {

    private val _state = MutableStateFlow(RankUiState(loading = true))
    val state: StateFlow<RankUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(loading = true, error = null) }

                val (direct, indirect) = repo.computeBusiness(userId)  // always latest
                val claimed = repo.fetchClaimed(userId)

                val items = buildItems(direct, indirect, claimed)
                _state.value = RankUiState(direct, indirect, claimed, items, _state.value.filter, false, null)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Error") }
            }
        }
    }

    private fun buildItems(direct: Double, indirect: Double, claimed: Set<String>): List<RankItemState> {
        val order = RankTable.rows
        val claimedBefore = claimed // set
        val out = mutableListOf<RankItemState>()

        // Sequential rule: a rank is claimable only if (a) thresholds met AND (b) all prior ranks already claimed
        order.forEachIndexed { idx, def ->
            val thresholdsMet = direct >= def.directRequired && indirect >= def.indirectRequired
            val allPrevClaimed = order.take(idx).all { claimedBefore.contains(it.id) }
            val status = when {
                claimedBefore.contains(def.id) -> RankUiStatus.CLAIMED
                thresholdsMet && allPrevClaimed -> RankUiStatus.CLAIMABLE
                else -> RankUiStatus.LOCKED
            }
            out += RankItemState(def, status)
        }
        return out
    }

    fun setFilter(f: RankFilter) {
        _state.update { it.copy(filter = f) }
    }

    fun visibleItems(): List<RankItemState> {
        val s = _state.value
        return when (s.filter) {
            RankFilter.ALL -> s.items
            RankFilter.UNLOCKED -> s.items.filter { it.status == RankUiStatus.CLAIMABLE }
            RankFilter.LOCKED -> s.items.filter { it.status == RankUiStatus.LOCKED }
            RankFilter.CLAIMED -> s.items.filter { it.status == RankUiStatus.CLAIMED }
        }
    }

    fun claim(rankId: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(loading = true, error = null) }
                val claimed = repo.claim(userId, rankId)
                // After claim, recompute business + status so UI shows next unlocks correctly
                val (direct, indirect) = repo.computeBusiness(userId)
                val items = buildItems(direct, indirect, claimed)
                _state.value = _state.value.copy(
                    direct = direct, indirect = indirect,
                    claimed = claimed, items = items, loading = false
                )
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Claim failed") }
            }
        }
    }
}
