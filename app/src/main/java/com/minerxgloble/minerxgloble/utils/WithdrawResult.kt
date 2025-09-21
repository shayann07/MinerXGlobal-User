package com.minerxgloble.minerxgloble.utils

sealed class WithdrawResult {
    object Success : WithdrawResult()
    data class Error(val message: String) : WithdrawResult()
    object NotEnoughBalance : WithdrawResult()
    object PendingExists : WithdrawResult()
    object AccountNotFound : WithdrawResult()
    object UserBlocked : WithdrawResult()
}
