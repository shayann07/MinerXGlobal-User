package com.minerxgloble.minerxgloble.repos

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.minerxgloble.minerxgloble.models.EarningsModel
import com.minerxgloble.minerxgloble.models.InvestmentModel
import com.minerxgloble.minerxgloble.models.User
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.models.Account
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

enum class LoginResult { SUCCESS, UNVERIFIED_EMAIL, FAILURE }

/* ---------- UID config ---------- */
private const val UID_PREFIX = "MXG-"
private const val UID_LEN = 6
private const val UID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private val secureRandom = SecureRandom()

private const val USERS_COLLECTION = "users"
private const val ACCOUNT_COLLECTION = "accounts" // singular

class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val prefService: PrefService
) {

    /** Check if a referrer with the given MXG UID exists (search by field 'uid'). */
    suspend fun referrerExists(refUid: String): Boolean {
        return try {
            val q = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("uid", refUid)
                .limit(1)
                .get()
                .await()
            !q.isEmpty
        } catch (_: Exception) {
            false
        }
    }

    private fun randomUidBody(): String {
        val sb = StringBuilder(UID_LEN)
        repeat(UID_LEN) { sb.append(UID_ALPHABET[secureRandom.nextInt(UID_ALPHABET.length)]) }
        return sb.toString()
    }

    private suspend fun generateUniqueUserId(): String {
        val reservations = firestore.collection("uidReservations")
        while (true) {
            val candidate = UID_PREFIX + randomUidBody()
            val ref = reservations.document(candidate)
            try {
                firestore.runTransaction { tx ->
                    val snap = tx.get(ref)
                    if (snap.exists()) {
                        throw FirebaseFirestoreException(
                            "UID already exists",
                            FirebaseFirestoreException.Code.ALREADY_EXISTS
                        )
                    }
                    tx.set(ref, mapOf("createdAt" to FieldValue.serverTimestamp()))
                }.await()
                return candidate
            } catch (e: FirebaseFirestoreException) {
                when (e.code) {
                    FirebaseFirestoreException.Code.ALREADY_EXISTS,
                    FirebaseFirestoreException.Code.ABORTED -> continue
                    else -> throw e
                }
            }
        }
    }

    suspend fun registerUser(
        name: String,
        lastName: String,
        email: String,
        password: String,
        phoneNumber: String,
        referralCode: String?
    ): Boolean {

        /* ─────────────────────── 0.  Duplicate-email guard ────────────────────── */
        val dup = firestore.collection(USERS_COLLECTION)
            .whereEqualTo("email", email.trim())
            .limit(1)
            .get()
            .await()
        if (!dup.isEmpty) throw FirebaseAuthUserCollisionException(
            "ERROR_EMAIL_ALREADY_IN_USE", "Email already in use"
        )

        /* ─────────────────────── 1.  Create Auth user ─────────────────────────── */
        val fbUser = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("FirebaseAuth returned null user")
        val authUid = fbUser.uid

        /* ─────────────  helper to wipe everything if anything fails  ──────────── */
        suspend fun rollbackAll(userDocRef: DocumentReference?, accDocRef: DocumentReference?) {
            try { fbUser.delete().await() } catch (_: Exception) {}
            try { userDocRef?.delete()?.await() } catch (_: Exception) {}
            try { accDocRef?.delete()?.await() }  catch (_: Exception) {}
        }

        /* ─────────────────────── 2.  Build docs & transaction ─────────────────── */
        val mxgUid        = generateUniqueUserId()
        val userDocRef    = firestore.collection(USERS_COLLECTION).document(authUid)
        val accountDocRef = firestore.collection(ACCOUNT_COLLECTION).document()

        val user = User(
            uid          = mxgUid,
            docId        = userDocRef.id,
            name         = name,
            lastName     = lastName,
            email        = email.trim(),
            password     = password,          // remove / hash in production
            phoneNumber  = phoneNumber,
            referralCode = referralCode ?: "",
            deviceToken  = "",
            createdAt    = Timestamp.now(),
            firebaseUid  = authUid,
            isBlocked    = false,
            status       = "inactive"
        )

        val account = Account(
            userId    = mxgUid,
            accountId = accountDocRef.id,
            status    = "inactive",
            createdAt = Timestamp.now(),
            investment = InvestmentModel(),
            earnings   = EarningsModel()
        )

        try {
            // 2️⃣  Atomic write
            firestore.runTransaction { tx ->
                tx.set(userDocRef, user.toMap())
                tx.set(accountDocRef, account.toMap())
            }.await()
        } catch (e: Exception) {
            rollbackAll(userDocRef, accountDocRef)
            Log.e("AuthRepository", "Tx failed — rolled back", e)
            return false
        }

        /* ─────────────────────── 3.  Send verification mail ───────────────────── */
        try {
            fbUser.sendEmailVerification().await()
        } catch (e: Exception) {
            rollbackAll(userDocRef, accountDocRef)
            Log.e("AuthRepository", "Mail failed — rolled back", e)
            return false
        }

        /* ─────────────────────── 4.  Cache essentials ─────────────────────────── */
        prefService.apply {
            setString("uid", mxgUid)
            setString("user_id", mxgUid)      // legacy key
            setString("email", email.trim())
            setString("name", name)
            setString("password", password)
            setString("firebase_uid", authUid)
            setReferrerId(referralCode ?: "")
            setBoolean("is_logged_in", false) // signup not counted as full login
        }

        // FCM token (best-effort)
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            if (!token.isNullOrEmpty()) {
                userDocRef.update("deviceToken", token)
                prefService.saveUserProfile(mapOf("deviceToken" to token))
            }
        }

        return true        // 🎉 All steps completed
    }


    suspend fun loginUser(email: String, password: String): LoginResult {
        return try {
            val credential = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = credential.user ?: return LoginResult.FAILURE

            if (!firebaseUser.isEmailVerified) {
                try {
                    firebaseUser.sendEmailVerification().await()
                } catch (_: Exception) {
                }
                auth.signOut()
                return LoginResult.UNVERIFIED_EMAIL
            }

            val qs = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("email", email.trim())
                .limit(1)
                .get()
                .await()
            if (qs.isEmpty) return LoginResult.FAILURE
            val userDoc = qs.documents.first()

            // Optional: keep Firestore 'password' updated for dev/testing parity
            try {
                userDoc.reference.update("password", password).await()
            } catch (_: Exception) {
            }

            // Pull the canonical UID (MXG-XXXXXX)
            val mxgUid = userDoc.getString("uid").orElse("")
            val displayName = userDoc.getString("name").orEmpty()

            // Cache
            prefService.setString("uid", mxgUid)
            prefService.setString("email", email.trim())
            prefService.setString("name", displayName)
            prefService.setString("password", password)
            prefService.setBoolean("is_logged_in", true)
            prefService.setString("firebase_uid", firebaseUser.uid)
            prefService.saveUserProfile(userDoc.data ?: emptyMap())
            prefService.setReferrerId(userDoc.getString("referralCode") ?: "")

            // Profile image (optional)
            val storageRef = FirebaseStorage.getInstance().reference
            storageRef.child("profile_pics/$mxgUid.jpg").downloadUrl
                .addOnSuccessListener { uri -> prefService.saveProfileImageUrl(uri.toString()) }

            // Refresh FCM token in user doc
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrEmpty()) {
                    firestore.collection(USERS_COLLECTION).document(userDoc.id)
                        .update("deviceToken", token)
                }
            }

            Log.d("AuthRepository", "Login ok uid=$mxgUid")
            LoginResult.SUCCESS
        } catch (e: Exception) {
            Log.e("AuthRepository", "loginUser failed: ${e.message}", e)
            LoginResult.FAILURE
        }
    }

    /** Resend the verification email for an unverified account. */
    suspend fun resendVerificationEmail(email: String, password: String): Boolean {
        return try {
            val credential = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = credential.user ?: return false
            firebaseUser.sendEmailVerification().await()
            auth.signOut()
            true
        } catch (e: Exception) {
            try { auth.signOut() } catch (_: Exception) { }
            Log.e("AuthRepository", "resendVerificationEmail failed: ${e.message}", e)
            false
        }
    }

    suspend fun checkEmailExists(email: String): Boolean {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("email", email.trim())
                .limit(1)
                .get()
                .await()
            !snapshot.isEmpty
        } catch (_: Exception) {
            false
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Boolean {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            true
        } catch (e: Exception) {
            Log.e("AuthRepository", "Reset email error: ${e.message}")
            false
        }
    }

    suspend fun updateUserPassword(email: String, newPassword: String): Boolean {
        return try {
            val user = auth.currentUser ?: return false
            val oldPassword = prefService.getString("password") ?: ""
            val credential = EmailAuthProvider.getCredential(email.trim(), oldPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()

            val snapshot = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("email", email.trim())
                .limit(1)
                .get()
                .await()
            if (!snapshot.isEmpty) {
                snapshot.documents.first().reference.update("password", newPassword).await()
            }

            prefService.setString("password", newPassword)
            true
        } catch (e: FirebaseAuthWeakPasswordException) {
            Log.e("AuthRepository", "Weak password update: ${e.message}")
            false
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e("AuthRepository", "Invalid credentials update: ${e.message}")
            false
        } catch (e: FirebaseAuthException) {
            Log.e("AuthRepository", "Auth failure update: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e("AuthRepository", "General error update: ${e.message}", e)
            false
        }
    }

    /* ───────────────────────────────────────────────────────────
       PROFILE (moved in from ProfileRepository)
       - fetchProfile by MXG userCode ("uid" field)
       - updateProfile (name/lastName/dob/phone)
       - updatePhoneNumber (updates both phoneNumber & phoneNo)
       ─────────────────────────────────────────────────────────── */

    /** Fetch user document data by business user code (field: "uid" == MXG-XXXXXX). */
    suspend fun fetchProfileByAuthUid(authUid: String, source: Source = Source.SERVER): Map<String, Any?>? {
        return try {
            val doc = firestore.collection(USERS_COLLECTION).document(authUid).get(source).await()
            doc.data
        } catch (e: Exception) {
            Log.e("AuthRepository", "fetchProfileByAuthUid failed", e); null
        }
    }

    fun listenProfileByAuthUid(authUid: String, onChange: (Map<String, Any?>) -> Unit): ListenerRegistration {
        return firestore.collection(USERS_COLLECTION).document(authUid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                snap.data?.let(onChange)
            }
    }

    /**
     * Smart fetch:
     * 1) Try authUid doc (fast path).
     * 2) If missing (legacy), try MXG doc id.
     * 3) If still missing, do a one-time query on "uid" == MXG and (optionally) cache mapping.
     */
    suspend fun fetchProfileSmart(authUid: String?, mxgUserCode: String?): Map<String, Any?>? {
        // 1) authUid doc
        authUid?.let {
            fetchProfileByAuthUid(it, Source.CACHE)?.let { return it }
            fetchProfileByAuthUid(it, Source.SERVER)?.let { return it }
        }
        // 2) legacy: MXG as document id
        mxgUserCode?.let {
            try {
                val doc = firestore.collection(USERS_COLLECTION).document(it).get(Source.SERVER).await()
                if (doc.exists()) return doc.data
            } catch (e: Exception) { /* ignore */ }
        }
        // 3) legacy: query by field "uid" == MXG
        mxgUserCode?.let {
            try {
                val q = firestore.collection(USERS_COLLECTION).whereEqualTo("uid", it).limit(1).get().await()
                return q.documents.firstOrNull()?.data
            } catch (e: Exception) { /* ignore */ }
        }
        return null
    }

    /**
     * Update basic profile fields for the user found by MXG userCode.
     * - Updates name, lastName, dob (if you store it), and phone number fields.
     * - Writes both "phoneNumber" and "phoneNo" for compatibility with existing code.
     */
    suspend fun updateProfile(
        userCode: String,
        newName: String?,
        newLastName: String?,
        newDob: String?,
        newPhone: String?
    ): Boolean {
        return try {
            val snap = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("uid", userCode)
                .limit(1)
                .get()
                .await()
            val doc = snap.documents.firstOrNull()?.reference ?: return false

            val updates = mutableMapOf<String, Any?>()
            if (newName != null) updates["name"] = newName
            if (newLastName != null) updates["lastName"] = newLastName

            if (newPhone != null) {
                updates["phoneNumber"] = newPhone

            }

            if (updates.isEmpty()) return true // nothing to change
            doc.update(updates as Map<String, Any>).await()
            true
        } catch (e: Exception) {
            Log.e("AuthRepository", "updateProfile failed", e)
            false
        }
    }

    /** Update only the phone number (keeps parity with PSE flow). */
    suspend fun updatePhoneNumber(
        userCode: String,
        newPhone: String
    ): Boolean {
        return try {
            val snap = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("uid", userCode)
                .limit(1)
                .get()
                .await()
            val doc = snap.documents.firstOrNull()?.reference ?: return false
            doc.update(mapOf("phoneNumber" to newPhone)).await()
            true
        } catch (e: Exception) {
            Log.e("AuthRepository", "updatePhoneNumber failed", e)
            false
        }
    }
    fun getAnnouncementImageUrls(callback: (List<String>?) -> Unit) {
        firestore.collection("announcement_images")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && !snapshot.isEmpty) {
                    val urls = snapshot.documents.mapNotNull { doc ->
                        doc.getString("imageUrl")
                    }
                    callback(urls)
                } else {
                    callback(emptyList())
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }


    private fun String?.orElse(fallback: String) = if (this.isNullOrEmpty()) fallback else this


}
