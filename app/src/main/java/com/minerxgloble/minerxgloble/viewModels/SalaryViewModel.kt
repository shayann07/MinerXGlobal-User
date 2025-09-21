package com.minerxgloble.minerxgloble.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.minerxgloble.minerxgloble.repos.SalaryRepo
import kotlinx.coroutines.launch

sealed class SalaryUiState {
    object Idle : SalaryUiState()
    object Loading : SalaryUiState()
    data class Success(val data: Map<String, Any?>) : SalaryUiState()
    data class Error(val message: String) : SalaryUiState()
}

class SalaryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SalaryRepo(app)

    private val _state = MutableLiveData<SalaryUiState>(SalaryUiState.Idle)
    val state: LiveData<SalaryUiState> = _state

    fun loadPreview(userId: String, monthKey: String? = null) {
        if (userId.isBlank()) {
            _state.value = SalaryUiState.Error("User id is missing")
            return
        }
        _state.value = SalaryUiState.Loading
        viewModelScope.launch {
            try {
                val data = repo.getPreview(userId, monthKey)
                _state.value = SalaryUiState.Success(data)
            } catch (t: Throwable) {
                _state.value = SalaryUiState.Error(t.message ?: "Failed to load salary preview")
            }
        }
    }
}
