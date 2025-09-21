package com.minerxgloble.minerxgloble.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit

/**
 * Receives DownloadManager COMPLETE event (foreground, background or killed).
 * Ensures installer is launched **exactly once**.
 * If user cancels installer, RemoteUpdateManager increments retry count on next launch.
 */
class UpdateDownloadReceiver : BroadcastReceiver() {

    companion object {
        internal const val KEY_DONE = "handled_once"
        private const val PREF     = "update_prefs"
        private const val TAG      = "UpdateRcvr"
    }

    override fun onReceive(ctx: Context, intent: Intent) {

        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        if (prefs.getBoolean(KEY_DONE, false)) return   // already handled
        prefs.edit { putBoolean(KEY_DONE, true) }

        val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        val expectedId = prefs.getLong("download_id", -1)
        if (finishedId != expectedId) return

        Log.d(TAG, "Download #$finishedId complete → validate & install")

        val launched = UpdateInstaller.launchIfApkValid(ctx)
        if (!launched) {
            // Validation failed  →  clear prefs, force new update flow
            prefs.edit { clear() }
            return
        }

        /*  Installer will present a system screen. We can't know if user cancels,
            but on next resume, RemoteUpdateManager will see version unchanged
            and bump retry_count via onInstallCancelled().                 */
    }
}
