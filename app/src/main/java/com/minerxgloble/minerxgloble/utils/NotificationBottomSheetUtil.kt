package com.minerxgloble.minerxgloble.utils


import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.NotificationAdapter

object NotificationBottomSheetUtil {

    fun show(context: Context, userId: String) {
        val dialog = BottomSheetDialog(context, R.style.AppBottomSheetDialogTheme)

        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialoge_notification_bottom_sheet, null)

        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.notificationRv)
        val clearBtn = view.findViewById<TextView>(R.id.clearNotificationView)

        val prefManager = NotificationPreferenceManager(context)
        var notifications = prefManager.getNotifications(userId)

        val adapter = NotificationAdapter(notifications)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Handle Clear
        clearBtn.setOnClickListener {
            prefManager.clearNotifications(userId)
            notifications = emptyList()
            recyclerView.adapter = NotificationAdapter(notifications)
        }

        dialog.setContentView(view)
        dialog.show()
    }
}
