package com.minerxgloble.minerxgloble.repos

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.minerxgloble.minerxgloble.models.DocumentItem
import kotlinx.coroutines.tasks.await

class NetworkStatsRepo {

    private val db = FirebaseFirestore.getInstance()

    // Existing one-shot fetch (keep it)
    suspend fun fetchStats(): Map<String, Any>? {
        val doc = db.collection("networkStats").document("main").get().await()
        return doc.data
    }

    // NEW: realtime listener
    fun listenStats(onUpdate: (Map<String, Any>?) -> Unit): ListenerRegistration {
        return db.collection("networkStats").document("main")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("NetworkStatsRepo", "listenStats error", e)
                    onUpdate(null)
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.data)
            }
    }
    // ADD in the same class: NetworkStatsRepo
    suspend fun fetchTokenRate(): Double? {
        val snap = db.collection("mxgToken")
            .document("tokenRate")
            .get()
            .await()

        // Accepts number stored as int/double/string; normalize to Double
        val raw = snap.get("rate")
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
    }

    fun listenTokenRate(onUpdate: (Double?) -> Unit): ListenerRegistration {
        return db.collection("mxgToken")
            .document("tokenRate")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("NetworkStatsRepo", "listenTokenRate error", e)
                    onUpdate(null)
                    return@addSnapshotListener
                }
                val raw = snapshot?.get("rate")
                val rate = when (raw) {
                    is Number -> raw.toDouble()
                    is String -> raw.toDoubleOrNull()
                    else -> null
                }
                onUpdate(rate)
            }
    }


    suspend fun fetchDocuments(): List<DocumentItem> {
        val snap = db.collection("documents").get().await()
        Log.d("DocsRepo", "Fetched ${snap.size()} documents")
        for (doc in snap.documents) {
            Log.d("DocsRepo", "Doc data: ${doc.data}")
        }
        return snap.documents.mapNotNull { it.toObject(DocumentItem::class.java) }
    }
}
