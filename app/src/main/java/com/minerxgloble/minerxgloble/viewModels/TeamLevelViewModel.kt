package com.minerxgloble.minerxgloble.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.minerxgloble.minerxgloble.models.TeamLevel
import com.minerxgloble.minerxgloble.repos.TeamLevelRepo
import kotlinx.coroutines.launch

class TeamLevelViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TeamLevelRepo(app)

    private val _levels  = MutableLiveData<List<TeamLevel>>(emptyList())
    private val _loading = MutableLiveData(false)
    private val _error   = MutableLiveData<String?>(null)
    private val _teamIncomeToday = MutableLiveData<Double>(0.0)
    private val _teamIncomeTotal = MutableLiveData<Double>(0.0)
    val levels : LiveData<List<TeamLevel>> get() = _levels
    val loading: LiveData<Boolean>         get() = _loading
    val error  : LiveData<String?>         get() = _error
    val teamIncomeToday: LiveData<Double>  get() = _teamIncomeToday
    val teamIncomeTotal: LiveData<Double>  get() = _teamIncomeTotal
    fun load() = viewModelScope.launch {
        _loading.value = true
        _error.value = null
        try {
            _levels.value = repo.fetchTeamLevels()
            loadEarnings()
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load team levels"
        } finally {
            _loading.value = false
        }
    }

    private suspend fun loadEarnings() {
               val acct = repo.fetchAccount()
                val todayKey = ymdPK() // implement same formatter as backend (PK day)
                val todays = if (acct.earnings.lastTeamDate == todayKey) acct.earnings.teamDaily else 0.0
                _teamIncomeToday.postValue(todays)
                _teamIncomeTotal.postValue(acct.earnings.teamProfit)
    }
    private fun ymdPK(): String {
        val tz = java.util.TimeZone.getTimeZone("Asia/Karachi")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd").apply { timeZone = tz }
        return fmt.format(java.util.Date())
    }

}
