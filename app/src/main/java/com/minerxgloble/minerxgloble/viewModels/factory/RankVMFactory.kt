package com.minerxgloble.minerxgloble.viewModels.factory

import com.minerxgloble.minerxgloble.repos.RankRepo
import com.minerxgloble.minerxgloble.viewModels.RankViewModel

class RankVMFactory(
    private val repo: RankRepo,
    private val userId: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RankViewModel(repo, userId) as T
    }
}