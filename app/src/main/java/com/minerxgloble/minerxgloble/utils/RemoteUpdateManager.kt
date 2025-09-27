package com.minerxgloble.minerxgloble.utils

import android.Manifest
import android.R
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.minerxgloble.minerxgloble.databinding.DialogUpdateBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.minerxgloble.minerxgloble.ui.animation.XLogoLoadingView

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**  Single-dialog in-app updater — race-safe, hash-enforced, retry-limited, with circular progress */
class RemoteUpdateManager(private val activity: ComponentActivity) {

    companion object {
        private const val TAG = "UpdateMgr"
        private const val PREF = "update_prefs"
        private const val KEY_PENDING_VERSION = "pending_version"
        private const val KEY_PENDING_URL = "pending_url"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_SHA256 = "expected_sha256"
        private const val KEY_RETRY_COUNT = "retry_count"
        private const val MAX_RETRIES = 20
    }

    // ──────────────────────────────────────────────────────────────────── //

    private val prefs = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig.apply {
        setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 0 })
        setDefaultsAsync(
            mapOf(
                "apk_sha256" to "",
                "apk_download_url" to "",
                "latest_version_code" to 1L,
                "update_message" to ""
            )
        )
    }

    private var updateDialog: AlertDialog? = null

    /** views inside the custom dialog */
    private lateinit var progressContainer: View
    private lateinit var xLogo: XLogoLoadingView
    private var pollJob: Job? = null

    // async permission callback
    private val notifPermLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        prefs.getString(KEY_PENDING_URL, null)?.let { downloadAndInstallApk(it) }
        if (!granted) toast("Notification permission declined – download will continue silently.")
    }

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                pollJob?.cancel()
                updateDialog?.dismiss()
            }
        })
    }

    // ───────────────────── PUBLIC API ─────────────────────────────────── //

    fun clearFlagsIfUpdated() = clearIfAlreadyUpdated(installedVersionCode())

    /** Call inside your Activity.onResume() */
    fun checkForUpdate() {
        Log.d(TAG, "checkForUpdate()")

        val installed = installedVersionCode()
        clearIfAlreadyUpdated(installed)

        // 1️⃣  show dialog immediately if a pending version exists
        val pendingVer = prefs.getInt(KEY_PENDING_VERSION, -1)
        val pendingUrl = prefs.getString(KEY_PENDING_URL, null)
        if (pendingVer != -1 && installed < pendingVer) {
            showUpdateDialog(pendingUrl.orEmpty(), defaultMessage())
        }

        // 2️⃣  fresh Remote Config fetch
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "RemoteConfig fetch failed: ${task.exception?.message}")
                return@addOnCompleteListener
            }

            val remoteVer = remoteConfig.getLong("latest_version_code").toInt()
            val remoteUrl = remoteConfig.getString("apk_download_url")
            val remoteHash = remoteConfig.getString("apk_sha256")
            val message = remoteConfig.getString("update_message")

            if (remoteHash.isBlank()) {
                Log.e(TAG, "Server did not provide apk_sha256 → ABORT update")
                return@addOnCompleteListener
            }

            // app already newer / same
            if (installed >= remoteVer) {
                cancelAnyDownload()
                prefs.edit { clear() }
                updateDialog?.dismiss()
                return@addOnCompleteListener
            }

            // server changed → cancel previous download
            if (remoteVer != pendingVer || remoteUrl != pendingUrl) cancelAnyDownload()

            prefs.edit {
                putInt(KEY_PENDING_VERSION, remoteVer).putString(KEY_PENDING_URL, remoteUrl)
                    .putString(KEY_SHA256, remoteHash)
            }

            showUpdateDialog(remoteUrl, message.ifBlank { defaultMessage() })
        }
    }

    // ───────────────── DIALOG & DOWNLOAD ─────────────────────────────── //

    private fun showUpdateDialog(serverUrl: String, message: String) {

        if (prefs.getInt(KEY_RETRY_COUNT, 0) >= MAX_RETRIES) {
            blockScreen(); return
        }

        if (updateDialog?.isShowing == true) {
            updateDialog?.setMessage(message)
            updateDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val fresh = prefs.getString(KEY_PENDING_URL, serverUrl) ?: serverUrl
                downloadAndInstallApk(fresh)
            }
            return
        }

        // inflate via generated binding
        val binding = DialogUpdateBinding.inflate(
            LayoutInflater.from(activity),
            null,
            false
        )

// cache views for download logic
        progressContainer = binding.updateProgressContainer
        xLogo = binding.updateLogo

        xLogo.indeterminate = true
        xLogo.start()

// set your text
        binding.updateTitle.text = "Update Required"
        binding.updateDesc.text = message

// wire up the button
        binding.updateNowBtn.setOnClickListener {
            val fresh = prefs.getString(KEY_PENDING_URL, serverUrl) ?: serverUrl
            downloadAndInstallApk(fresh)
        }

