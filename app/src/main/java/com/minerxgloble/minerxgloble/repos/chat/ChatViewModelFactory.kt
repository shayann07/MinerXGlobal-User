package com.minerxgloble.minerxgloble.repos.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.minerxgloble.minerxgloble.viewModels.ChatViewModel


class ChatViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(ChatRepository(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
