package com.minerxgloble.minerxgloble.ui.fragments

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentProfileBinding
import com.minerxgloble.minerxgloble.repos.AuthRepository
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.viewModels.ProfileViewModel
import com.minerxgloble.minerxgloble.viewModels.ProfileViewModelFactory

class ProfileFragment : BaseFragment() {

    private var _binding: FragmentProfileBinding? = null
    // Intentionally no `val binding get() = _binding!!` to avoid NPEs in async callbacks

    private lateinit var viewModel: ProfileViewModel
    private lateinit var pref: PrefService

    private val storage by lazy { FirebaseStorage.getInstance() }
    private val PICK_IMAGE_REQUEST = 1001
    private var originalPhoneNumber: String = ""

    // Upload dialog state

    private var uploadMsgView: TextView? = null

    // Keep these as fields if you want to update/dismiss later
    private var uploadDialog: android.app.Dialog? = null
    private var uploadStatusTv: TextView? = null
    private var uploadProgress: com.google.android.material.progressindicator.CircularProgressIndicator? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = PrefService(requireContext())


        val mxg = pref.getUserId().orEmpty() // your business key (MXG-XXXX)
        val authRepo = AuthRepository(
            FirebaseAuth.getInstance(),
            FirebaseFirestore.getInstance(),
            pref
        )
        val authUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

        val factory = ProfileViewModelFactory(authRepo, authUid) // initial = Auth UID
        viewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]

