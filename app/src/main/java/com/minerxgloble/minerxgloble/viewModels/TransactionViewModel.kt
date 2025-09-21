package com.minerxgloble.minerxgloble.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minerxgloble.minerxgloble.models.TransactionModel
import com.minerxgloble.minerxgloble.repos.TransactionRepo
import kotlinx.coroutines.launch

class TransactionViewModel : ViewModel() {

    private val repo = TransactionRepo()

    private val _withdrawals = MutableLiveData<List<TransactionModel>>()
    val withdrawals: LiveData<List<TransactionModel>> = _withdrawals

    private val _deposits = MutableLiveData<List<TransactionModel>>()
    val deposits: LiveData<List<TransactionModel>> = _deposits

    private val _allTransactions = MutableLiveData<List<TransactionModel>>()
    val allTransactions: LiveData<List<TransactionModel>> = _allTransactions

    private val _mergedTxns = MutableLiveData<List<TransactionModel>>()
    val mergedTxns: LiveData<List<TransactionModel>> = _mergedTxns

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error


    fun fetchDeposits(userId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val txList = repo.getDepositTransactions(userId) ?: emptyList()
                _deposits.value = txList
            } catch (e: Exception) {
                _error.value = "Failed to load deposits: ${e.localizedMessage}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun fetchWithdrawalTransactions(userId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val list = repo.getWithdrawalTransactions(userId) ?: emptyList()
                _withdrawals.value = list
            } catch (e: Exception) {
                _error.value = "Failed to load withdrawal txns: ${e.localizedMessage}"
            } finally {
                _loading.value = false
            }
        }
    }

    /** 🔹 Fetch merged transactions */
    fun fetchMergedTransactions(userId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val list = repo.getMergedTransactions(userId)
                _mergedTxns.value = list
                _allTransactions.value = list
            } catch (e: Exception) {
                _error.value = "Failed to load transactions: ${e.localizedMessage}"
            } finally {
                _loading.value = false
            }
        }
    }

    /** 🔹 Apply filter by type */
    fun applyFilter(type: String?) {
        val current = _mergedTxns.value ?: return
        if (type.isNullOrEmpty() || type == "All") {
            _allTransactions.value = current
        } else {
            _allTransactions.value = current.filter { it.type.equals(type, true) }
        }
    }
}
