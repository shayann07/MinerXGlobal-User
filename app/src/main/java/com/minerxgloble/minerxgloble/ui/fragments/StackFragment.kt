package com.minerxgloble.minerxgloble.ui.fragments

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.AudioManager
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

        // Cache plans for instant access (no network in click-path)
        viewModel.startPlansCache()

        depositBalanceTv = binding.root.findViewById(R.id.depositBalance)

        // Wallet UI
        walletVm.wallet.observe(viewLifecycleOwner) { snap ->
            if (snap == null) return@observe
            val acc = snap.account
            val raw = snap.raw
            val bal = acc.investment.currentBalance
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

        // Purchase status -> show success dialog (sound only; no lottie)
        viewModel.buyPlanStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                PlanStatus.Success -> {
                    val planName = viewModel.lastPurchasedPlanName.value ?: "Your Plan"
                    val amount = viewModel.lastPurchasedAmount.value ?: 0.0
                    binding.etAmt.text?.clear()
                    hideLoading()
                    viewModel.clearStatus()
                    if (isAdded) showSuccessDialog(planName, amount)
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

            // ✅ Show loading now, keep it until dialog is actually visible
            showLoading()

            val cached = viewModel.pickPlanFromCache(amount)
            if (cached != null) {
                showInstantConfirmDialog(cached, amount)
            } else {
                showFallbackConfirmDialog(amount)
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

    // ===== Success dialog (sound only) =====
    private fun showSuccessDialog(planName: String = "", amount: Double = 0.0) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_plan_purchase_success, null, false)

        val planImage = dialogView.findViewById<ImageView>(R.id.planImage)
        val planText  = dialogView.findViewById<TextView>(R.id.planText)


        val tvAmountEnt  = dialogView.findViewById<TextView>(R.id.tvAmountEntered)
        val tvRoiPct     = dialogView.findViewById<TextView>(R.id.tvRoiPct)
        val tvPayoutAmt  = dialogView.findViewById<TextView>(R.id.tvPayoutAmt)
        val tvDirectAmt  = dialogView.findViewById<TextView>(R.id.tvDirectAmt)
        val btnDone      = dialogView.findViewById<MaterialButton>(R.id.btnDone)

        // Image & headline
        planImage.setImageResource(miningDrawableFor(planName))
        planText.text = "You invested ${walletVm.money(amount)} in $planName"

        // Bind cached plan details
        val plan = viewModel.plansCache.value.orEmpty()
            .firstOrNull { it.name.equals(planName, ignoreCase = true) }

        val minTxt = plan?.minAmount?.let { walletVm.money(it) } ?: "—"
        val maxTxt = plan?.maxAmount?.let { walletVm.money(it) } ?: "∞"

        val roiPct    = plan?.roiPercent ?: 0.0
        val payoutPct = plan?.payoutPercent ?: 0.0
        val directPct = plan?.directPercent ?: 0.0

        val payoutAmt = amount * payoutPct / 100.0
        val directAmt = amount * directPct / 100.0


        tvAmountEnt?.text = " ${walletVm.money(amount)}"
        tvRoiPct?.text    = "${"%.2f".format(roiPct)}%"
        tvPayoutAmt?.text = "${walletVm.money(payoutAmt)}"
        tvDirectAmt?.text = "${walletVm.money(directAmt)}"




        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.setOnShowListener {
            // 🔊 Play success sound as soon as dialog appears
            playSuccessSound()
        }
        dialog.setOnDismissListener { stopAndReleaseSuccessPlayer() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.show()

        btnDone?.setOnClickListener { dialog.dismiss() }
    }

    // --- Audio helpers ---
    private fun playSuccessSound() {
        stopAndReleaseSuccessPlayer()

        val ctx = requireContext().applicationContext
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Request transient focus so our sound is audible
        @Suppress("DEPRECATION")
        am.requestAudioFocus(
            null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )

        // Prefer success_sound.mp3, fall back to success if needed
        var resId = resources.getIdentifier("success_sound", "raw", ctx.packageName)
        if (resId == 0) resId = resources.getIdentifier("success", "raw", ctx.packageName)
        if (resId == 0) return // nothing to play

        val afd = ctx.resources.openRawResourceFd(resId) ?: return
        try {
            successPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                setVolume(1f, 1f)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stopAndReleaseSuccessPlayer() }
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepareAsync()
            }
        } catch (_: Exception) {
            stopAndReleaseSuccessPlayer()
        } finally {
            try { afd.close() } catch (_: Exception) {}
        }
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
        stopAndReleaseSuccessPlayer()
    }

    private fun showInstantConfirmDialog(plan: UiPlan, enteredAmount: Double) {
        val view = layoutInflater.inflate(R.layout.dialog_plan_confirm, null)

        val img        = view.findViewById<ImageView>(R.id.confirmPlanImage)
        val title      = view.findViewById<TextView>(R.id.tvTitle)
        val name       = view.findViewById<TextView>(R.id.tvPlanName)
        val amountTv   = view.findViewById<TextView>(R.id.tvAmountEntered)
        val tvRoiPct   = view.findViewById<TextView>(R.id.tvRoiPct)
        val tvPayoutAmt= view.findViewById<TextView>(R.id.tvPayoutAmt)
        val tvDirectAmt= view.findViewById<TextView>(R.id.tvDirectAmt)
        val btnCancel  = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirm)

        img.setImageResource(miningDrawableFor(plan.name))
        title.text = "Confirm Plan Purchase"
        name.text  = plan.name
        amountTv.text = walletVm.money(enteredAmount)

        val payoutAmt = enteredAmount * plan.payoutPercent / 100.0
        val directAmt = enteredAmount * plan.directPercent / 100.0
        tvRoiPct?.text     = "${"%.2f".format(plan.roiPercent)}%"
        tvPayoutAmt?.text  = walletVm.money(payoutAmt)
        tvDirectAmt?.text  = walletVm.money(directAmt)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // ✅ Hide loader exactly when the dialog is on screen
        dialog.setOnShowListener {
            // Post once more to ensure layout pass completed on some OEMs
            view.post { hideLoading() }
        }

        // 🛟 Safety guard: if setOnShow somehow never fires, hide after a tiny delay
        binding.root.postDelayed({ hideLoading() }, 600)

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
        view.findViewById<TextView>(R.id.tvAmountEntered).text = walletVm.money(enteredAmount)
        view.findViewById<TextView>(R.id.tvRoiPct)?.text = "—"
        view.findViewById<TextView>(R.id.tvPayoutAmt)?.text = "—"
        view.findViewById<TextView>(R.id.tvDirectAmt)?.text = "—"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.setOnShowListener {
            view.post { hideLoading() }
        }
        binding.root.postDelayed({ hideLoading() }, 600)

        dialog.show()

        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<MaterialButton>(R.id.btnConfirm).setOnClickListener {
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

    // ----- Utils -----
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
