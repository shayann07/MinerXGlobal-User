package com.minerxgloble.minerxgloble.adapters.chat

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.R
import com.trustledger.aitrustledger.models.chat.Message
import java.text.DateFormat
import java.util.Date


class ChatDetailAdapter(
    private var messages: List<Message>,
    private val currentUserId: String
) : RecyclerView.Adapter<ChatDetailAdapter.ChatDetailViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatDetailViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ChatDetailViewHolder(view)
    }
    override fun onBindViewHolder(holder: ChatDetailViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.message

        val created = message.createdAt?.toDate()
        holder.messageTime.text = created?.let { DateFormat.getTimeInstance().format(it) } ?: ""
        holder.messageDate.text = created?.let { getFormattedDate(it.time) } ?: ""

        when (message.sender) {
            "1" -> { holder.messageText.setBackgroundResource(R.drawable.bubble_right); holder.itemView.layoutDirection = View.LAYOUT_DIRECTION_RTL }
            "2" -> { holder.messageText.setBackgroundResource(R.drawable.bubble_left);  holder.itemView.layoutDirection = View.LAYOUT_DIRECTION_LTR }
            else -> { holder.messageText.setBackgroundResource(R.drawable.bubble_left);  holder.itemView.layoutDirection = View.LAYOUT_DIRECTION_LTR }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun setMessages(messages: List<Message>) {
        this.messages = messages
        notifyDataSetChanged()
    }

    class ChatDetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        val messageDate: TextView = itemView.findViewById(R.id.messageDate)
    }

    private fun getFormattedDate(timestamp: Long): String {
        return DateFormat.getDateInstance().format(Date(timestamp))
    }
}