// show the dialog with the binding’s root view
        updateDialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setCancelable(false)
            .create().apply {
                window?.setBackgroundDrawableResource(R.color.transparent)
                show()
            }

    }

    private fun downloadAndInstallApk(apkUrl: String) {/* unknown-apps permission (O+) */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData("package:${activity.packageName}".toUri())
            )
            toast("Allow install permission, then reopen the app."); return
        }

        /* notification permission on 13+ */
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return   // resume in callback
        }

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        cancelAnyDownload()

        val visibility = if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) DownloadManager.Request.VISIBILITY_HIDDEN
        else DownloadManager.Request.VISIBILITY_VISIBLE

        val req = DownloadManager.Request(apkUrl.toUri()).apply {
            setTitle("Downloading update")
            setDescription("Downloading…")
            setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                "update.apk"
            )
            setNotificationVisibility(visibility)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val id = dm.enqueue(req)
        prefs.edit {
            putLong(KEY_DOWNLOAD_ID, id).remove(UpdateDownloadReceiver.Companion.KEY_DONE)
        }

        if (visibility == DownloadManager.Request.VISIBILITY_HIDDEN) {
            toast("Downloading ...")
        }

        // ─── show & animate circular progress ───
        // ─── show & animate circular progress ───
        progressContainer.visibility = View.VISIBLE
        xLogo.indeterminate = true
        xLogo.start()

        pollJob?.cancel()
        pollJob = activity.lifecycleScope.launch(Dispatchers.IO) {

            val query = DownloadManager.Query().setFilterById(id)
            var finished = false            // ← flag instead of break

            while (isActive && !finished) {
                dm.query(query)?.use { c ->
                    if (!c.moveToFirst()) return@use          // nothing yet

                    when (c.getInt(
                        c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            withContext(Dispatchers.Main) {
                                if (this@RemoteUpdateManager::xLogo.isInitialized) xLogo.stop()
                                progressContainer.visibility = View.GONE
                            }
                            finished = true                   // will drop out after use{}
                        }

                        DownloadManager.STATUS_FAILED -> {
                            withContext(Dispatchers.Main) {
                                if (this@RemoteUpdateManager::xLogo.isInitialized) xLogo.stop()
                                progressContainer.visibility = View.GONE
                            }
                            finished = true
                        }

                        DownloadManager.STATUS_RUNNING -> {
                            val soFar = c.getLong(
                                c.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                            )
                            val total = c.getLong(
                                c.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                                )
                            )
                            if (total > 0) {
                                val pct = (soFar * 100 / total).toInt()
                                withContext(Dispatchers.Main) {
                                    if (xLogo.indeterminate) xLogo.indeterminate = false
                                    xLogo.setProgress(pct / 100f)
                                }
                            }
                        }

                        else -> { /* pending / paused — leave spinner indeterminate */
                        }
                    }
                }
                if (!finished) delay(400)
            }
            // hide bar once we leave the loop
            withContext(Dispatchers.Main) { progressContainer.visibility = View.GONE }
        }
        Log.d(TAG, "Download enqueued id=$id")
    }

    // ────────────────────────── HELPERS ──────────────────────────────── //

    private fun cancelAnyDownload() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id != -1L) {
            (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
            prefs.edit { remove(KEY_DOWNLOAD_ID) }
            Log.d(TAG, "Canceled download id=$id")
        }

        pollJob?.cancel()
        // ⛔ FIX: Don’t touch uninitialized progressContainer
        if (this::progressContainer.isInitialized) {
            progressContainer.visibility = View.GONE
        }
        if (this::xLogo.isInitialized) {
            xLogo.stop()
        }
    }

    private fun clearIfAlreadyUpdated(installed: Int) {
        val pending = prefs.getInt(KEY_PENDING_VERSION, -1)
        if (pending != -1 && installed >= pending) {
            cancelAnyDownload()
            prefs.edit { clear() }
        }
    }

    private fun installedVersionCode(): Int =
        activity.packageManager.getPackageInfo(activity.packageName, 0).run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode.toInt()
            else versionCode
        }

    private fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()

    private fun defaultMessage() = "A new version is required to continue."

    /** blocks UI after too many cancelled installs */
    private fun blockScreen() {
        updateDialog?.dismiss()
        MaterialAlertDialogBuilder(activity).setTitle("Update Required").setMessage(
            "You’ve dismissed the installer too many times.\n" + "Please open the downloaded APK from your Downloads folder " + "or reinstall the app from the official link."
        ).setCancelable(false).setPositiveButton("Exit") { _, _ -> activity.finish() }.show()
    }

    /** called by UpdateDownloadReceiver when install cancelled */
    internal fun onInstallCancelled() {
        val retries = prefs.getInt(KEY_RETRY_COUNT, 0) + 1
        prefs.edit { putInt(KEY_RETRY_COUNT, retries) }
    }
}
