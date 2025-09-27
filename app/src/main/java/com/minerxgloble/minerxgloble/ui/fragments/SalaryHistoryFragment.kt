package com.minerxgloble.minerxgloble.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.TransactionAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentSalaryHistoryBinding
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.TransactionDialogUtil
import com.minerxgloble.minerxgloble.viewModels.TransactionViewModel
import kotlin.math.ceil

class SalaryHistoryFragment : BaseFragment() {

    private var _b: FragmentSalaryHistoryBinding? = null
    private val b get() = _b!!

    private val vm: TransactionViewModel by viewModels({ requireActivity() })
    private lateinit var adapter: TransactionAdapter

    // guards to repopulate skeletons only when size changes
    private var lastSkelW = 0
    private var lastSkelH = 0

    // unified UI state
    private var isLoadingNow = false
    private var hasAnyData = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentSalaryHistoryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        /* ---------- list ---------- */
        adapter = TransactionAdapter(emptyList()) {
            TransactionDialogUtil.showTransactionDialog(requireContext(), it)
        }
        b.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        b.rvHistory.adapter = adapter

        // initial state: loading
        isLoadingNow = true
        hasAnyData = false
        renderState()

        val userId = PrefService(requireContext()).getUserId().orEmpty()
        vm.fetchMergedTransactions(userId)

        // loading flag drives shimmer visibility only via renderState()
        vm.loading.observe(viewLifecycleOwner) { loading ->
            isLoadingNow = loading == true
            renderState()
        }

        // data observer: sort latest-first, update list, then render
        vm.allTransactions.observe(viewLifecycleOwner) { txns ->
            val sorted = (txns ?: emptyList()).sortedByDescending { it.timestamp }
            adapter.submitList(sorted)
            hasAnyData = sorted.isNotEmpty()
            renderState()   // will pick list vs empty based on flags
        }

        // Filter button (pretty labels for UX, raw keys for filtering)
        b.btnFilter.setOnClickListener {
            // label -> raw key (keep these raw values exactly as your VM expects)
            val filterMap = linkedMapOf(
                "All" to "All",
                "Plan Purchase" to "Plan Purchase",
                "Daily Income" to "dailyRoi",
                "Team Income" to "teamProfit",
                "Rank Income" to "rank-reward",
                "Monthly Salary" to "star-salary",
                "Deposit" to "Deposit",
                "Withdraw" to "Withdraw",
                "Lucky Draw " to "Lucky Draw Investment",
                "Direct Income" to "Direct Profit"
            )

            val labels = filterMap.keys.toTypedArray()

            MaterialAlertDialogBuilder(requireContext()).setTitle("Filter Transactions")
                .setItems(labels) { _, which ->
                    val label = labels[which]
                    val raw = filterMap[label] ?: "All"
                    b.btnFilter.text = label        // show pretty text on the chip/button
                    vm.applyFilter(raw)             // still filter by your raw key
                }.show()
        }

        // Recompute skeleton fill on size change to perfectly fill viewport
        b.transactionListContainer.addOnLayoutChangeListener { _, l, t, r, btm, _, _, _, _ ->
            if (b.shimmerTransactions.isVisible) {
                val w = r - l
                val h = btm - t
                if (w != lastSkelW || h != lastSkelH) {
                    lastSkelW = w
                    lastSkelH = h
                    populateTransactionSkeletons()
                }
            }
        }
    }

    /* -------------------- Unified render -------------------- */

    private fun renderState() {
        // Loading → show shimmer only
        if (isLoadingNow) {
            b.shimmerTransactions.isVisible = true
            b.rvHistory.isVisible = false
            b.emptyState.isVisible = false
            populateTransactionSkeletons()
            b.shimmerTransactions.startShimmer()
            return
        }

        // Not loading: hide shimmer
        b.shimmerTransactions.stopShimmer()
        b.shimmerTransactions.isVisible = false

        if (!hasAnyData) {
            // No data → show empty only
            b.emptyState.isVisible = true
            b.rvHistory.isVisible = false
        } else {
            // Has data → show list only
            b.emptyState.isVisible = false
            b.rvHistory.isVisible = true
        }
    }

    /* -------------------- Shimmer skeleton rows -------------------- */

    private fun populateTransactionSkeletons() {
        val container = b.skeletonTxnList
        container.removeAllViews()

        b.transactionListContainer.post {
            val viewportH = b.transactionListContainer.height
            if (viewportH <= 0) {
                repeat(3) { addTxnSkeletonRow(container) }
                return@post
            }

            val probe = addTxnSkeletonRow(container)
            probe.post {
                val lp = (probe.layoutParams as? MarginLayoutParams)
                val itemFullH = ((probe.measuredHeight) + (lp?.topMargin ?: 0) + (lp?.bottomMargin
                    ?: 0)).coerceAtLeast(dp(100)) // safe minimum

                val count = (ceil(viewportH / itemFullH.toDouble()) + 1).toInt().coerceAtLeast(2)

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

    override fun onPause() {
        super.onPause()
        b.shimmerTransactions.stopShimmer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
