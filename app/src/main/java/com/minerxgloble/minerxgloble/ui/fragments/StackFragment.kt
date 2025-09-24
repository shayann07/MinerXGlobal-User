package com.minerxgloble.minerxgloble.ui.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.airbnb.lottie.LottieDrawable
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentStackBinding
import com.minerxgloble.minerxgloble.repos.BuyPlanRepo
import com.minerxgloble.minerxgloble.utils.PlanStatus
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.viewModels.BuyPlanViewModel
import com.minerxgloble.minerxgloble.viewModels.UiPlan
import com.minerxgloble.minerxgloble.viewModels.WalletViewModel
import com.minerxgloble.minerxgloble.viewModels.factory.BuyPlanViewModelFactory

class StackFragment : BaseFragment() {

    private var _binding: FragmentStackBinding? = null
    private val binding get() = _binding!!

    private val walletVm: WalletViewModel by activityViewModels()
    private val viewModel: BuyPlanViewModel by viewModels {
        BuyPlanViewModelFactory(BuyPlanRepo(FirebaseFirestore.getInstance()))
    }

    private var successPlayer: MediaPlayer? = null
    private var isCelebrating = false
    private lateinit var depositBalanceTv: TextView

    private var lastClickAt = 0L
    private fun throttleClick(ms: Long = 600): Boolean {
        val now = System.currentTimeMillis()
        return if (now - lastClickAt < ms) true else { lastClickAt = now; false }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = FragmentStackBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)
        ProfileImageUtil.loadOrRefresh(
            requireContext(),
            uid = PrefService(requireContext()).getUserId().toString(),
            binding.walletCard.profileImage
        )
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { navigateHome() }
            }
        )

        // start caching plans once (no network in click-path)
        viewModel.startPlansCache()

        depositBalanceTv = binding.root.findViewById(R.id.depositBalance)

        // Wallet UI
        walletVm.wallet.observe(viewLifecycleOwner) { snap ->
            if (snap == null) return@observe
            val acc = snap.account
            val raw = snap.raw
            val bal = snap.account.investment.currentBalance
            depositBalanceTv.text = walletVm.money(bal)
            binding.totalInvestedAmount.text = walletVm.money(acc.investment.totalInvestedInPlans)
            binding.totalDepositAmt.text = walletVm.money(acc.investment.totalDeposit)
            val totalWithdrawn = walletVm.nestedDouble(raw, "earnings.totalWithdrawn")
            binding.withdrawAmt.text = walletVm.money(totalWithdrawn)
        }

        binding.btnInvestedPlan.setOnClickListener {
            findNavController().navigate(R.id.action_stackFragment_to_plansFragment2)
        }

        // Global loading + disable buy button
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) showLoading() else hideLoading()
            binding.buyBtn.isEnabled = !loading
        }

        // Purchase status -> celebration/success dialog or errors
        viewModel.buyPlanStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                PlanStatus.Success -> {
                    val planName = viewModel.lastPurchasedPlanName.value ?: "Your Plan"
                    val amount = viewModel.lastPurchasedAmount.value ?: 0.0
                    binding.etAmt.text?.clear()
                    hideLoading()
                    viewModel.clearStatus()
                    playPurchaseCelebrationThen {
                        if (isAdded) showSuccessDialog(planName, amount)
                    }
                }
                PlanStatus.InvalidAmount -> {
                    showSnackbar("Invalid amount.")
                    hideLoading()
                    viewModel.clearStatus()
                }
                PlanStatus.NoPlanFound -> {
                    showSnackbar("No plan matches this amount.")
                    hideLoading()
                    viewModel.clearStatus()
                }
                PlanStatus.NoUserFound -> {
                    hideLoading()
                    showSnackbar("User not found login again")
                    viewModel.clearStatus()
                }
                PlanStatus.NotEnoughBalance -> {
                    hideLoading()
                    showSnackbar("Not enough balance")
                    viewModel.clearStatus()
                }
                PlanStatus.Error -> {
                    hideLoading()
                    showSnackbar("Something went wrong. Try again.")
                    viewModel.clearStatus()
                }
                null -> Unit
            }
        }

        // BUY button → instant (cached) confirm dialog
        binding.buyBtn.setOnClickListener {
            if (throttleClick()) return@setOnClickListener

            val amount = binding.etAmt.text?.toString()?.trim()?.toDoubleOrNull()
            if (amount == null || amount <= 0.0 ) {
                showSnackbar("Enter a valid amount."); return@setOnClickListener
            }
            if (amount < 10.0) {
                showSnackbar("Minimum amount to invest is 10$."); return@setOnClickListener
            }
            val userId = PrefService(requireContext()).getUserId()
            if (userId.isNullOrBlank()) {
                showSnackbar("Please login first."); return@setOnClickListener
            }

            // ZERO network: pick plan from cache synchronously
            val cached = viewModel.pickPlanFromCache(amount)
            if (cached != null) {
                showInstantConfirmDialog(cached, amount)
            } else {
                showFallbackConfirmDialog(amount) // non-blocking fallback
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun navigateHome() {
        val navController = findNavController()
        val popped = navController.popBackStack(R.id.homeFragment, false)
        if (!popped) navController.navigate(R.id.homeFragment)
    }

    private fun showSuccessDialog(planName: String = "", amount: Double = 0.0) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_plan_purchase_success, null)

        val planImage = dialogView.findViewById<ImageView>(R.id.planImage)
        val planText = dialogView.findViewById<TextView>(R.id.planText)

        val imageRes = when (planName.trim().lowercase()) {
            "crypto forge" -> R.drawable.mining_1
            "hash power"   -> R.drawable.mining_2
            "block pulse"  -> R.drawable.mining_3
            "core miner"   -> R.drawable.mining_4
            "quantum rig"  -> R.drawable.mining_5
            else           -> R.drawable.mining_1
        }

        planImage.setImageResource(imageRes)
        planText.text = "You invested ${amount}$ in ${planName}"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.show()
        dialogView.postDelayed({ dialog.dismiss() }, 3000)
    }

    private fun playPurchaseCelebrationThen(onEnd: () -> Unit) {
        if (isCelebrating) return
        val lottie = binding.lottieCelebration
        isCelebrating = true

        lottie.cancelAnimation()
        lottie.removeAllAnimatorListeners()
        lottie.repeatCount = 0
        lottie.repeatMode  = LottieDrawable.RESTART
        lottie.progress    = 0f
        lottie.speed       = 1.0f
        lottie.visibility  = View.VISIBLE

        stopAndReleaseSuccessPlayer()
        successPlayer = MediaPlayer.create(requireContext(), R.raw.success).apply {
            isLooping = false
            setOnErrorListener { mp, _, _ ->
                try { mp.reset(); mp.release() } catch (_: Exception) {}
                successPlayer = null
                false
            }
            start()
        }

        var delivered = false
        fun cleanup(callEnd: Boolean) {
            if (!delivered && callEnd) {
                delivered = true
                if (isAdded) onEnd()
            }
            lottie.removeAllAnimatorListeners()
            lottie.cancelAnimation()
            lottie.visibility = View.GONE
            stopAndReleaseSuccessPlayer()
            isCelebrating = false
        }

        lottie.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = cleanup(callEnd = true)
            override fun onAnimationCancel(animation: Animator) = cleanup(callEnd = false)
        })

        lottie.playAnimation()
    }

    private fun stopAndReleaseSuccessPlayer() {
        successPlayer?.let { mp ->
            try {
                mp.setOnErrorListener(null)
                mp.setOnCompletionListener(null)
                if (mp.isPlaying) mp.stop()
            } catch (_: Exception) {}
            try { mp.reset() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
        successPlayer = null
    }

    override fun onStop() {
        super.onStop()
        _binding?.lottieCelebration?.let { lottie ->
            lottie.cancelAnimation()
            lottie.visibility = View.GONE
        }
        stopAndReleaseSuccessPlayer()
        isCelebrating = false
    }

    // ---------- Confirm dialogs (instant + fallback) ----------

    private fun showInstantConfirmDialog(plan: UiPlan, enteredAmount: Double) {
        val view = layoutInflater.inflate(R.layout.dialog_plan_confirm, null)

        val img        = view.findViewById<ImageView>(R.id.confirmPlanImage)
        val title      = view.findViewById<TextView>(R.id.tvTitle)
        val name       = view.findViewById<TextView>(R.id.tvPlanName)
        val range      = view.findViewById<TextView>(R.id.tvRange)
        val amountTv   = view.findViewById<TextView>(R.id.tvAmountEntered)
        val tvTotal    = view.findViewById<TextView>(R.id.tvTotalPayout)
        val btnCancel  = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirm)

        img.setImageResource(miningDrawableFor(plan.name))
        title.text = "Confirm Plan Purchase"
        name.text  = "Plan: ${plan.name}"

        val minTxt = walletVm.money(plan.minAmount)
        val maxTxt = plan.maxAmount?.let { walletVm.money(it) } ?: "∞"
        range.text = "Range: Min $minTxt — Max $maxTxt"

        amountTv.text = "Amount entered: ${walletVm.money(enteredAmount)}"
        tvTotal.text  = "Total payout: ${"%.2f".format(plan.payoutPercent)}%"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            showLoading()
            val userId = PrefService(requireContext()).getUserId()
            if (!userId.isNullOrBlank()) {
                viewModel.buyPlan(userId = userId, amount = enteredAmount)
            } else {
                hideLoading()
                showSnackbar("Please login first.")
            }
        }
    }

    private fun showFallbackConfirmDialog(enteredAmount: Double) {
        val view = layoutInflater.inflate(R.layout.dialog_plan_confirm, null)

        view.findViewById<ImageView>(R.id.confirmPlanImage).setImageResource(R.drawable.mining_1)
        view.findViewById<TextView>(R.id.tvTitle).text = "Confirm Purchase"
        view.findViewById<TextView>(R.id.tvPlanName).text = "Plan will be selected at purchase"
        view.findViewById<TextView>(R.id.tvRange).text = "Range: —"
        view.findViewById<TextView>(R.id.tvAmountEntered).text =
            "Amount entered: ${walletVm.money(enteredAmount)}"
        view.findViewById<TextView>(R.id.tvTotalPayout).text = "Total payout: —"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<MaterialButton>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            showLoading()
            val userId = PrefService(requireContext()).getUserId()
            if (!userId.isNullOrBlank()) {
                viewModel.buyPlan(userId = userId, amount = enteredAmount) // repo picks plan server-side
            } else {
                hideLoading()
                showSnackbar("Please login first.")
            }
        }
    }

    private fun miningDrawableFor(planName: String): Int = when (planName.trim().lowercase()) {
        "crypto forge" -> R.drawable.mining_1
        "hash power"   -> R.drawable.mining_2
        "block pulse"  -> R.drawable.mining_3
        "core miner"   -> R.drawable.mining_4
        "quantum rig"  -> R.drawable.mining_5
        else           -> R.drawable.mining_1
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        val bg = ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bg)
        snack.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snack.show()
    }
}
