package com.minerxgloble.minerxgloble.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.minerxgloble.minerxgloble.repos.WalletRepo
import java.text.NumberFormat
import java.util.Locale

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = WalletRepo(application)

    val wallet: LiveData<WalletRepo.WalletSnapshot?> = repo.snapshot

    private val fmt = NumberFormat.getCurrencyInstance(Locale.US)

    init {
        repo.start()
    }

    override fun onCleared() {
        super.onCleared()
        repo.stop()
    }

    fun money(v: Double?): String = fmt.format((v ?: 0.0))

    /** Reads nested doubles like "earnings.totalWithdrawn" from raw map. */
    fun nestedDouble(raw: Map<String, Any>, path: String): Double {
        var cur: Any? = raw
        for (key in path.split(".")) {
            cur = (cur as? Map<*, *>)?.get(key)
        }
        return (cur as? Number)?.toDouble() ?: 0.0
    }

    /** Earnings balance shown in the Earnings wallet card. */
    fun earningsBalance(s: WalletRepo.WalletSnapshot): Double {

        val earnedToDate = s.account.earnings.totalEarnedToDate

        return earnedToDate
    }
    fun totalEarningsBalance(s: WalletRepo.WalletSnapshot): Double {

        val totalEarned = s.account.earnings.totalEarned
        val withdrawn = nestedDouble(s.raw, "earnings.totalWithdrawn")
        return totalEarned-withdrawn
    }
}
