// app/src/main/java/com/minerxgloble/minerxgloble/viewModels/ProfileViewModel.kt
package com.minerxgloble.minerxgloble.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.minerxgloble.minerxgloble.repos.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps the current user's profile "hot" so the drawer shows instantly.
 * - Set authUid with setAuthUid(...) (call after login and in onResume()).
 * - Use ensureProfileFresh() instead of calling load on every drawer open.
 */
class ProfileViewModel(
    private val authRepo: AuthRepository,
    initialAuthUid: String // Firebase Auth UID (document id in /users)
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _profileData = MutableLiveData<Map<String, Any?>?>()
    val profileData: LiveData<Map<String, Any?>?> = _profileData

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _updateSuccess = MutableLiveData<Boolean>()
    val updateSuccess: LiveData<Boolean> = _updateSuccess

    private val _phoneUpdateSuccess = MutableLiveData<Boolean>()
    val phoneUpdateSuccess: LiveData<Boolean> = _phoneUpdateSuccess

    /** Active identifiers */
    private var authUid: String = initialAuthUid       // primary (doc id)
    private var mxgUserCode: String = ""               // optional fallback: MXG-XXXXXX

    /** Warmth / throttle */
    private var lastLoadedAuthUid: String? = null
    private var lastLoadedAtMs: Long = 0L
    private var currentLoadJob: Job? = null
    private var profileReg: ListenerRegistration? = null

    /**
     * Call this whenever the logged-in user changes (or from onResume defensively).
     * Optionally pass mxg code for legacy fallback lookups.
     */
    fun setAuthUid(newAuthUid: String?, mxgCode: String? = null, attachListener: Boolean = true) {
        val uid = newAuthUid.orEmpty()
        val mxg = mxgCode.orEmpty()
        val changed = (uid != authUid) || (mxg != mxgUserCode)
        authUid = uid
        mxgUserCode = mxg
        if (attachListener && changed) attachProfileListener()
        if (changed) {
            lastLoadedAuthUid = null
            lastLoadedAtMs = 0L
        }
    }

    /** Attach a single snapshot listener so VM stays hot in memory. */
    private fun attachProfileListener() {
        profileReg?.remove()
        if (authUid.isBlank()) {
            _profileData.postValue(null)
            return
        }
        profileReg = authRepo.listenProfileByAuthUid(authUid) { map ->
            _profileData.postValue(map)
            lastLoadedAuthUid = authUid
            lastLoadedAtMs = System.currentTimeMillis()
        }
    }

    /**
     * Prefer this from UI (e.g., on drawer open). Loads only if stale or user changed.
     */
    fun ensureProfileFresh(maxAgeMs: Long = 5_000) {
        val now = System.currentTimeMillis()
        val sameUser = (lastLoadedAuthUid == authUid)
        val fresh = sameUser && (now - lastLoadedAtMs) < maxAgeMs
        if (!fresh) loadProfile()
    }

    /** Cache → Server, plus legacy fallbacks if authUid doc doesn’t exist. */
    fun loadProfile() {
        if (authUid.isBlank() && mxgUserCode.isBlank()) {
            _profileData.value = null
            return
        }
        currentLoadJob?.cancel()
        currentLoadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1) Fast path from local cache by authUid
                if (authUid.isNotBlank()) {
                    authRepo.fetchProfileByAuthUid(authUid, Source.CACHE)?.let {
                        _profileData.value = it
                    }
                }

                // 2) Fresh from server by authUid, else legacy fallback
                val fresh = if (authUid.isNotBlank())
                    authRepo.fetchProfileByAuthUid(authUid, Source.SERVER)
                else null

                val finalData = fresh ?: authRepo.fetchProfileSmart(authUid, mxgUserCode)
                if (finalData != null) {
                    _profileData.value = finalData
                    lastLoadedAuthUid = authUid
                    lastLoadedAtMs = System.currentTimeMillis()
                } else {
                    _error.value = "Profile not found"
                }
            } catch (ex: Exception) {
                _error.value = ex.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update profile fields (legacy path expects MXG user code for write).
     * Keep your repo's update APIs as-is; they still locate by field "uid".
     */
    fun updateProfile(
        name: String?,
        lastName: String?,
        dob: String?,
        phone: String?
    ) {
        if (mxgUserCode.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val ok = authRepo.updateProfile(mxgUserCode, name, lastName, dob, phone)
                _updateSuccess.value = ok
                if (ok) {
                    val cur = (_profileData.value ?: emptyMap()).toMutableMap()
                    name?.let { cur["name"] = it }
                    lastName?.let { cur["lastName"] = it }
                    dob?.let { cur["dob"] = it }
                    phone?.let {
                        cur["phoneNumber"] = it
                        cur["phoneNo"] = it
                    }
                    _profileData.value = cur
                    lastLoadedAuthUid = authUid
                    lastLoadedAtMs = System.currentTimeMillis()
                } else {
                    _error.value = "Update failed"
                }
            } catch (ex: Exception) {
                _error.value = ex.message
                _updateSuccess.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Update only phone number (legacy write path). */
    fun updatePhoneNumber(newPhone: String) {
        if (mxgUserCode.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val ok = authRepo.updatePhoneNumber(mxgUserCode, newPhone)
                _phoneUpdateSuccess.value = ok
                if (ok) {
                    val cur = (_profileData.value ?: emptyMap()).toMutableMap()
                    cur["phoneNumber"] = newPhone
                    cur["phoneNo"] = newPhone
                    _profileData.value = cur
                    lastLoadedAuthUid = authUid
                    lastLoadedAtMs = System.currentTimeMillis()
                } else {
                    _error.value = "Failed to update phone number"
                }
            } catch (ex: Exception) {
                _error.value = ex.message
                _phoneUpdateSuccess.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        profileReg?.remove()
        super.onCleared()
    }
}

/** Factory: pass the CURRENT Firebase auth UID at creation time (can be blank initially). */
class ProfileViewModelFactory(
    private val authRepo: AuthRepository,
    private val initialAuthUid: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(authRepo, initialAuthUid) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
