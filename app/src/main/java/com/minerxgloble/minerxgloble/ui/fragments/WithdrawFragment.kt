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
import com.google.android.material.button.MaterialButton
import com.minerxgloble.minerxgloble.models.ActiveWithdrawalDTO
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

    // ---- Cache keys (top-level in class) ----
    private fun cacheKey(uid: String) = "active_withdraw_$uid"

    // Replace the old 'prefs' definition with:
    private val prefs by lazy {
        requireContext().getSharedPreferences("withdraw_prefs", Context.MODE_PRIVATE)
    }

    // Single shared client
    private val okHttpClient by lazy { okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(true).build() }

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

        setWithdrawButtonDefault()
        // 1) Try cache immediately (instant button toggle)
        readActiveFromCache(userId)?.let { cached ->
            if (cached.status.equals("pending_admin", ignoreCase = true)) {
                setWithdrawButtonAsCancelable(cached)
            }
        }

// 2) Then reconcile with server
        fetchActiveWithdrawal()

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
            val uiOptions     = listOf("All", "Pending", "Processing", "Completed", "Rejected","Canceled")
            val statusMapping = mapOf(
                "Pending"     to "pending_admin",
                "Processing"  to "processing",
                "Completed"   to "completed",
                "Rejected"    to "rejected",
                "Canceled"    to "canceled_by_user"
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
                val resp = okHttpClient.newCall(req).execute()
                val bodyStr = resp.body?.string()
                withContext(Dispatchers.Main) {
                    hideLoading()
                    if (resp.isSuccessful) {
                        // Try parse id/status/amount (adapt to your actual response)
                        var optimistic = ActiveWithdrawalDTO(id = "", status = "pending_admin", amountGross = amount)
                        try {
                            val root = JSONObject(bodyStr ?: "{}")
                            val w = root.optJSONObject("withdrawal")
                            if (w != null) {
                                optimistic = ActiveWithdrawalDTO(
                                    id = w.optString("id"),
                                    status = w.optString("status", "pending_admin"),
                                    amountGross = w.optDouble("amountGross", amount)
                                )
                            }
                        } catch (_: Exception) {}
                        // Save + instant UI switch
                        saveActiveToCache(userId, optimistic)
                        setWithdrawButtonAsCancelable(optimistic)

                        transactionVM.fetchWithdrawalTransactions(userId)
                        playWithdrawCelebration()
                        // Reconcile with server (ensures we get the real id if not parsed)
                        fetchActiveWithdrawal()
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
    private fun fetchActiveWithdrawal() {
        // GET {BASE_URL}/api/withdraw/active?uid=<userId>
        val url = "$BASE_URL/api/withdraw/active?uid=$userId"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder().url(url).get().build()
                val resp = okHttpClient.newCall(req).execute()
                val body = resp.body?.string()
                withContext(Dispatchers.Main) {
                    if (!resp.isSuccessful) {
                        setWithdrawButtonDefault()
                        return@withContext
                    }
                    val root = JSONObject(body ?: "{}")
                    val active = root.optJSONObject("active")
                    if (active == null) {
                        clearActiveCache(userId)
                        setWithdrawButtonDefault()
                    } else {
                        val dto = ActiveWithdrawalDTO(
                            id = active.optString("id"),
                            status = active.optString("status"),
                            amountGross = active.optDouble("amountGross")
                        )
                        if (dto.status.equals("pending_admin", ignoreCase = true)) {
                            saveActiveToCache(userId, dto)
                            setWithdrawButtonAsCancelable(dto)
                        } else {
                            clearActiveCache(userId)
                            setWithdrawButtonDefault()
                        }
                    }

                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { setWithdrawButtonDefault() }
            }
        }
    }
    private fun showCancelConfirmDialog(active: ActiveWithdrawalDTO) {
        val view = layoutInflater.inflate(R.layout.dialog_cancel_withdrawal, null, false)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setCancelable(true)
            .create()

        view.findViewById<MaterialButton>(R.id.btnNo).setOnClickListener { dialog.dismiss() }
        view.findViewById<MaterialButton>(R.id.btnYes).setOnClickListener {
            dialog.dismiss()
            cancelWithdrawal(active.id)
        }
        dialog.show()
    }

    private fun cancelWithdrawal(withdrawId: String) {
        showLoading()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply { put("uid", userId) }
                val req = Request.Builder()
                    .url("$BASE_URL/api/withdraw/$withdrawId/cancel")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val resp = okHttpClient.newCall(req).execute()
                val bodyStr = resp.body?.string()

                withContext(Dispatchers.Main) {
                    hideLoading()
                    if (resp.isSuccessful) {
                        // Flip UI back to normal state
                        clearActiveCache(userId)
                        setWithdrawButtonDefault()

                        // Refresh the list so the canceled row appears immediately
                        showTransactionsShimmer(true)
                        transactionVM.fetchWithdrawalTransactions(userId)

                        showSnackbar("Withdrawal canceled. Funds returned to your Earnings wallet.")
                    } else {
                        val err = try { JSONObject(bodyStr ?: "{}").optString("error") } catch (_: Exception) { null }
                        showSnackbar(err ?: "Couldn’t cancel withdrawal", true)

                        // If server says 409 (state changed), also just refresh UI
                        if (resp.code == 409) {
                            showTransactionsShimmer(true)
                            transactionVM.fetchWithdrawalTransactions(userId)
                            fetchActiveWithdrawal()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    showSnackbar("Network error: ${e.message}", true)
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

    private fun setWithdrawButtonDefault() {
        binding.btnWithdraw.text = getString(R.string.withdraw)
        binding.btnWithdraw.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.orange))
        binding.btnWithdraw.setOnClickListener { openWithdrawDialog() }
    }

    private fun setWithdrawButtonAsCancelable(active: ActiveWithdrawalDTO) {
        binding.btnWithdraw.text = getString(R.string.cancel_withdrawal) // "Cancel Withdrawal"
        binding.btnWithdraw.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.red))
        binding.btnWithdraw.setOnClickListener {
            showCancelConfirmDialog(active)
        }
    }

    // ---- Cache helpers ----
    private fun saveActiveToCache(uid: String, dto: ActiveWithdrawalDTO) {
        val obj = JSONObject().apply {
            put("id", dto.id)
            put("status", dto.status)
            if (dto.amountGross != null) put("amountGross", dto.amountGross)
        }
        prefs.edit().putString(cacheKey(uid), obj.toString()).apply()
    }

    private fun readActiveFromCache(uid: String): ActiveWithdrawalDTO? {
        val s = prefs.getString(cacheKey(uid), null) ?: return null
        return try {
            val o = JSONObject(s)
            ActiveWithdrawalDTO(
                id = o.optString("id"),
                status = o.optString("status"),
                amountGross = if (o.has("amountGross")) o.optDouble("amountGross") else null
            )
        } catch (_: Exception) { null }
    }

    private fun clearActiveCache(uid: String) {
        prefs.edit().remove(cacheKey(uid)).apply()
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
