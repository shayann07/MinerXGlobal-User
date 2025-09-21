package com.minerxgloble.minerxgloble.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PrefService(context: Context) {

    companion object {
        fun clearAllPrefs(context: Context) {
            context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
                .edit(commit = true) { clear() }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE)

    private val KEY_USER_ID = "user_id"
    private val KEY_NAME = "name"
    private val KEY_EMAIL = "email"
    private val KEY_STATUS = "status"
    private val KEY_DEVICE_TOKEN = "deviceToken"
    private val KEY_PROFILE_IMG_URL = "profile_img_url"
    private val KEY_LOGGED_IN = "is_logged_in"
    private val KEY_REFERRER_ID = "referrerId" // stored from Firestore's "referralCode"

    // ---------- generic helpers ----------
    fun setString(key: String, value: String) = prefs.edit { putString(key, value) }
    fun getString(key: String): String? = prefs.getString(key, null)

    fun setBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        prefs.getBoolean(key, defaultValue)

    fun setInt(key: String, value: Int) = prefs.edit { putInt(key, value) }
    fun getInt(key: String, default: Int = -1): Int = prefs.getInt(key, default)

    fun setUserId(value: String) = setString(KEY_USER_ID, value)
    fun getUserId(): String? = getString(KEY_USER_ID)

    fun saveProfileImageUrl(url: String) = prefs.edit { putString(KEY_PROFILE_IMG_URL, url) }
    fun getProfileImageUrl(): String? = prefs.getString(KEY_PROFILE_IMG_URL, null)

    fun saveLogin() { setBoolean(KEY_LOGGED_IN, true) }
    fun checkLogin(): Boolean = getBoolean(KEY_LOGGED_IN, false)

    /** Save profile basics and (only) referralCode -> referrerId. */
    fun saveUserProfile(data: Map<String, Any?>) {
        prefs.edit {
            (data["userId"] ?: data["uid"])?.toString()?.let { putString(KEY_USER_ID, it) }
            data["name"]?.toString()?.let { putString(KEY_NAME, it) }
            data["email"]?.toString()?.let { putString(KEY_EMAIL, it) }
            data["status"]?.toString()?.let { putString(KEY_STATUS, it) }
            data["deviceToken"]?.toString()?.let { putString(KEY_DEVICE_TOKEN, it) }
            data["referralCode"]?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { putString(KEY_REFERRER_ID, it) }
        }
    }

    fun saveReferralFromLink(referrerId: String) {
        prefs.edit { putString("referrerId", referrerId) }
    }

    fun getReferralFromLink(): String? = prefs.getString("referrerId", null)

    /** Explicit setter/getter for referrer id (stored from referralCode). */
    fun setReferrerId(value: String?) {
        prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_REFERRER_ID) else putString(KEY_REFERRER_ID, value)
        }
    }
    fun getReferrerId(): String? = prefs.getString(KEY_REFERRER_ID, null)

    fun getName(): String? = prefs.getString(KEY_NAME, null)

    fun clearAllPrefs(){ prefs.edit { clear().apply() } }
}
