package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.databinding.ItemDocumentBinding
import com.minerxgloble.minerxgloble.models.DocumentItem
import java.text.SimpleDateFormat
import java.util.*

class DocumentsAdapter(
    private var items: List<DocumentItem>,
    private val onClick: (DocumentItem) -> Unit
) : RecyclerView.Adapter<DocumentsAdapter.DocVH>() {

    inner class DocVH(val binding: ItemDocumentBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocVH {
        val binding = ItemDocumentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DocVH(binding)
    }

    override fun onBindViewHolder(holder: DocVH, position: Int) {
        val item = items[position]
        holder.binding.titleTv.text = item.title
        holder.binding.descTv.text = item.description

        // Format updatedAt timestamp
        val formattedDate = item.updatedAt?.toDate()?.let { date ->
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            sdf.format(date)
        } ?: "-"

        holder.binding.updatedTv.text = "Updated On $formattedDate"

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun submit(newItems: List<DocumentItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
