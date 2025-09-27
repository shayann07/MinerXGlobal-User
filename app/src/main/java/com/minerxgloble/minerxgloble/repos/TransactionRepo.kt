package com.minerxgloble.minerxgloble.repos

import android.util.Log
import com.minerxgloble.minerxgloble.models.TransactionModel
import com.minerxgloble.minerxgloble.utils.TxnConstants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class TransactionRepo {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getTransactionsForUser(userId: String): List<TransactionModel>? {
        return try {
            val snapshot =
                firestore.collection("transactions").whereEqualTo("userId", userId).get().await()

            snapshot.documents.mapNotNull {
                it.toObject(TransactionModel::class.java)?.copy(id = it.id)
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "Error fetching transactions: $e", e)
            null
        }
    }

    suspend fun getWithdrawalTransactions(userId: String): List<TransactionModel>? {
        return try {
            val snapshot = firestore.collection("withdrawals")
                .whereEqualTo("userId", userId)
                .get().await()

            snapshot.documents.map { doc ->
                TransactionModel(
                    id = doc.id, // use DOC ID, not the field
                    address = doc.getString("address") ?: "",
                    amount = doc.getDouble("amountGross") ?: 0.0,
                    balanceUpdated = doc.getBoolean("balanceUpdated") ?: false,
                    status = doc.getString("status") ?: "",
                    timestamp = doc.getTimestamp("timestamp"),
                    type = doc.getString("type") ?: "",
                    userId = doc.getString("userId") ?: ""
                )
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "Error fetching withdrawals: $e", e)
            null
        }
    }


    suspend fun getDepositTransactions(userId: String): List<TransactionModel>? {
        return try {
            val snapshot = firestore.collection("deposits")
                .whereEqualTo(TxnConstants.FIELD_USER_ID, userId)
                .get().await()

            snapshot.documents.mapNotNull {
                it.toObject(TransactionModel::class.java)?.copy(id = it.id)
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "Error fetching deposits: $e", e)
            null
        }
    }

    /** 🔹 Merge deposits, withdrawals, and transactions */
    suspend fun getMergedTransactions(userId: String): List<TransactionModel> {
        val deposits = getDepositTransactions(userId) ?: emptyList()
        val withdrawals = getWithdrawalTransactions(userId) ?: emptyList()
        val all = getTransactionsForUser(userId) ?: emptyList()

        return (deposits + withdrawals + all)
            .sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
    }
}
