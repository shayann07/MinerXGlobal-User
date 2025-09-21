package com.minerxgloble.minerxgloble.viewModels.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.minerxgloble.minerxgloble.repos.LuckyDrawRepository
import com.minerxgloble.minerxgloble.viewModels.LuckyDrawViewModel

class LuckyDrawVMFactory(
    private val repo: LuckyDrawRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LuckyDrawViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LuckyDrawViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
