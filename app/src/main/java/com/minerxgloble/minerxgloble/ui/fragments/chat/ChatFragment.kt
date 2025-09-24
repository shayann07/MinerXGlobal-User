package com.minerxgloble.minerxgloble.ui.fragments.chat

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentChatBinding
import com.minerxgloble.minerxgloble.viewModels.ChatViewModel

import com.minerxgloble.minerxgloble.adapters.chat.ChatPreviewAdapter
import com.minerxgloble.minerxgloble.repos.chat.ChatViewModelFactory

import com.trustledger.aitrustledger.models.chat.Admin
import com.minerxgloble.minerxgloble.models.chat.ChatPreview


class ChatFragment : Fragment(), ChatPreviewAdapter.OnChatPreviewClickListener {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var adapter: ChatPreviewAdapter
    private var adminListCache: List<Admin>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)

        adapter = ChatPreviewAdapter(emptyList(), this)
        binding.chatListRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.chatListRecyclerView.adapter = adapter

        chatViewModel = ViewModelProvider(
            this, ChatViewModelFactory(requireContext())
        )[ChatViewModel::class.java]

        chatViewModel.getChatPreviewList().observe(viewLifecycleOwner) { chatPreviews ->
            adapter.setChatPreviews(chatPreviews)
        }

        chatViewModel.getAdmin().observe(viewLifecycleOwner) { admins ->
            adminListCache = admins
            Log.d("ChatFragment", "Cached admin list: ${admins.map { it.id }}")
        }

        binding.btnStartChat.setOnClickListener {
            val adminList = adminListCache
            val firstAdmin = adminList?.firstOrNull()

            if (firstAdmin?.id.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No Admin available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bundle = Bundle().apply {
                putString("adminId", firstAdmin!!.id)
                putString("adminName", firstAdmin.name)
                putBoolean("isNewChat", true)
            }

            if (firstAdmin != null) {
                Log.d("ChatFragment", "Navigating to chat with admin: ${firstAdmin.id}")
            }
            findNavController().navigate(R.id.action_chatFragment_to_detailChatFragment, bundle)
        }

        return binding.root
    }

    override fun onChatPreviewClick(chatPreview: ChatPreview) {
        val bundle = Bundle().apply {
            putString("adminId", chatPreview.userId)
            putString("adminName", chatPreview.userName)
            putBoolean("isNewChat", false)
        }

        Log.d("ChatPreviewClick", "Navigating to chat with ${chatPreview.userId}")
        findNavController().navigate(R.id.action_chatFragment_to_detailChatFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}