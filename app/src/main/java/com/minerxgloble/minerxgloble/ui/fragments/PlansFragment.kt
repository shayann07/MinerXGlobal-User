package com.minerxgloble.minerxgloble.ui.fragments

import android.os.Bundle
import android.view.*
import android.view.ViewGroup.MarginLayoutParams
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.PurchasedPlansAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentPlansBinding
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.viewModels.BuyPlanViewModel
import com.minerxgloble.minerxgloble.viewModels.StatusFilter
import kotlin.math.ceil

class PlansFragment : BaseFragment() {

    private var _binding: FragmentPlansBinding? = null
    private val b get() = _binding!!

    private val vm: BuyPlanViewModel by viewModels()
    private lateinit var adapter: PurchasedPlansAdapter

    // guards to repopulate skeletons only when size changes
    private var lastSkelW = 0
    private var lastSkelH = 0

    // unified state
    private var isLoadingNow = false
    private var hasAnyData = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlansBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        // Recycler
        adapter = PurchasedPlansAdapter(onItemClick = { /* navigate or sheet */ })
        b.rvPlans.layoutManager = LinearLayoutManager(requireContext())
        b.rvPlans.adapter = adapter

        // Filter pill (All / Active / Expired)
        val options = listOf("All", "Active", "Expired")
        b.btnFilter.text = options.first()
        b.btnFilter.setOnClickListener {
            val popup = PopupMenu(requireContext(), b.btnFilter)
            options.forEachIndexed { i, label -> popup.menu.add(Menu.NONE, i, i, label) }
            popup.setOnMenuItemClickListener { item ->
                val choice = options[item.itemId]
                b.btnFilter.text = choice
                vm.setFilter(
                    when (choice) {
                        "Active"  -> StatusFilter.ACTIVE
                        "Expired" -> StatusFilter.EXPIRED
                        else      -> StatusFilter.ALL
                    }
                )
                true
            }
            popup.show()
        }

        // Initial state: loading
        isLoadingNow = true
        hasAnyData = false
        renderState()

        // Observe data
        vm.plans.observe(viewLifecycleOwner) { list ->
            val safe = list ?: emptyList()
            adapter.submitList(safe)
            hasAnyData = safe.isNotEmpty()
            renderState()
        }
        vm.isPlansLoading.observe(viewLifecycleOwner) { loading ->
            isLoadingNow = loading == true
            renderState()
        }

        // Fill skeletons to viewport on size changes
        b.planListContainer.addOnLayoutChangeListener { _, l, t, r, btm, _, _, _, _ ->
            if (b.shimmerPlans.isVisible) {
                val w = r - l
                val h = btm - t
                if (w != lastSkelW || h != lastSkelH) {
                    lastSkelW = w
                    lastSkelH = h
                    populatePlanSkeletons()
                }
            }
        }

        // Load
        val uid = PrefService(requireContext()).getUserId().orEmpty()
        if (uid.isNotBlank()) vm.loadPurchasedPlans(uid)
    }

    /* -------------------- Unified render -------------------- */

    private fun renderState() {
        if (isLoadingNow) {
            // shimmer only
            b.shimmerPlans.isVisible = true
            b.rvPlans.isVisible = false
            b.emptyPlans.isVisible = false
            populatePlanSkeletons()
            b.shimmerPlans.startShimmer()
            return
        }

        b.shimmerPlans.stopShimmer()
        b.shimmerPlans.isVisible = false

        if (!hasAnyData) {
            // empty only
            b.emptyPlans.isVisible = true
            b.rvPlans.isVisible = false
        } else {
            // list only
            b.emptyPlans.isVisible = false
            b.rvPlans.isVisible = true
        }
    }

    /* -------------------- Shimmer skeleton rows -------------------- */

    private fun populatePlanSkeletons() {
        val container = b.skeletonPlanList
        container.removeAllViews()

        b.planListContainer.post {
            val viewportH = b.planListContainer.height
            if (viewportH <= 0) {
                repeat(3) { addPlanSkeletonRow(container) }
                return@post
            }

            val probe = addPlanSkeletonRow(container)
            probe.post {
                val lp = (probe.layoutParams as? MarginLayoutParams)
                val itemFullH = ((probe.measuredHeight) +
                        (lp?.topMargin ?: 0) + (lp?.bottomMargin ?: 0))
                    .coerceAtLeast(dp(140)) // 84dp image + paddings/margins

                val count = (ceil(viewportH / itemFullH.toDouble()) + 1)
                    .toInt()
                    .coerceAtLeast(2)

                repeat(count - 1) { addPlanSkeletonRow(container) }
            }
        }
    }

    private fun addPlanSkeletonRow(parent: ViewGroup): View {
        val row = layoutInflater.inflate(R.layout.skeleton_item_plan, parent, false)
        parent.addView(row)
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        b.shimmerPlans.stopShimmer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
