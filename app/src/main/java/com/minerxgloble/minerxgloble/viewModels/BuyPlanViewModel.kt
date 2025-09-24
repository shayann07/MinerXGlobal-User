package com.minerxgloble.minerxgloble.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.minerxgloble.minerxgloble.models.LightPlanPreview

import com.minerxgloble.minerxgloble.models.UserPlanUi
import com.minerxgloble.minerxgloble.repos.BuyPlanRepo
import com.minerxgloble.minerxgloble.utils.PlanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StatusFilter { ALL, ACTIVE, EXPIRED }
data class UiPlan(
    val id: String,
    val name: String,
    val minAmount: Double,
    val maxAmount: Double?,   // null = unlimited
    val payoutPercent: Double
)
class BuyPlanViewModel(
    private val repo: BuyPlanRepo = BuyPlanRepo()
) : ViewModel() {



    private val _plansCache = MutableLiveData<List<UiPlan>>(emptyList())
    val plansCache: LiveData<List<UiPlan>> = _plansCache


    private var plansListener: ListenerRegistration? = null

    private val _buyPlanStatus = MutableLiveData<PlanStatus?>()
    val buyPlanStatus: LiveData<PlanStatus?> = _buyPlanStatus



    private val _lastPurchasedPlanName = MutableLiveData<String?>()
    val lastPurchasedPlanName: LiveData<String?> = _lastPurchasedPlanName

    private val _lastPurchasedAmount = MutableLiveData<Double?>()
    val lastPurchasedAmount: LiveData<Double?> = _lastPurchasedAmount

    // 👇 NEW: notify UI if first-plan bonus happened

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


    // Call this once (e.g., in init { ... } of your VM or from the fragment on first view)
    fun startPlansCache(db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
        if (plansListener != null) return // already listening
        plansListener = db.collection("plans").addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { d ->
                val name   = d.getString("planName") ?: return@mapNotNull null
                val minAmt = d.getDouble("minAmount") ?: return@mapNotNull null
                val maxAmt = d.getDouble("maxAmount")
                val payout = d.getDouble("totalPayout") ?: return@mapNotNull null
                UiPlan(d.id, name, minAmt, maxAmt, payout)
            }?.sortedBy { it.minAmount } ?: emptyList()
            _plansCache.postValue(list)
        }
    }

    override fun onCleared() {
        super.onCleared()
        plansListener?.remove()
        plansListener = null
    }

    /** Synchronous, zero-network plan pick from cache */
    fun pickPlanFromCache(amount: Double): UiPlan? {
        val plans = _plansCache.value.orEmpty()
        if (amount <= 0.0 || plans.isEmpty()) return null
        // same rule: amount in [min, max], choose highest min that matches
        return plans.filter { p -> amount >= p.minAmount && (p.maxAmount == null || amount <= p.maxAmount) }
            .maxByOrNull { it.minAmount }
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
