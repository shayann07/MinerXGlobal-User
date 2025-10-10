package com.minerxgloble.minerxgloble.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.firebase.firestore.FirebaseFirestore
import com.minerxgloble.minerxgloble.repos.WalletRepo
import java.text.NumberFormat
import java.util.Locale

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    // ─────────────────────────────────────────────────────────────
    // Data sources
    // ─────────────────────────────────────────────────────────────
    private val repo = WalletRepo(application)
    val wallet: LiveData<WalletRepo.WalletSnapshot?> = repo.snapshot

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val fmt = NumberFormat.getCurrencyInstance(Locale.US)

    // ─────────────────────────────────────────────────────────────
    // Token → USD live computation
    // Firestore path:  mxgToken / tokenRate { rate: <number> }  (USD per token)
    // ─────────────────────────────────────────────────────────────
    private val TOKEN_RATE_COLLECTION = "mxgToken"
    private var tokenRate: Double = 0.0
    private var lastTokens: Double = 0.0

    private val _tokenUsd = MutableLiveData(0.0)
    val tokenUsd: LiveData<Double> = _tokenUsd

    // Keep a strong reference so we can remove it in onCleared()
    private val walletForeverObserver = Observer<WalletRepo.WalletSnapshot?> { snap ->
        if (snap != null) {
            // Primary token location
            var tokens = nestedDouble(snap.raw, "earnings.tokens")
            // Optional fallbacks (won't hurt if not present)
            if (tokens == 0.0) tokens = nestedDouble(snap.raw, "tokens")
            if (tokens == 0.0) tokens = nestedDouble(snap.raw, "rewards.tokens")

            lastTokens = tokens
            recomputeTokenUsd()
        }
    }

    init {
        // Start repo listeners
        repo.start()

        // Observe wallet snapshots forever inside the VM
        wallet.observeForever(walletForeverObserver)

        // Live-watch the tokenRate doc; tolerate any numeric type (Long/Double/Int)
        db.collection(TOKEN_RATE_COLLECTION)
            .document("tokenRate")
            .addSnapshotListener { snap, _ ->
                val num = snap?.get("rate") as? Number
                val newRate = num?.toDouble() ?: 0.0
                if (newRate != tokenRate) {
                    tokenRate = newRate
                    recomputeTokenUsd()
                }
            }
    }

    private fun recomputeTokenUsd() {
        _tokenUsd.postValue(lastTokens * tokenRate)
    }

    // ─────────────────────────────────────────────────────────────
    // Public helpers used by the fragment
    // ─────────────────────────────────────────────────────────────
    fun money(v: Double?): String = fmt.format((v ?: 0.0))

    /** Reads nested doubles like "earnings.totalWithdrawn" from a raw map. */
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

    // Replace this with a direct return if totalEarned is already net:
    fun totalEarningsBalance(s: WalletRepo.WalletSnapshot): Double =
        s.account.earnings.totalEarned


    override fun onCleared() {
        // Detach the forever observer to avoid leaks
        wallet.removeObserver(walletForeverObserver)
        repo.stop()
        super.onCleared()
    }
}
