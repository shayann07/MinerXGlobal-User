package com.minerxgloble.minerxgloble.ui.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieDrawable
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.TransactionAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentWithdrawBinding
import com.minerxgloble.minerxgloble.models.TransactionModel
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.viewModels.TransactionViewModel
import com.minerxgloble.minerxgloble.viewModels.WalletViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.widget.EditText
import android.widget.LinearLayout
import java.text.DecimalFormat
import kotlin.math.ceil

class WithdrawFragment : BaseFragment() {

    private val BASE_URL = "https://minerxserviceapi.onrender.com"

    private var _binding: FragmentWithdrawBinding? = null
    private val binding get() = _binding!!

    private lateinit var transactionVM: TransactionViewModel
    private lateinit var adapter: TransactionAdapter

    private  val walletViewModel: WalletViewModel by activityViewModels()

    private var userId: String = ""
    private var allWithdrawals = emptyList<TransactionModel>()

    // audio
    private var coinPlayer: MediaPlayer? = null
    private var isCelebrating = false

    private val prefs by lazy {
        requireContext().getSharedPreferences("withdraw_prefs_$userId", Context.MODE_PRIVATE)
    }

    // guards to repopulate skeletons only when size changes
    private var lastSkelW = 0
    private var lastSkelH = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWithdrawBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        transactionVM = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return TransactionViewModel() as T
            }
        })[TransactionViewModel::class.java]

        adapter = TransactionAdapter(emptyList()) { /* optional onClick */ }
        binding.transactionRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.transactionRecycler.adapter = adapter

        userId = PrefService(requireContext()).getUserId().orEmpty()
        if (userId.isEmpty()) {
            showSnackbar("User ID not found", true)
            return
        }

        ProfileImageUtil.loadOrRefresh(
            requireContext(),
            uid = userId,
            binding.walletCard.profileImage
        )
        binding.walletCard.tvAccount.text = "Withdraw Wallet"

        // --- SHIMMER: start while first page is loading
        showTransactionsShimmer(true)

        transactionVM.withdrawals.observe(viewLifecycleOwner) { list ->
            allWithdrawals = list ?: emptyList()
            adapter.submitList(list)
            // stop shimmer as soon as first result arrives (even if empty—show empty state via list)
            showTransactionsShimmer(false)
        }
        transactionVM.fetchWithdrawalTransactions(userId)

        walletViewModel.wallet.observe(viewLifecycleOwner){
            binding.walletCard.earningsAmount.text = walletViewModel.money(it?.account?.earnings?.totalEarned)
        }

        binding.btnWithdraw.setOnClickListener { openWithdrawDialog() }

        binding.filterAllBtn.setOnClickListener {
            val uiOptions     = listOf("All", "Pending", "Processing", "Completed", "Rejected")
            val statusMapping = mapOf(
                "Pending"     to "pending_admin",
                "Processing"  to "processing",
                "Completed"   to "completed",
                "Rejected"    to "rejected"
            )

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filter Withdrawals")
                .setItems(uiOptions.toTypedArray()) { _, which ->
                    val selectedLabel = uiOptions[which]
                    val filtered = if (selectedLabel == "All") {
                        allWithdrawals
                    } else {
                        val statusValue = statusMapping[selectedLabel] ?: selectedLabel.lowercase()
                        allWithdrawals.filter { it.status.equals(statusValue, ignoreCase = true) }
                    }
                    adapter.submitList(filtered)
                    binding.filterAllBtn.text = selectedLabel
                }
                .show()
        }

        // Recompute skeleton fill on size change
        binding.transactionListContainer.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            if (binding.shimmerTransactions.isVisible) {
                val w = r - l
                val h = b - t
                if (w != lastSkelW || h != lastSkelH) {
                    lastSkelW = w
                    lastSkelH = h
                    populateTransactionSkeletons()
                }
            }
        }
    }

    // ───────────── Shimmer helpers ─────────────

    private fun showTransactionsShimmer(show: Boolean) {
        binding.transactionRecycler.isVisible = !show
        binding.shimmerTransactions.isVisible = show
        if (show) {
            populateTransactionSkeletons()
            binding.shimmerTransactions.startShimmer()
        } else {
            binding.shimmerTransactions.stopShimmer()
        }
    }

    private fun populateTransactionSkeletons() {
        val container = binding.skeletonTxnList
        container.removeAllViews()

        // wait for container size
        binding.transactionListContainer.post {
            val viewportH = binding.transactionListContainer.height
            if (viewportH <= 0) {
                repeat(3) { addTxnSkeletonRow(container) }
                return@post
            }

            val probe = addTxnSkeletonRow(container)
            probe.post {
                val lp = probe.layoutParams as ViewGroup.MarginLayoutParams
                val itemFullH = (probe.measuredHeight + lp.topMargin + lp.bottomMargin)
                    .coerceAtLeast(dp(100)) // safe min (84dp + paddings/margins)

                val count = (ceil(viewportH / itemFullH.toDouble()) + 1)
                    .toInt()
                    .coerceAtLeast(2)

                repeat(count - 1) { addTxnSkeletonRow(container) }
            }
        }
    }

    private fun addTxnSkeletonRow(parent: ViewGroup): View {
        val row = layoutInflater.inflate(R.layout.skeleton_item_transaction, parent, false)
        parent.addView(row)
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ───────────── Withdraw dialog / request (unchanged) ─────────────
    private fun openWithdrawDialog() {
        val ctx  = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_withdraw_input, null)

        val tilAmt   = view.findViewById<TextInputLayout>(R.id.tilWithdrawAmount)
        val etAmt    = view.findViewById<TextInputEditText>(R.id.etWithdrawAmount)
        val tilAddr  = view.findViewById<TextInputLayout>(R.id.tilAddress)
        val etAddr   = view.findViewById<TextInputEditText>(R.id.etAddress)
        val btnCancel= view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnW     = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWithdraw)

        val orange = ContextCompat.getColor(ctx, R.color.orange)
        tilAmt.setBoxStrokeColor(orange);  tilAmt.hintTextColor = ColorStateList.valueOf(orange)
        tilAddr.setBoxStrokeColor(orange); tilAddr.hintTextColor = ColorStateList.valueOf(orange)

        val dialog = MaterialAlertDialogBuilder(ctx).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnW.setOnClickListener {
            val amt  = etAmt.text?.toString()?.trim()?.toDoubleOrNull()
            val addr = etAddr.text?.toString()?.trim().orEmpty()
            if (amt == null || amt <= 0.0) { showSnackbar("Enter a valid amount", true); return@setOnClickListener }
            makeWithdrawRequest(userId, amt, addr)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun makeWithdrawRequest(userId: String, amount: Double, address: String) {
        showLoading()

        val json = JSONObject().apply {
            put("userId", userId)
            put("amount", amount)
            put("address", address)
        }

        val req = Request.Builder()
            .url("$BASE_URL/api/withdraw")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = OkHttpClient().newCall(req).execute()
                val bodyStr = resp.body?.string()
                withContext(Dispatchers.Main) {
                    hideLoading()
                    if (resp.isSuccessful) {
                        transactionVM.fetchWithdrawalTransactions(userId)
                        playWithdrawCelebration()
                    } else {
                        val msg = try { JSONObject(bodyStr ?: "{}").optString("error") } catch (_: Exception) { null }
                        showSnackbar(msg ?: "Withdraw failed: ${resp.code}", true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    showSnackbar("Exception: ${e.message}", true)
                }
            }
        }
    }

    // celebration (unchanged)
    private fun playWithdrawCelebration() {
        if (isCelebrating) return
        val lottie = binding.lottieCelebration
        isCelebrating = true

        lottie.cancelAnimation()
        lottie.removeAllAnimatorListeners()
        lottie.repeatCount = 0
        lottie.repeatMode = LottieDrawable.RESTART
        lottie.progress = 0f
        lottie.speed = 1.0f
        lottie.visibility = View.VISIBLE

        stopAndReleaseCoinPlayer()
        coinPlayer = MediaPlayer.create(requireContext(), R.raw.coin_drop).apply {
            isLooping = true
            setOnPreparedListener { start() }
            setOnErrorListener { mp, _, _ ->
                try { mp.reset(); mp.release() } catch (_: Exception) {}
                coinPlayer = null
                false
            }
        }

        lottie.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                lottie.removeAllAnimatorListeners()
                lottie.cancelAnimation()
                lottie.visibility = View.GONE
                stopAndReleaseCoinPlayer()
                isCelebrating = false
            }

            override fun onAnimationCancel(animation: Animator) {
                lottie.removeAllAnimatorListeners()
                lottie.visibility = View.GONE
                stopAndReleaseCoinPlayer()
                isCelebrating = false
            }
        })

        lottie.playAnimation()
    }

    private fun stopAndReleaseCoinPlayer() {
        coinPlayer?.let { mp ->
            try { mp.setOnCompletionListener(null); mp.setOnErrorListener(null); mp.isLooping = false; if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
            try { mp.reset() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
        }
        coinPlayer = null
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        val bgColor = ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bgColor)
        snack.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snack.show()
    }

    override fun onPause() {
        super.onPause()
        binding.shimmerTransactions.stopShimmer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
