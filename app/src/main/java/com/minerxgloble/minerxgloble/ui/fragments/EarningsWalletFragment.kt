package com.minerxgloble.minerxgloble.ui.fragments

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentEarningsWalletBinding
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.viewModels.WalletViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class EarningsWalletFragment : BaseFragment() {

    private var _binding: FragmentEarningsWalletBinding? = null
    private val binding get() = _binding!!

    private val walletVm: WalletViewModel by activityViewModels()

    // 🔗 Server base (update if your domain differs)
    private val API_BASE = "https://minerxserviceapi.onrender.com"
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEarningsWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)
        // Withdraw navigation
        binding.amountWithdrawBtn.setOnClickListener {
            findNavController().navigate(R.id.action_earningsWalletFragment_to_withdrawFragment)
        }

        // ✅ Transfer flow
        binding.amountTransfer.setOnClickListener {
            showTransferDialog()
        }

        setupDrawerTrigger(view)
        ProfileImageUtil.loadOrRefresh(
            requireContext(),
            uid = PrefService(requireContext()).getUserId().toString(),
            binding.walletCard.profileImage
        )

        // Live-bind wallet snapshot
        walletVm.wallet.observe(viewLifecycleOwner) { snap ->
            if (snap == null) return@observe

            val acc = snap.account
            val raw = snap.raw

            // Top 4 rows
            binding.amtRoi.text         = walletVm.money(acc.earnings.totalRoi)
            binding.teamProfitAmt.text  = walletVm.money(acc.earnings.teamProfit)
            binding.referralAmt.text    = walletVm.money(acc.earnings.referralProfit)
            binding.totalEarnedAmt.text = walletVm.money(acc.earnings.totalEarnedToDate)

            // Horizontal stats
            binding.totalInvestedAmount.text =
                walletVm.money(acc.investment.totalInvestedInPlans)

            // Total Deposit
            binding.totalDepositAmt.text =
                walletVm.money(acc.investment.totalDeposit)

            // Total Withdraw
            val withdrawn = walletVm.nestedDouble(raw, "earnings.totalWithdrawn")
            binding.withdrawAmt.text = walletVm.money(withdrawn)

            // Big card earnings balance
            binding.walletCard.earningsBalance.text =
                walletVm.money(walletVm.totalEarningsBalance(snap))

        }
        // NEW: observe computed token USD and show it on the token card
        walletVm.tokenUsd.observe(viewLifecycleOwner) { usd ->
            binding.tokenAmt.text = walletVm.money(usd)
        }
    }

    private fun showTransferDialog() {
        val ctx = requireContext()
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_transfer_to_deposit, null, false)

        val tvCurrency = dialogView.findViewById<TextView>(R.id.tvCurrency)
        val tilAmount = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilAmount)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnDeposit = dialogView.findViewById<MaterialButton>(R.id.btnDeposit)

        tvCurrency.text = "Transfer USDT to Investment Wallet"
        btnDeposit.text = "Transfer"

        val ad = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { ad.dismiss() }

        btnDeposit.setOnClickListener {
            val amountStr = etAmount.text?.toString()?.trim().orEmpty()
            val amount = amountStr.toDoubleOrNull()

            if (amount == null || amount <= 0) {
                tilAmount.error = "Enter a valid amount"
                return@setOnClickListener
            } else {
                tilAmount.error = null
            }

            val userId = PrefService(ctx).getUserId()
            if (userId.isNullOrBlank()) {
                showSnackbar("User not logged in")
                return@setOnClickListener
            }

            // show global loader from BaseFragment
            showLoading()

            // prevent double taps
            btnDeposit.isEnabled = false
            btnCancel.isEnabled = false

            val handler = CoroutineExceptionHandler { _, e ->
                // re-enable + hide loader even on crash inside coroutine
                btnDeposit.isEnabled = true
                btnCancel.isEnabled = true
                hideLoading()
               showSnackbar("Transfer failed: ${e.message}")
            }

            viewLifecycleOwner.lifecycleScope.launch(handler) {
                try {
                    val (ok, message) = withContext(Dispatchers.IO) {
                        callTransferEndpoint(userId, amount, note = "Transfer from Android app")
                    }

                    if (ok) {
                        showSnackbar("Transfer successful")
                        ad.dismiss()
                    } else {
                        showSnackbar(message.ifBlank { "Transfer failed" })
                    }
                } finally {
                    // always hide loader & re-enable buttons
                    btnDeposit.isEnabled = true
                    btnCancel.isEnabled = true
                    hideLoading()
                }
            }
        }


        ad.show()
    }



    /**
     * POST /api/transfer-earnings-to-investment
     * Body: { userId: string, amount: number, note?: string }
     */
    private fun callTransferEndpoint(userId: String, amount: Double, note: String?): Pair<Boolean, String> {
        val url = "$API_BASE/api/transfer-earnings-to-investment"
        val payload = JSONObject().apply {
            put("userId", userId)
            put("amount", amount)
            if (!note.isNullOrBlank()) put("note", note)
        }.toString().toRequestBody(jsonMedia)

        val req = Request.Builder()
            .url(url)
            .post(payload)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            return if (resp.isSuccessful) {
                // success payload: { ok: true, transferId }
                runCatching {
                    val json = JSONObject(body)
                    val ok = json.optBoolean("ok", false)
                    if (ok) true to "ok"
                    else false to json.optString("error", "Transfer failed")
                }.getOrElse { false to "Transfer failed" }
            } else {
                // error payload: { error: "..."}
                runCatching {
                    val json = JSONObject(body)
                    false to json.optString("error", "Transfer failed (${resp.code})")
                }.getOrElse { false to "Transfer failed (${resp.code})" }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    private fun showSnackbar(message: String, isError: Boolean = false) {


        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        val bg = ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bg)
        snack.setTextColor(
            ContextCompat.getColor(requireContext(), android.R.color.white)
        )
        snack.show()
    }

}