// keep VM hot & in sync with both ids
        viewModel.setAuthUid(authUid, mxg)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        setupDrawerTrigger(view)
        _binding?.let { b ->
            // Username & email are read-only
            listOf(b.etUsername, b.etEmail).forEach { et ->
                et.isFocusable = false
                et.isFocusableInTouchMode = false
                et.isCursorVisible = false
                et.isClickable = false
                et.isLongClickable = false
            }

            // Phone editable
            b.etPhone.apply {
                isFocusable = true
                isFocusableInTouchMode = true
                isCursorVisible = true
                isClickable = true
            }

            // Initial visibility
            b.btnUpdate.isVisible = false
            b.btnLogout.isVisible = true
            b.updatePasswordBtn.isVisible = true
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) showLoading()else hideLoading()
        }
        // Observe profile
        viewModel.profileData.observe(viewLifecycleOwner) { data ->
            if (data == null) return@observe

            val name  = (data["name"] as? String).orEmpty()
            val lastname=(data["lastName"] as? String).orEmpty()
            val fullName = "$name $lastname"
            val userId =(data["uid"] as? String).orEmpty()
            val email = (data["email"] as? String).orEmpty()
            val phone = (data["phoneNumber"] as? String)
                ?: (data["phoneNo"] as? String)
                ?: ""

            _binding?.let { b ->
                b.tvEmailTop.text = userId
                b.etUsername.setText(fullName)
                b.etEmail.setText(email)
                originalPhoneNumber = phone
                b.etPhone.setText(phone)
            }

            // Load avatar
            val uid = pref.getUserId() ?: return@observe
            loadProfileImageFromLocalOrRemote(uid)

            updateButtons()
        }

        // Initial load
        // Warm from cache/server only if stale; VM already has a live listener
        viewModel.ensureProfileFresh()


        // Toggle buttons when phone changes
        _binding?.etPhone?.addTextChangedListener { updateButtons() }

        // Update phone
        _binding?.btnUpdate?.setOnClickListener {
            val newPhone = _binding?.etPhone?.text?.toString()?.trim().orEmpty()
            if (newPhone.isEmpty()) {
                showSnackbar("Enter a valid phone number")
                return@setOnClickListener
            }
            viewModel.updatePhoneNumber(newPhone)
        }

        // Phone update result
        viewModel.phoneUpdateSuccess.observe(viewLifecycleOwner) { ok ->
            if (ok == true) {
                showSnackbar("Phone number updated successfully")
                originalPhoneNumber = _binding?.etPhone?.text?.toString().orEmpty()
                updateButtons()
            } else {
                showSnackbar("Failed to update phone number. Try again.")
            }
        }

        // Change password
        _binding?.updatePasswordBtn?.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_newPasswordFragment)
        }

        // Avatar edit triggers
        _binding?.ivAvatar?.setOnClickListener { selectProfileImageFromGallery() }
        _binding?.ivEdit?.setOnClickListener { selectProfileImageFromGallery() }

        // Logout
        _binding?.btnLogout?.setOnClickListener { logout() }
    }

    private fun updateButtons() {
        val edited = _binding?.etPhone?.text?.toString().orEmpty() != originalPhoneNumber
        _binding?.btnUpdate?.isVisible = edited
        _binding?.btnLogout?.isVisible = !edited
        // updatePasswordBtn always visible
    }

    private fun logout() {


       showLogoutConfirmation()
    }

    // ---------- Avatar helpers ----------

    private fun selectProfileImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            uploadProfileImageToFirebase(data.data!!)
        }
    }

    /** Canonical path for READ & WRITE (matches your Storage rules) */
    private fun profilePicRef(uid: String) =
        storage.reference.child("profile_pics/$uid/avatar.jpg")

    private fun loadProfileImageFromLocalOrRemote(uid: String) {
        val cachedUrl = pref.getProfileImageUrl()
        if (!cachedUrl.isNullOrBlank()) {
            loadProfileImageIntoView(cachedUrl)
            return
        }
        val ref = profilePicRef(uid)
        ref.downloadUrl
            .addOnSuccessListener { uri ->
                // View may be gone: cache first, UI only if binding exists
                val url = uri.toString()
                pref.saveProfileImageUrl(url)
                loadProfileImageIntoView(url)
            }
            .addOnFailureListener {
                // avatar may not exist yet — ignore
            }
    }

    private fun uploadProfileImageToFirebase(imageUri: Uri) {
        val uid = pref.getUserId() ?: run {
            showSnackbar("Upload failed: not signed in")
            return
        }
        val ref = profilePicRef(uid)

        showUploadingDialog()

        ref.putFile(imageUri)
            .addOnProgressListener { snap ->
                val total = snap.totalByteCount
                if (total > 0L) {
                    val pct = (100.0 * snap.bytesTransferred / total).toInt()
                    updateUploadingDialog(pct)
                } else {
                    updateUploadingDialog(null) // indeterminate
                }
            }
            .continueWithTask { t ->
                if (!t.isSuccessful) throw (t.exception ?: Exception("Upload failed"))
                ref.downloadUrl
            }
            .addOnSuccessListener { downloadUrl ->
                val url = downloadUrl.toString()
                pref.saveProfileImageUrl(url)
                loadProfileImageIntoView(url)
                showSnackbar("Profile picture updated!")
                hideUploadingDialog()
            }
            .addOnFailureListener { e ->
                showSnackbar("Upload failed: ${e.message ?: "permission denied"}")
                hideUploadingDialog()
            }
    }

    private fun loadProfileImageIntoView(url: String) {
        val b = _binding ?: return  // view destroyed, skip
        if (!isAdded) return
        Glide.with(b.ivAvatar)
            .load(url)
            .placeholder(R.drawable.ic_profile)
            .circleCrop()
            .into(b.ivAvatar)
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {


        val snack = Snackbar.make(_binding?.root ?: return, message, Snackbar.LENGTH_LONG)
        val bg = ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bg)
        snack.setTextColor(
            ContextCompat.getColor(requireContext(), android.R.color.white)
        )
        snack.show()
    }
    private fun showLogoutConfirmation() {
        val dlg = AlertDialog.Builder(requireContext()).setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ ->
                try {
                    FirebaseAuth.getInstance().signOut()
                    this.viewModelStore.clear()
                } catch (_: Exception) {}

                // 1) Synchronous prefs clear
                PrefService.clearAllPrefs(requireContext())

                // 2) Clear profile image caches globally
                ProfileImageUtil.clearAllProfileImageCache(requireContext(), null)

                // 3) Navigate to login
                val navHost = requireActivity()
                    .supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val navController = navHost.navController
                val opts = NavOptions.Builder()
                    .setPopUpTo(navController.graph.startDestinationId, true)
                    .build()
                navController.navigate(R.id.loginFragment, null, opts)
            }

            .setNegativeButton("Cancel", null).create()
        dlg.show()
    }
    // ---------- Simple blocking upload dialog with % ----------

    private fun showUploadingDialog(
        title: String = "Upload",
        subtitle: String = "Please wait…"
    ) {
        if (_binding == null) return
        if (uploadDialog?.isShowing == true) return

        _binding?.ivAvatar?.isEnabled = false
        _binding?.ivEdit?.isEnabled = false

        val ctx = requireContext()
        val view = layoutInflater.inflate(R.layout.dialoge_download_xml, null)

        val titleTv = view.findViewById<TextView>(R.id.dlTitle)
        val subTv   = view.findViewById<TextView>(R.id.dlSubtitle)
        val progressContainer = view.findViewById<View>(R.id.dlProgressContainer)
        val progress = view.findViewById<CircularProgressIndicator>(R.id.dlCircle)
        val statusTv = view.findViewById<TextView>(R.id.dlLabel)
        val btnDownload = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDownload)

        // customize for UPLOAD
        titleTv.text = "$title File"
        subTv.text   = subtitle
        statusTv.text = getString(R.string.uploading) // "Uploading…"
        progress.isIndeterminate = true
        progressContainer.visibility = View.VISIBLE

        // hide buttons during upload

        btnDownload.visibility = View.GONE

        // keep references for updates
        uploadStatusTv = statusTv
        uploadProgress = progress

        uploadDialog = MaterialAlertDialogBuilder(ctx)
            .setView(view)
            .setCancelable(false)
            .create()
            .also { it.show() }
    }

    private fun updateUploadingDialog(percent: Int?) {
        if (percent == null) {
            uploadProgress?.isIndeterminate = true
            uploadStatusTv?.text = getString(R.string.uploading)
        } else {
            uploadProgress?.isIndeterminate = false
            uploadProgress?.progress = percent.coerceIn(0, 100)
            uploadStatusTv?.text = "Uploading… $percent%"
        }
    }


    private fun hideUploadingDialog(success: Boolean = true) {
        _binding?.ivAvatar?.isEnabled = true
        _binding?.ivEdit?.isEnabled = true

        if (success) {
            uploadStatusTv?.text = getString(R.string.uploaded) // "Uploaded"
            uploadProgress?.isIndeterminate = false
            uploadProgress?.progress = 100
            uploadDialog?.window?.decorView?.postDelayed({
                uploadDialog?.dismiss()
                uploadDialog = null
                uploadStatusTv = null
                uploadProgress = null
            }, 500)
        } else {
            uploadDialog?.dismiss()
            uploadDialog = null
            uploadStatusTv = null
            uploadProgress = null
        }
    }


    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        hideUploadingDialog()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        val authUid = FirebaseAuth.getInstance().currentUser?.uid
        val mxg = pref.getUserId()
        viewModel.setAuthUid(authUid, mxg, attachListener = true)
    }
}
