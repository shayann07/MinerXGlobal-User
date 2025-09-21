package com.minerxgloble.minerxgloble.adapters.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.minerxgloble.minerxgloble.R
import com.trustledger.aitrustledger.models.chat.ChatPreview
import java.text.DateFormat
import java.util.Date

class ChatPreviewAdapter(
    private var chatPreviews: List<ChatPreview>,
    private val listener: OnChatPreviewClickListener?
) : RecyclerView.Adapter<ChatPreviewAdapter.ChatPreviewViewHolder>() {

    fun setChatPreviews(chatPreviews: List<ChatPreview>) {
        this.chatPreviews = chatPreviews
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatPreviewViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_chat_preview, parent, false)
        return ChatPreviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatPreviewViewHolder, position: Int) {
        val chatPreview: ChatPreview = chatPreviews[position]
        holder.userName.text = chatPreview.userName
        holder.lastMessage.text = chatPreview.lastMessage
        holder.timestamp.text = chatPreview.timestamp.takeIf { it > 0L }?.let {
            DateFormat.getTimeInstance().format(Date(it))
        } ?: "N/A"

        holder.itemView.setOnClickListener {
            listener?.onChatPreviewClick(chatPreview)
        }
    }

    override fun getItemCount(): Int = chatPreviews.size

    class ChatPreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var userName: TextView = itemView.findViewById(R.id.user_name)
        var lastMessage: TextView = itemView.findViewById(R.id.last_message)
        var timestamp: TextView = itemView.findViewById(R.id.timestamp)
        var profileImage: ImageView = itemView.findViewById(R.id.profile_image)
    }

    interface OnChatPreviewClickListener {
        fun onChatPreviewClick(chatPreview: ChatPreview)
    }
}
