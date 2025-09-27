package com.minerxgloble.minerxgloble.ui.fragments

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues.TAG
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.TransactionAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentDepositBinding
import com.minerxgloble.minerxgloble.models.TransactionModel
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.utils.TransactionDialogUtil
import com.minerxgloble.minerxgloble.viewModels.TransactionViewModel
import com.minerxgloble.minerxgloble.viewModels.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DepositFragment : BaseFragment() {

    // --- Server base ---------------------------------------------------------
    private val BASE_URL = "https://minerxserviceapi.onrender.com"
    private val http by lazy { OkHttpClient() }

    // --- UI / VM -------------------------------------------------------------
    private lateinit var adapter: TransactionAdapter
    private lateinit var viewModel: TransactionViewModel
    private lateinit var binding: FragmentDepositBinding
    private var allDeposits: List<TransactionModel> = emptyList()
    private lateinit var currentUserId: String

    private val walletViewModel: WalletViewModel by activityViewModels()

    // --- In-memory “active invoice” (replaces SharedPreferences) -------------
    private var activeTxnId: String? = null
    private var activeAddress: String? = null
    private var activeAmount: String? = null
    private var activeExpiryMs: Long = 0L
    private var dialogShownThisSession = false

    private var lastSkelW = 0
    private var lastSkelH = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentDepositBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        viewModel = ViewModelProvider(requireActivity())[TransactionViewModel::class.java]

        // RecyclerView + adapter
        adapter = TransactionAdapter(emptyList()) {
            TransactionDialogUtil.showTransactionDialog(requireContext(),it)
        }
        binding.transactionRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.transactionRecycler.adapter = adapter

        // User ID (from PrefService)
        currentUserId = PrefService(requireContext()).getUserId() ?: ""
        if (currentUserId.isEmpty()) {
            showSnackbar("User not found", true)
            return
        }

        showTransactionsShimmer(true)
        // Fetch + observe deposits
        viewModel.fetchDeposits(currentUserId)
        viewModel.deposits.observe(viewLifecycleOwner) { list ->
            allDeposits = list ?: emptyList()
            adapter.submitList(list)
            showTransactionsShimmer(false)
        }
        viewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }

        walletViewModel.wallet.observe(viewLifecycleOwner) {
            binding.walletCard.earningsAmount.text =
                walletViewModel.money(it?.account?.investment?.currentBalance)
        }
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

        // Filter button
        binding.filterAllBtn.setOnClickListener {
            val options = listOf("All", "pending", "approved", "expired")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filter Deposits")
                .setItems(options.toTypedArray()) { _, which ->
                    val selected = options[which]
                    val filtered = if (selected == "All") {
                        allDeposits
                    } else {
                        allDeposits.filter { it.status.equals(selected, true) }
                    }
                    adapter.submitList(filtered)
                    binding.filterAllBtn.text = selected
                }
                .show()
        }

        // Profile image
        ProfileImageUtil.loadOrRefresh(
            requireContext(),
            uid = currentUserId,
            binding.walletCard.profileImage
        )

        // Deposit button
        binding.btnLeft.setOnClickListener {
            showLoading()
            lifecycleScope.launch {
                // 1) in-memory?           2) ask server?
                if (hasActiveInvoice() || fetchActiveDeposit()) {

                    showPaymentDialog()       // → show the QR dialog immediately
                } else {

                    openDepositInputDialog()  // → ask for amount (BottomSheet)
                }
                hideLoading()
            }
        }
    }

    // ========================================================================
    // Deposit creation flow (no SharedPreferences)
    // ========================================================================

    /** Is there an unexpired active invoice in memory? */
    private fun hasActiveInvoice(): Boolean {
        val stillValid = System.currentTimeMillis() < activeExpiryMs
        return activeTxnId != null && stillValid
    }

    /** Deposit amount dialog (Material, currency label from server configuration) */
    private fun openDepositInputDialog() {
        val ctx = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_deposit_input, null)
        val tilAmount = view.findViewById<TextInputLayout>(R.id.tilAmount)
        val etAmount = view.findViewById<TextInputEditText>(R.id.etAmount)
        val btnCancel =
            view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnDeposit =
            view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDeposit)

        val orange = ContextCompat.getColor(ctx, R.color.orange)
        tilAmount.boxStrokeColor = orange
        tilAmount.hintTextColor = ColorStateList.valueOf(orange)

        val dialog = BottomSheetDialog(ctx, R.style.TransparentBottomSheetDialog)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnDeposit.setOnClickListener {
            val amt = etAmount.text?.toString()?.trim()?.toDoubleOrNull()
            if (amt == null || amt <= 0.0) {
                showSnackbar("Enter a valid amount", true); return@setOnClickListener
            }
            if (amt < 5.0) { // Keep consistent with server MIN_DEPOSIT
                showSnackbar("Minimum deposit is 5 (server enforced)", true)
                return@setOnClickListener
            }

            val buyerEmail = "${currentUserId}@mxg.app"
            createDepositRequest(amt, buyerEmail, currentUserId)
            dialog.dismiss()
        }

        dialog.show()
    }

    /** Call backend to create (or reuse) a CoinPayments txn. */
    private fun createDepositRequest(amount: Double, email: String, userId: String) {
        showLoading()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("amount", amount.toString())
                    put("buyer_email", email)
                    put("custom", userId)
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("$BASE_URL/api/create-transaction")
                    .post(body)
                    .build()

                http.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string()
                    if (!resp.isSuccessful || raw.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            hideLoading()
                            val msg = runCatching { JSONObject(raw ?: "{}").optString("error") }
                                .getOrNull()
                            showSnackbar(msg ?: "Network error: ${resp.code}", true)
                        }
                        return@use
                    }

                    val jo = JSONObject(raw)
                    val err = jo.optString("error", "")
                    when (err) {
                        "ok", "active_deposit" -> {
                            val result = jo.getJSONObject("result")
                            val txnId = result.optString("txn_id")
                            val addressRaw = result.optString("address")
                            val amountStr = result.optString("amount")
                            val timeoutSec = result.optLong("timeout", 900L)

                            // Cache in memory only
                            activeTxnId = txnId
                            activeAddress = extractCleanAddressForCurrency(addressRaw)
                            activeAmount = amountStr
                            activeExpiryMs = System.currentTimeMillis() + timeoutSec * 1000L
                            dialogShownThisSession = false

                            withContext(Dispatchers.Main) {
                                hideLoading()
                                showPaymentDialog()
                            }
                        }
                        else -> {
                            withContext(Dispatchers.Main) {
                                hideLoading()
                                showSnackbar(if (err.isNotBlank()) err else "Unknown error", true)
                            }
                        }
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

    // ========================================================================
    // Payment dialog + QR + countdown
    // ========================================================================

    private fun showPaymentDialog() {
        val address = activeAddress.orEmpty()
        val amount = activeAmount ?: "0.00"
        val expiryMs = activeExpiryMs

        if (address.isBlank()) {
            showSnackbar("No active invoice", true)
            return
        }

        val ctx = context ?: return
        val dialog = Dialog(ctx)
        dialog.setContentView(R.layout.dialog_qr_scan)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.6f)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setCancelable(true)

        val ivQRCode = dialog.findViewById<ImageView>(R.id.ivQRCode)
        generateLocalQrCode(address, ivQRCode)

        dialog.findViewById<TextView>(R.id.sendingAddress)?.text = address
        dialog.findViewById<TextView>(R.id.amountVal)?.text = "Amount: $amount$"
        val tvTimer = dialog.findViewById<TextView>(R.id.tvTimerDialog)
        startDialogTimer(expiryMs, tvTimer)

        dialog.findViewById<ImageView>(R.id.copyButton)?.setOnClickListener {
            val clipboard =
                ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Payment Address", address))
            Toast.makeText(ctx, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
        dialogShownThisSession = true
    }
    /** returns true if a pending invoice was restored */
    private suspend fun fetchActiveDeposit(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/api/active-deposit/$currentUserId")
                .get().build()

            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: return@use false
                val jo  = JSONObject(raw)
                if (jo.optString("error") == "active_deposit") {
                    jo.getJSONObject("result").apply {
                        activeTxnId    = optString("txn_id")
                        activeAddress  = extractCleanAddressForCurrency(optString("address"))
                        activeAmount   = optString("amount")
                        activeExpiryMs = System.currentTimeMillis() + optLong("timeout", 0) * 1000L
                    }
                    return@withContext true
                }
            }
        } catch (_: Exception) { }
        false
    }

    private fun startDialogTimer(expiryMs: Long, tv: TextView?) {
        val remain = expiryMs - System.currentTimeMillis()
        if (remain <= 0L) {
            tv?.text = "Expired"
            // Clear in-memory invoice if expired
            clearActiveInvoice()
            return
        }
        object : CountDownTimer(remain, 1000) {
            override fun onTick(millis: Long) {
                tv?.text = formatMillis(millis)
            }

            override fun onFinish() {
                tv?.text = "Expired"
                clearActiveInvoice()
            }
        }.start()
    }

    private fun formatMillis(ms: Long): String {
        val sec = (ms / 1000) % 60
        val min = (ms / 1000 / 60) % 60
        val hr = ms / 1000 / 3600
        return String.format("Time left: %02d:%02d:%02d", hr, min, sec)
    }

    private fun clearActiveInvoice() {
        activeTxnId = null
        activeAddress = null
        activeAmount = null
        activeExpiryMs = 0L
        dialogShownThisSession = false
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Currency-aware cleanup. Works for LTCT testnet; keeps EVM cleanup for future. */
    private fun extractCleanAddressForCurrency(address: String): String {
        // For LTCT (testnet / mainnet), the address is typically already usable (tltc1..., m/n..., L/M...)
        // Keep EVM-style cleanup in case you switch back to USDT.BEP20 later.
        if (address.contains("?address=")) {
            val startIndex = address.indexOf("?address=") + 9
            val endIndex = address.indexOf("&", startIndex).takeIf { it > 0 } ?: address.length
            return address.substring(startIndex, endIndex)
        }
        if (address.startsWith("ethereum:") && address.contains("/transfer")) {
            val addressPart = address.split("/transfer").first().removePrefix("ethereum:")
            if (addressPart.matches(Regex("0x[a-fA-F0-9]{40}"))) return addressPart
        }
        if (address.startsWith("ethereum:")) {
            val addressPart = address.removePrefix("ethereum:")
            if (addressPart.matches(Regex("0x[a-fA-F0-9]{40}"))) return addressPart
        }
        if (address.matches(Regex("0x[a-fA-F0-9]{40}"))) return address
        return address
    }

    /** Local QR generation (ZXing) */
    private fun generateLocalQrCode(address: String, ivQRCode: ImageView) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val hints = hashMapOf<EncodeHintType, Any>(
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
                )
                val size = 512
                val bitMatrix =
                    QRCodeWriter().encode(address, BarcodeFormat.QR_CODE, size, size, hints)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
                withContext(Dispatchers.Main) {
                    ivQRCode.setImageBitmap(bitmap)
                    ivQRCode.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "QR generation error: ${e.message}", e)
            }
        }
    }
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

                val count = (kotlin.math.ceil(viewportH / itemFullH.toDouble()) + 1)
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
}
