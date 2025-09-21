package com.minerxgloble.minerxgloble.repos

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.minerxgloble.minerxgloble.models.Account
import com.minerxgloble.minerxgloble.utils.PrefService

/**
 * Live-listens to the current user's Account document in 'accounts'.
 * It queries by accounts.userId == PrefService(...).getUserId().
 */
class WalletRepo(context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val prefs = PrefService(context)

    private var reg: ListenerRegistration? = null
    private val _snapshot = MutableLiveData<WalletSnapshot?>()
    val snapshot: LiveData<WalletSnapshot?> get() = _snapshot

    data class WalletSnapshot(
        val docId: String,
        val raw: Map<String, Any>,
        val account: Account
    )

    fun start() {
        val uid = prefs.getUserId()
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "No userId found in PrefService; cannot listen to accounts.")
            _snapshot.postValue(null)
            return
        }
        stop()

        reg = db.collection("accounts")
            .whereEqualTo("userId", uid)
            .limit(1)
            .addSnapshotListener { qs, e ->
                if (e != null) {
                    Log.e(TAG, "Account listen failed: ${e.message}", e)
                    _snapshot.postValue(null)
                    return@addSnapshotListener
                }
                val doc = qs?.documents?.firstOrNull()
                if (doc == null) {
                    Log.w(TAG, "No accounts doc for userId=$uid")
                    _snapshot.postValue(null)
                    return@addSnapshotListener
                }
                val acc = Account.fromDocument(doc)
                _snapshot.postValue(
                    WalletSnapshot(
                        docId = doc.id,
                        raw = doc.data ?: emptyMap(),
                        account = acc
                    )
                )
            }
    }

    fun stop() {
        reg?.remove()
        reg = null
    }

    companion object {
        private const val TAG = "WalletRepo"
    }
}
