package com.minerxgloble.minerxgloble.viewModels.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.minerxgloble.minerxgloble.repos.BuyPlanRepo
import com.minerxgloble.minerxgloble.viewModels.BuyPlanViewModel


class BuyPlanViewModelFactory(
    private val repo: BuyPlanRepo
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BuyPlanViewModel(repo) as T
    }
}
