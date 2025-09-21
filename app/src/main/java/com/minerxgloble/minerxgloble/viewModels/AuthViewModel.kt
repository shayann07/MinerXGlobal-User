// app/src/main/java/com/minerxgloble/minerxgloble/viewModels/AuthViewModel.kt
package com.minerxgloble.minerxgloble.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minerxgloble.minerxgloble.repos.AuthRepository
import com.minerxgloble.minerxgloble.repos.LoginResult
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    /* ---------- UI state ---------- */
    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> get() = _loading

    /* ---------- Auth results ---------- */
    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> get() = _loginResult

    private val _registrationSuccess = MutableLiveData<Boolean>()
    val registrationSuccess: LiveData<Boolean> get() = _registrationSuccess

    private val _registrationError = MutableLiveData<String?>()
    val registrationError: LiveData<String?> get() = _registrationError

    /* ---------- Password flows ---------- */
    private val _updatePasswordSuccess = MutableLiveData<Boolean>()
    val updatePasswordSuccess: LiveData<Boolean> get() = _updatePasswordSuccess

    private val _resetEmailSent = MutableLiveData<Boolean>()
    val resetEmailSent: LiveData<Boolean> get() = _resetEmailSent

    private val _announcementImageUrls = MutableLiveData<List<String>?>()
    val announcementImageUrls: LiveData<List<String>?> get() = _announcementImageUrls
    /* ---------- Misc ---------- */
    private val _checkEmailResult = MutableLiveData<Boolean>()
    val checkEmailResult: LiveData<Boolean> get() = _checkEmailResult

    private val _verificationEmailSent = MutableLiveData<Boolean>()
    val verificationEmailSent: LiveData<Boolean> get() = _verificationEmailSent

    fun clearRegistrationError() { _registrationError.value = null }

    fun registerUser(
        name: String,
        lastName: String,
        email: String,
        password: String,
        phoneNo: String,
        referralCode: String
    ) {
        viewModelScope.launch {
            _loading.value = true
            _registrationError.value = null
            _registrationSuccess.value = false
            try {
                val ok = authRepository.registerUser(
                    name, lastName, email, password, phoneNo, referralCode
                )
                _registrationSuccess.value = ok
                if (!ok) _registrationError.value = "Registration failed. Please try again."
            } catch (e: FirebaseAuthUserCollisionException) {
                _registrationSuccess.value = false
                _registrationError.value = "Email already exists"
            } catch (e: Exception) {
                _registrationSuccess.value = false
                _registrationError.value = e.localizedMessage ?: "Something went wrong"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _loginResult.value = authRepository.loginUser(email, password)
            } finally {
                _loading.value = false
            }
        }
    }

    fun checkEmailExists(email: String) {
        viewModelScope.launch {
            _checkEmailResult.value = authRepository.checkEmailExists(email)
        }
    }

    fun resendVerificationEmail(email: String, password: String) {
        viewModelScope.launch {
            _verificationEmailSent.value = authRepository.resendVerificationEmail(email, password)
        }
    }

    /** Send password reset email (forgot password). */
    fun sendResetEmail(email: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _resetEmailSent.value = authRepository.sendPasswordResetEmail(email)
            } finally {
                _loading.value = false
            }
        }
    }

    /** Change password (reauth is handled in repository using the stored old password). */
    fun updateUserPassword(email: String, newPassword: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _updatePasswordSuccess.value = authRepository.updateUserPassword(email, newPassword)
            } finally {
                _loading.value = false
            }
        }
    }

    fun getAnnouncementImageUrls() {
      authRepository.
        getAnnouncementImageUrls { urls ->
            _announcementImageUrls.postValue(urls)
        }
    }
}
