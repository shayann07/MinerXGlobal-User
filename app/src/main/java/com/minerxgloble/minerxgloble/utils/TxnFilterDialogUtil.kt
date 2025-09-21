package com.minerxgloble.minerxgloble.utils

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.minerxgloble.minerxgloble.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object TxnFilterDialogUtil {

    interface OnPlanSelectedCallback {
        fun onPlanSelected()
    }

    fun show(
        context: Context,
        navController: NavController,
        onPlanSelected: OnPlanSelectedCallback? = null
    ) {
        val types = arrayOf(
            "Deposit History",
            "Withdraw History",
            "Profit History",
            "Purchased Plans",
            "Lucky Spin History",
            "Salary History",
            "Achievement History"
        )
        val constants = arrayOf(
            TxnConstants.TYPE_DEPOSIT,
            TxnConstants.TYPE_WITHDRAW,
            TxnConstants.TYPE_TEAM,
            TxnConstants.TYPE_INVESTMENT_BOUGHT,
            TxnConstants.TYPE_LUCKY_SPIN,
            TxnConstants.TYPE_SALARY,
            TxnConstants.TYPE_ACHIEVEMENT
        )

        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.material_dialog, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.dialogContainer)
        val radioGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val dialog = MaterialAlertDialogBuilder(
            context,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
        )
            .setView(dialogView)
            .create()

        types.forEachIndexed { idx, label ->
            val rb = RadioButton(context).apply {
                text = label
                id = View.generateViewId()
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                buttonTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.blue)
                )
                textSize = 18f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 46, 24, 46)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16; bottomMargin = 16; gravity = Gravity.CENTER
                }

                setOnClickListener {
                    // Clear current fragment from backstack
                    navController.popBackStack()

                    // Navigate to selected screen
//                    when (constants[idx]) {
//                        TxnConstants.TYPE_DEPOSIT ->
//                            navController.navigate(R.id.depositHistoryFragment)
//
//                        TxnConstants.TYPE_WITHDRAW ->
//                            navController.navigate(R.id.withdrawHistoryFragment)
//
//                        TxnConstants.TYPE_TEAM ->
//                            navController.navigate(R.id.profitHistoryFragment)
//
//                        TxnConstants.TYPE_INVESTMENT_BOUGHT ->
//                            navController.navigate(R.id.purchasedPlansHistory)
//
//                        TxnConstants.TYPE_LUCKY_SPIN -> navController.navigate(R.id.luckySpinHistoryFragment)
//                        TxnConstants.TYPE_SALARY -> navController.navigate(R.id.salaryHistoryFragment)
//                        TxnConstants.TYPE_ACHIEVEMENT -> navController.navigate(R.id.achievementsHistoryFragment)
//                    }

                    dialog.dismiss()
                }
            }
            radioGroup.addView(rb)
        }

        container.addView(radioGroup)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
