package com.minerxgloble.minerxgloble.utils

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.storage.FirebaseStorage
import com.minerxgloble.minerxgloble.R

object ProfileImageUtil {

    fun loadCachedInto(context: Context, imageView: ImageView, placeholder: Int = R.drawable.ic_profile) {
        val url = PrefService(context).getProfileImageUrl()
        if (!url.isNullOrBlank()) {
            Glide.with(imageView)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(placeholder)
                .error(placeholder)
                .circleCrop()
                .into(imageView)
        } else {
            imageView.setImageResource(placeholder)
        }
    }

    fun refreshCache(context: Context, uid: String, onUpdated: ((String?) -> Unit)? = null) {
        if (uid.isBlank()) { onUpdated?.invoke(null); return }
        FirebaseStorage.getInstance().reference
            .child("profile_pics/$uid/avatar.jpg")   // <- updated path
            .downloadUrl
            .addOnSuccessListener { uri ->
                val url = uri.toString()
                PrefService(context).saveProfileImageUrl(url)
                onUpdated?.invoke(url)
            }
            .addOnFailureListener { onUpdated?.invoke(null) }
    }

    fun loadOrRefresh(context: Context, uid: String, imageView: ImageView, placeholder: Int = R.drawable.ic_profile) {
        loadCachedInto(context, imageView, placeholder)
        refreshCache(context, uid) { fresh ->
            if (!fresh.isNullOrBlank()) {
                Glide.with(imageView)
                    .load(fresh)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .circleCrop()
                    .into(imageView)
            }
        }
    }

    // ProfileImageUtil.kt  (add at bottom of the object)
    fun clearAllProfileImageCache(context: Context, imageView: ImageView? = null) {
        // Remove cached URL from SharedPreferences
        PrefService(context).saveProfileImageUrl("") // empty string means "no cache"

        // Reset any currently-bound ImageView to placeholder right away
        imageView?.setImageResource(R.drawable.ic_profile)

        // Nuke Glide caches (mem now, disk off main thread)
        try {
            Glide.get(context).clearMemory()
        } catch (_: Exception) {}

        Thread {
            try { Glide.get(context).clearDiskCache() } catch (_: Exception) {}
        }.start()
    }

}
