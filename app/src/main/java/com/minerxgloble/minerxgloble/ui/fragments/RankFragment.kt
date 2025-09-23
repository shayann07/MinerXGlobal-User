package com.minerxgloble.minerxgloble.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.RankAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentRankBinding
import com.minerxgloble.minerxgloble.models.RankFilter
import com.minerxgloble.minerxgloble.models.RankItemState
import com.minerxgloble.minerxgloble.models.RankUiStatus
import com.minerxgloble.minerxgloble.repos.RankRepo
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.viewModels.RankViewModel
import com.minerxgloble.minerxgloble.viewModels.factory.RankVMFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class RankFragment : BaseFragment() {

    private var _binding: FragmentRankBinding? = null
    private val binding get() = _binding!!

    private val adapter = RankAdapter(onOpenDialog = { openDialog(it) })

    private val userId: String by lazy { PrefService(requireContext()).getUserId().orEmpty() }
    private val vm: RankViewModel by viewModels { RankVMFactory(RankRepo(), userId) }
    private val pref by lazy { PrefService(requireContext()) }

    // guards to repopulate skeletons only when size changes
    private var lastSkelW = 0
    private var lastSkelH = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRankBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        val fullName = pref.getString("name") ?: ""
        if (fullName.isNotBlank()) {
            binding.hiName.text = getString(R.string.hi_name, fullName.substringBefore(" "))
        }
        ProfileImageUtil.loadOrRefresh(requireContext(), uid = userId, imageView = binding.avatar)

        // Referral copy
        binding.inputReferral.apply {
            val referralLink = "https://minerxglobal.com/?ref=$userId"
            setText(referralLink)
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            isFocusable = false
            isClickable = true

            // (Optional) make text selectable on long-press
            setTextIsSelectable(true)

            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) {
                    val end = compoundDrawablesRelative[2] ?: return@setOnTouchListener false
                    val touchableStart = width - paddingEnd - end.intrinsicWidth
                    if (e.x >= touchableStart) {
                        val cb = requireContext().getSystemService(ClipboardManager::class.java)
                        cb?.setPrimaryClip(ClipData.newPlainText("Referral Link", referralLink))

                        // Give click/haptic feedback (optional)
                        performClick()
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                        // 👇 Show Snackbar only on lower Android versions
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                          showSnackbar("Referral link copied to clipboard")
                        }

                        return@setOnTouchListener true
                    }
                }
                false
            }
        }


        // Recycler
        binding.rankRewardsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.rankRewardsRecycler.setHasFixedSize(true)
        binding.rankRewardsRecycler.adapter = adapter

        // Start with shimmer for first load
        showRankShimmer(true)

        // Filter chooser
        binding.filterAllBtn.setOnClickListener { showFilterChooser() }

        // Collect state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collectLatest { s ->
                    // shimmer on/off from loading
                    showRankShimmer(s.loading)

                    s.error?.let {
                        Snackbar.make(requireView(), it, Snackbar.LENGTH_LONG).show()
                    }

                    // Update list according to current filter
                    adapter.submitList(vm.visibleItems())

                    // Button text reflects filter
                    binding.filterAllBtn.text = when (s.filter) {
                        RankFilter.ALL -> "Rank"
                        RankFilter.UNLOCKED -> "Claimable"
                        RankFilter.LOCKED -> "Locked"
                        RankFilter.CLAIMED -> "Claimed"
                    }
                }
            }
        }

        // Recompute skeleton fill on size change
        binding.rankListContainer.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            if (binding.shimmerRank.isVisible) {
                val w = r - l
                val h = b - t
                if (w != lastSkelW || h != lastSkelH) {
                    lastSkelW = w
                    lastSkelH = h
                    populateRankSkeletons()
                }
            }
        }

        // Initial fetch
        vm.refresh()
    }

    // ---------- Shimmer helpers (list only, using skeleton_item_rank) ----------

    private fun showRankShimmer(show: Boolean) {
        binding.rankRewardsRecycler.isVisible = !show
        binding.shimmerRank.isVisible = show
        if (show) {
            populateRankSkeletons()
            binding.shimmerRank.startShimmer()
        } else {
            binding.shimmerRank.stopShimmer()
        }
    }

    private fun populateRankSkeletons() {
        val container = binding.skeletonRankList
        container.removeAllViews()

        binding.rankListContainer.post {
            val viewportH = binding.rankListContainer.height
            if (viewportH <= 0) {
                repeat(3) { addSkeletonRow(container) }
                return@post
            }

            // Measure an exact skeleton row (incl. margins) based on skeleton_item_rank.xml
            val probe = addSkeletonRow(container)
            probe.post {
                val lp = probe.layoutParams as ViewGroup.MarginLayoutParams
                val itemFullH = (probe.measuredHeight + lp.topMargin + lp.bottomMargin)
                    .coerceAtLeast(dp(120)) // safe minimum

                // Fill viewport (+1 buffer avoids rounding gap)
                val count = (Math.ceil(viewportH / itemFullH.toDouble()) + 1)
                    .toInt()
                    .coerceAtLeast(2)

                // We already added one probe
                repeat(count - 1) { addSkeletonRow(container) }
            }
        }
    }

    private fun addSkeletonRow(parent: ViewGroup): View {
        // NOTE: this is the pixel-perfect skeleton for item_rank.xml
        val row = layoutInflater.inflate(R.layout.skeleton_item_rank, parent, false)
        parent.addView(row)
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- Dialog ----------

    private fun openDialog(item: RankItemState) {
        val ctx = requireContext()
        val v = layoutInflater.inflate(R.layout.dialog_rank_details, null)

        val titleTv = v.findViewById<TextView>(R.id.tvTitle)
        val rewardTv = v.findViewById<TextView>(R.id.tvReward)
        val directTv = v.findViewById<TextView>(R.id.tvDirect)
        val indirectTv = v.findViewById<TextView>(R.id.tvIndirect)
        val statusTv = v.findViewById<TextView>(R.id.tvStatus)
        val btnPrimary = v.findViewById<MaterialButton>(R.id.btnPrimary)
        val btnSecondary = v.findViewById<MaterialButton>(R.id.btnSecondary)

        val s = vm.state.value
        titleTv.text = item.def.title
        rewardTv.text = "Reward: ${fmt(item.def.reward)}$"
        directTv.text = "Direct (latest): ${fmt(s.direct)}$"
        indirectTv.text = "Indirect (latest): ${fmt(s.indirect)}$"
        statusTv.text = when (item.status) {
            RankUiStatus.CLAIMED -> "Status: Claimed"
            RankUiStatus.CLAIMABLE -> "Status: Claimable"
            RankUiStatus.LOCKED -> "Status: Locked"
        }

        btnPrimary.text = if (item.status == RankUiStatus.CLAIMABLE) "Claim" else "Close"
        btnSecondary.visibility =
            if (item.status == RankUiStatus.CLAIMABLE) View.VISIBLE else View.GONE

        val d = MaterialAlertDialogBuilder(ctx).setView(v).setCancelable(true).create()

        btnPrimary.setOnClickListener {
            if (item.status == RankUiStatus.CLAIMABLE) vm.claim(item.def.id)
            d.dismiss()
        }
        btnSecondary.setOnClickListener { d.dismiss() }

        d.show()
    }

    private fun showFilterChooser() {
        val labels = arrayOf("All", "Claimable", "Locked", "Claimed")
        val values =
            arrayOf(RankFilter.ALL, RankFilter.UNLOCKED, RankFilter.LOCKED, RankFilter.CLAIMED)
        val current = values.indexOf(vm.state.value.filter).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter ranks")
            .setSingleChoiceItems(labels, current) { d, which ->
                vm.setFilter(values[which])
                d.dismiss()
            }
            .show()
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val host = requireActivity().findViewById<View>(android.R.id.content)
        val snack = Snackbar.make(host, message, Snackbar.LENGTH_LONG)

        val bottomNav = requireActivity().findViewById<View?>(R.id.bottomNavBar)
        if (bottomNav?.isShown == true) snack.setAnchorView(bottomNav)

        val bg = ContextCompat.getColor(
            requireContext(), if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.setBackgroundTint(bg)
        snack.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))

        androidx.core.view.ViewCompat.setElevation(snack.view, 100f)
        snack.show()
    }
    private fun fmt(value: Double): String = DecimalFormat("#.##").format(value)

    override fun onPause() {
        super.onPause()
        // stop shimmer off-screen to save work
        binding.shimmerRank.stopShimmer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
