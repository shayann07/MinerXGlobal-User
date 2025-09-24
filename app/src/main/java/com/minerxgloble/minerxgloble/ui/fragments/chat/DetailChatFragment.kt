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
import com.google.firebase.firestore.FirebaseFirestore
import com.minerxgloble.minerxgloble.adapters.chat.ChatDetailAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentDetailChatBinding
import com.minerxgloble.minerxgloble.fcm.AccessToken
import com.minerxgloble.minerxgloble.fcm.Fcm
import com.minerxgloble.minerxgloble.repos.chat.ChatViewModelFactory
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.viewModels.ChatViewModel
import com.trustledger.aitrustledger.models.chat.Admin


class DetailChatFragment : Fragment() {

    private lateinit var binding: FragmentDetailChatBinding
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var adapter: ChatDetailAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sharedPrefManager: PrefService

    private var userId: String? = null
    private var adminId: String? = null
    private var adminName: String? = null
    private var deviceToken: String? = null
    private var isNewChat: Boolean = false
    private var tokenFetched = false

    private var welcomeSent = false   // add at class level
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentDetailChatBinding.inflate(inflater, container, false)

        sharedPrefManager = PrefService(requireContext())
        userId = sharedPrefManager.getUserId()
        adminId = arguments?.getString("adminId")
        adminName = arguments?.getString("adminName")
        isNewChat = arguments?.getBoolean("isNewChat") == true

        Log.d("DetailChat", "userId=$userId, adminId=$adminId, isNewChat=$isNewChat")
//        Toast.makeText(requireContext(), "userId=$userId, adminId=$adminId", Toast.LENGTH_SHORT).show()

        if (userId.isNullOrEmpty() || adminId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Missing chat information", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return binding.root
        }
        Log.d("DetailChat", "userId=$userId, adminId=$adminId, isNewChat=$isNewChat")

        firestore = FirebaseFirestore.getInstance()
        adapter = ChatDetailAdapter(emptyList(), userId!!)
        binding.recyclerViewChat.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewChat.adapter = adapter

        chatViewModel = ViewModelProvider(
            this, ChatViewModelFactory(requireContext())
        )[ChatViewModel::class.java]

        chatViewModel.getChats(userId!!, adminId!!).observe(viewLifecycleOwner) { messages ->
            adapter.setMessages(messages)
            binding.recyclerViewChat.scrollToPosition(messages.size - 1)

            if (isNewChat && messages.isEmpty()) {
                welcomeSent = true                    // guard BEFORE sending
                isNewChat = false
                val welcomeMessage = "Hello admin, I have a query."
                chatViewModel.sendMessage(adminId!!, welcomeMessage, userId!!)
                sendNotification()
                Log.d("DetailChatFragment", "✅ Sent first message to admin")
            }
        }

        fetchAdminDeviceToken()

        binding.buttonSendMessage.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString().trim()
            if (messageText.isEmpty()) {
                Toast.makeText(requireContext(), "Please type a message.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            chatViewModel.sendMessage(adminId!!, messageText, userId!!)
            sendNotification()
            binding.editTextMessage.setText("")
        }

        return binding.root
    }

    private fun fetchAdminDeviceToken() {
        if (tokenFetched) return
        firestore.collection("Admin").document(adminId ?: "")
            .addSnapshotListener { snapshot, error ->
                error?.let {
                    Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT)
                        .show()
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val admin = it.toObject(Admin::class.java)
                    deviceToken = admin?.deviceToken
                    tokenFetched = true
                    Log.d("FCM", "📱 Admin token = $deviceToken")
                }
            }
    }

    private fun sendNotification() {
        AccessToken.getAccessTokenAsync(object : AccessToken.AccessTokenCallback {
            override fun onAccessTokenReceived(token: String?) {
                if (!token.isNullOrEmpty() && !deviceToken.isNullOrEmpty()) {
                    Fcm().sendFCMNotification(
                        deviceToken!!,
                        "Miner X Globle",
                        "You've received a message from a user!",
                        token
                    )
                } else {
                    Log.e("FCM", "❌ Notification not sent. Token missing.")
                }
            }
        })
    }
}
