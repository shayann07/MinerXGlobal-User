package com.minerxgloble.minerxgloble.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.models.AnnouncementModel
import java.text.SimpleDateFormat
import java.util.Locale


class AnnouncementAdapter(
    private val item: List<AnnouncementModel>
) :
    RecyclerView.Adapter<AnnouncementAdapter.AnnouncementViewHolder>() {
    inner class AnnouncementViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvBody: TextView = itemView.findViewById(R.id.message)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(item: AnnouncementModel?) {
            tvTitle.text = item?.announcementTitlte
            tvBody.text = item?.message

            val formattedTime = item?.time?.toDate()?.let {
                SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                    .format(it)
            } ?: "—"

            tvTime.text = formattedTime
        }

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AnnouncementViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcment, parent, false)
        return AnnouncementViewHolder(v)
    }

    override fun onBindViewHolder(
        holder: AnnouncementViewHolder,
        position: Int
    ) {
        holder.bind(item[position])

        // Hide divider if this is the last item
        val divider = holder.itemView.findViewById<View>(R.id.divider)
        divider.visibility =
            if (position == item.size - 1) View.GONE else View.VISIBLE
    }

    override fun getItemCount(): Int = item.size
}


