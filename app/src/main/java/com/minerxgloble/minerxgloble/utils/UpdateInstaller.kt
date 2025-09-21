package com.minerxgloble.minerxgloble.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * Validates 'update.apk' and launches installer (ACTION_VIEW).
 * Returns true if installer started, false if APK invalid / user already updated.
 */
object UpdateInstaller {
    private const val TAG = "UpdateInstaller"

    fun launchIfApkValid(ctx: Context): Boolean {
        // 1) Locate the file on public Download/
        val apkFile = File(
            ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "update.apk"
        )
        if (!apkFile.exists()) return false

        // 2) Wrap it in a content:// URI
        val apkUri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            apkFile
        )

        // 3️⃣ Package name check (wrapped in try/catch for Android 15 bug)
        val info = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager.getPackageArchiveInfo(
                    apkFile.path,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageArchiveInfo(apkFile.path, 0)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "PackageInfo lookup failed: ${t.message}")
            null
        }
        if (info != null && info.packageName != ctx.packageName) {
            Log.e(TAG, "Package mismatch: ${info.packageName}")
            apkFile.delete()
            return false
        }

        // 4️⃣ SHA-256 via ContentResolver (no FileInputStream on raw path)
        val expected = ctx.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
            .getString("expected_sha256", null) ?: return false

        val actual = sha256(ctx, apkUri)
        if (!actual.equals(expected, ignoreCase = true)) {
            Log.e(TAG, "SHA-256 mismatch: $actual vs $expected")
            apkFile.delete()
            return false
        }

        // 5️⃣ Launch installer with content URI
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        return true
    }

    private fun sha256(ctx: Context, uri: android.net.Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
