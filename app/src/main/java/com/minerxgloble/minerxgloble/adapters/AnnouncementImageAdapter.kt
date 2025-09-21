// AnnouncementImageAdapter.kt
package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.ItemImageAnnouncementBinding

class AnnouncementImageAdapter(
    private var images: List<String>,
    private val emptyText: String = "No announcements yet"
) : RecyclerView.Adapter<AnnouncementImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(val binding: ItemImageAnnouncementBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemImageAnnouncementBinding.inflate(inflater, parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val b = holder.binding

        // If list is empty, show fallback image + text and return
        if (images.isEmpty()) {
            b.imageItem.setImageResource(R.drawable.upgrade_system)
            b.tvEmpty.text = emptyText
            b.tvEmpty.visibility = View.VISIBLE
            return
        }



        Glide.with(holder.itemView.context)
            .load(images.getOrNull(position))            // safe get
            .placeholder(R.drawable.upgrade_system)      // while loading
            .error(R.drawable.upgrade_system)            // if URL fails
            .fallback(R.drawable.upgrade_system)         // if model is null
            .centerCrop()
            .into(b.imageItem)
    }

    // Ensure we still render ONE item when empty (for the fallback)
    override fun getItemCount(): Int = if (images.isEmpty()) 1 else images.size

    fun submit(newImages: List<String>) {
        images = newImages
        notifyDataSetChanged()
    }
}
