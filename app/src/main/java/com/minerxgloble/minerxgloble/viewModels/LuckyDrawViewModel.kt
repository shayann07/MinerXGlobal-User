package com.minerxgloble.minerxgloble.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minerxgloble.minerxgloble.models.Winner
import com.minerxgloble.minerxgloble.repos.InvestResult
import com.minerxgloble.minerxgloble.repos.LuckyDrawRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LuckyDrawUiState(
    val loading: Boolean = true,
    val winners: List<Winner> = emptyList(),
    val myTotalInvested: Double = 0.0,
    val investInFlight: Boolean = false,
    val toast: String? = null
)

class LuckyDrawViewModel(
    private val repo: LuckyDrawRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(LuckyDrawUiState())
    val ui: StateFlow<LuckyDrawUiState> = _ui.asStateFlow()

    private var uid: String? = null
    private var totalsJob: Job? = null
    private var winnersJob: Job? = null

    fun bindUser(userId: String) {
        if (uid == userId) return
        uid = userId
        observeWinners()
        observeMyTotals()
    }

    private fun observeWinners() {
        winnersJob?.cancel()
        winnersJob = viewModelScope.launch {
            repo.observeWinners()
                .catch { _ui.update { it.copy(loading = false, winners = emptyList()) } }
                .collect { list ->
                    _ui.update { it.copy(loading = false, winners = list) }
                }
        }
    }

    private fun observeMyTotals() {
        val id = uid ?: return
        totalsJob?.cancel()
        totalsJob = viewModelScope.launch {
            repo.observeUserTotalInvested(id)
                .catch { _ui.update { it.copy(myTotalInvested = 0.0) } }
                .collect { total ->
                    _ui.update { it.copy(myTotalInvested = total) }
                }
        }
    }

    fun investOneDollar() {
        val id = uid ?: run {
            _ui.update { it.copy(toast = "User not available") }
            return
        }

        _ui.update { it.copy(investInFlight = true, toast = null) }
        viewModelScope.launch {
            when (val res = repo.investOneDollar(id)) {
                is InvestResult.Success ->
                    _ui.update { it.copy(investInFlight = false, toast = "Invested $1 successfully") }
                is InvestResult.Failure ->
                    _ui.update { it.copy(investInFlight = false, toast = res.message) }
            }
        }
    }

    fun clearToast() = _ui.update { it.copy(toast = null) }
}
