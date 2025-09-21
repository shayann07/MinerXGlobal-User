package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.databinding.ItemDocumentBinding
import com.minerxgloble.minerxgloble.models.DocumentItem
import java.text.SimpleDateFormat
import java.util.Locale

class DocumentsAdapter(
    private var items: List<DocumentItem>, private val onClick: (DocumentItem) -> Unit
) : RecyclerView.Adapter<DocumentsAdapter.DocVH>() {

    inner class DocVH(val b: ItemDocumentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocVH {
        val b = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DocVH(b)
    }

    override fun onBindViewHolder(holder: DocVH, position: Int) {
        val item = items[position]
        val b = holder.b

        b.titleTv.text = item.title
        b.descTv.text = item.description

        val formatted = item.updatedAt?.toDate()?.let {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it)
        } ?: "-"

        b.updatedTv.text = "Updated • $formatted"

        b.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun submit(newItems: List<DocumentItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}