package com.minerxgloble.minerxgloble.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minerxgloble.minerxgloble.models.UserPlanUi
import com.minerxgloble.minerxgloble.repos.BuyPlanRepo
import com.minerxgloble.minerxgloble.utils.PlanStatus
import kotlinx.coroutines.launch

enum class StatusFilter { ALL, ACTIVE, EXPIRED }

class BuyPlanViewModel(
    private val repo: BuyPlanRepo = BuyPlanRepo()
) : ViewModel() {

    private val _buyPlanStatus = MutableLiveData<PlanStatus?>()
    val buyPlanStatus: LiveData<PlanStatus?> = _buyPlanStatus

    private val _lastPurchasedPlanName = MutableLiveData<String?>()
    val lastPurchasedPlanName: LiveData<String?> = _lastPurchasedPlanName

    private val _lastPurchasedAmount = MutableLiveData<Double?>()
    val lastPurchasedAmount: LiveData<Double?> = _lastPurchasedAmount

    // 👇 NEW: notify UI if first-plan bonus happened
    private val _firstPlanBonusAwarded = MutableLiveData<Boolean?>()
    val firstPlanBonusAwarded: LiveData<Boolean?> = _firstPlanBonusAwarded

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun buyPlan(userId: String, amount: Double) {
        if (userId.isBlank()) {
            _buyPlanStatus.value = PlanStatus.NoUserFound
            return
        }
        if (amount <= 0.0) {
            _buyPlanStatus.value = PlanStatus.InvalidAmount
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                when (val result = repo.buyPlan(userId, amount)) {
                    is BuyPlanRepo.BuyResult.Success -> {
                        _lastPurchasedPlanName.value = result.planName
                        _lastPurchasedAmount.value = result.amount
                        _firstPlanBonusAwarded.value = result.firstPlanBonus   // 👈 set flag
                        _buyPlanStatus.value = PlanStatus.Success
                        loadPurchasedPlans(userId)
                    }
                    BuyPlanRepo.BuyResult.MinInvestError -> _buyPlanStatus.value = PlanStatus.NoPlanFound
                    BuyPlanRepo.BuyResult.InsufficientBalance -> _buyPlanStatus.value = PlanStatus.NotEnoughBalance
                    BuyPlanRepo.BuyResult.Failure -> _buyPlanStatus.value = PlanStatus.Error
                }

            } catch (_: Throwable) {
                _buyPlanStatus.value = PlanStatus.Error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearStatus() { _buyPlanStatus.value = null }

    // allow UI to clear the bonus event after consuming
    fun clearFirstPlanBonusFlag() { _firstPlanBonusAwarded.value = null }

    private val _plans = MutableLiveData<List<UserPlanUi>>(emptyList())
    val plans: LiveData<List<UserPlanUi>> = _plans

    private val _isPlansLoading = MutableLiveData(false)
    val isPlansLoading: LiveData<Boolean> = _isPlansLoading

    private val _appliedFilter = MutableLiveData(StatusFilter.ALL)
    val appliedFilter: LiveData<StatusFilter> = _appliedFilter

    private var allPlans: List<UserPlanUi> = emptyList()
    private var lastUserId: String? = null

    fun loadPurchasedPlans(userId: String) {
        if (userId.isBlank()) {
            _plans.value = emptyList()
            lastUserId = null
            return
        }
        lastUserId = userId
        _isPlansLoading.value = true
        viewModelScope.launch {
            try {
                allPlans = repo.fetchPurchasedPlans(userId)
                applyFilter(_appliedFilter.value ?: StatusFilter.ALL)
            } catch (_: Throwable) {
                _plans.value = emptyList()
            } finally {
                _isPlansLoading.value = false
            }
        }
    }

    fun setFilter(newFilter: StatusFilter) {
        if (_appliedFilter.value == newFilter) return
        _appliedFilter.value = newFilter
        applyFilter(newFilter)
    }

    fun refreshPurchasedPlans() {
        lastUserId?.let { loadPurchasedPlans(it) }
    }

    private fun applyFilter(filter: StatusFilter) {
        var list = when (filter) {
            StatusFilter.ALL     -> allPlans
            StatusFilter.ACTIVE  -> allPlans.filter { it.userPlan.status.equals("active",  ignoreCase = true) }
            StatusFilter.EXPIRED -> allPlans.filter { it.userPlan.status.equals("expired", ignoreCase = true) }
        }
        list = list.sortedByDescending { it.userPlan.buyDate?.toDate()?.time ?: 0L }
        _plans.value = list
    }
}
