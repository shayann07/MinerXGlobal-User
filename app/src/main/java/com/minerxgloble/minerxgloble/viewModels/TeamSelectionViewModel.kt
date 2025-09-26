package com.minerxgloble.minerxgloble.viewModels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.minerxgloble.minerxgloble.models.TeamLevel

class TeamSelectionViewModel : ViewModel() {
    val selectedLevel = MutableLiveData<TeamLevel?>()
    fun select(level: TeamLevel) { selectedLevel.value = level }
}
