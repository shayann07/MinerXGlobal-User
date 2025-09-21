package com.minerxgloble.minerxgloble.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.minerxgloble.minerxgloble.models.DocumentItem
import com.minerxgloble.minerxgloble.models.NetworkStat
import com.minerxgloble.minerxgloble.repos.NetworkStatsRepo
import kotlinx.coroutines.launch

class NetworkStatsViewModel : ViewModel() {

    private val repo = NetworkStatsRepo()

    // Treat null as "loading" to avoid flashing placeholders from VM
    private val _stats = MutableLiveData<List<NetworkStat>?>(null)
    val stats: LiveData<List<NetworkStat>?> = _stats

    private var statsListener: ListenerRegistration? = null

    fun loadStats() {
        viewModelScope.launch {
            val data = repo.fetchStats()
            data?.let { postStats(it) }
        }
    }

    fun startStatsListener() {
        stopStatsListener()
        statsListener = repo.listenStats { data ->
            if (data != null) postStats(data)
        }
    }

    fun stopStatsListener() {
        statsListener?.remove()
        statsListener = null
    }

    private fun postStats(data: Map<String, Any>) {
        // Normalize missing numbers -> "0"
        val list = listOf(
            NetworkStat((data["totalMembers"] ?: 0).toString(), "All Members in Network"),
            NetworkStat((data["totalWithdrawal"] ?: 0).toString(), "Total Withdrawal"),
            NetworkStat((data["totalInvestment"] ?: 0).toString(), "Total Investment")
        )
        _stats.postValue(list)
    }

    // Documents
    private val _docs = MutableLiveData<List<DocumentItem>>()
    val docs: LiveData<List<DocumentItem>> = _docs

    fun load() {
        viewModelScope.launch {
            _docs.postValue(repo.fetchDocuments())
        }
    }

    override fun onCleared() {
        stopStatsListener()
        super.onCleared()
    }
}
