package com.minerxgloble.minerxgloble.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PersistentPrefService(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("persistent_prefs", Context.MODE_PRIVATE)

    fun isTourCompleted(key: String): Boolean {
        return prefs.getBoolean("tour_done_$key", false)
    }

    fun setTourCompleted(key: String) {
        prefs.edit { putBoolean("tour_done_$key", true) }
    }


}
