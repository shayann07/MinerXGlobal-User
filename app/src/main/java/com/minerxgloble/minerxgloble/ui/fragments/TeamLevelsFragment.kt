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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.TeamLevelAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentTeamLevelsBinding
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.viewModels.TeamLevelViewModel
import com.minerxgloble.minerxgloble.viewModels.TeamSelectionViewModel

class TeamLevelsFragment : BaseFragment() {

    private var _binding: FragmentTeamLevelsBinding? = null
    private val binding get() = _binding!!

    private val vm: TeamLevelViewModel by viewModels()
    private lateinit var adapter: TeamLevelAdapter
    private val pref by lazy { PrefService(requireContext()) }
    private val userCode: String by lazy { PrefService(requireContext()).getUserId().orEmpty() }

    // guard to avoid redundant population loops
    private var lastSkeletonWidth = 0
    private var lastSkeletonHeight = 0

    private val selectionVM: TeamSelectionViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamLevelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        val fullName = pref.getString("name") ?: ""
        if (fullName.isNotBlank()) {
            val firstName = fullName.substringBefore(" ")
            binding.hiName.text = getString(R.string.hi_name, firstName)
        }

        ProfileImageUtil.loadOrRefresh(
            requireContext(), uid = userCode, imageView = binding.avatar
        )

        adapter = TeamLevelAdapter { item ->
            selectionVM.select(item)

            if (item.levelUnlocked) {
                // Navigate when unlocked
                findNavController().navigate(R.id.action_teamLevelsFragment_to_teamUserFragment)
            } else {
                // Show snackbar only for locked
                showSnackbar("Level ${item.level} is locked")
            }
        }

        binding.teamLevelRv.layoutManager = LinearLayoutManager(requireContext())
        binding.teamLevelRv.setHasFixedSize(true)
        binding.teamLevelRv.adapter = adapter

        // Prefill shimmer while loading
        showLevelsShimmer(true)

        // Referral copy
        binding.inputReferral.apply {
            val referralLink = "https://minerxglobal.com/?ref=$userCode"
            setText(referralLink)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
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


        // Observe VM
        vm.levels.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            // stop shimmer as soon as we have a list — even if empty (your call to show empty state)
            showLevelsShimmer(false)
        }
        vm.loading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) showLevelsShimmer(true) else showLevelsShimmer(false)
        }
        vm.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) showSnackbar(err, true)
        }

        // Recompute skeleton count on layout changes to always fill visible space
        binding.levelsListContainer.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            if (binding.shimmerLevels.isVisible) {
                val w = right - left
                val h = bottom - top
                if (w != lastSkeletonWidth || h != lastSkeletonHeight) {
                    lastSkeletonWidth = w
                    lastSkeletonHeight = h
                    populateLevelSkeletons()
                }
            }
        }

        // Filter button logic
        binding.filterAllBtn.setOnClickListener { showFilterChooser() }

        // Initial load
        vm.load()
    }

    private fun showFilterChooser() {
        val labels = arrayOf("All", "Unlocked", "Locked")
        val current = 0 // default All

        MaterialAlertDialogBuilder(requireContext()).setTitle("Filter Levels")
            .setSingleChoiceItems(labels, current) { d, which ->
                val selected = labels[which]
                vm.levels.value?.let { list ->
                    val filtered = when (selected) {
                        "Unlocked" -> list.filter { it.levelUnlocked }
                        "Locked" -> list.filter { !it.levelUnlocked }
                        else -> list
                    }
                    adapter.submitList(filtered)
                    binding.filterAllBtn.text = selected
                }
                d.dismiss()
            }.show()
    }

    private fun showLevelsShimmer(show: Boolean) {
        binding.teamLevelRv.isVisible = !show
        binding.shimmerLevels.isVisible = show
        if (show) {
            populateLevelSkeletons()
            binding.shimmerLevels.startShimmer()
        } else {
            binding.shimmerLevels.stopShimmer()
        }
    }

    private fun populateLevelSkeletons() {
        val container = binding.skeletonLevelsList
        container.removeAllViews()

        // Wait until container has a definite size
        binding.levelsListContainer.post {
            val viewportH = binding.levelsListContainer.height
            if (viewportH <= 0) {
                // fallback: add a few placeholders
                repeat(3) { addSkeletonRow(container) }
                return@post
            }

            // Add a probe row to measure its exact height including margins
            val probe = addSkeletonRow(container)
            probe.post {
                val lp = probe.layoutParams as ViewGroup.MarginLayoutParams
                val itemFullH =
                    (probe.measuredHeight + lp.topMargin + lp.bottomMargin).coerceAtLeast(dp(88)) // safe minimum

                // Fill the viewport. +1 buffer avoids 1px gaps due to rounding.
                val count =
                    (Math.ceil(viewportH / itemFullH.toDouble()) + 1).toInt().coerceAtLeast(2)

                // We already added one probe
                repeat(count - 1) { addSkeletonRow(container) }
            }
        }
    }

    private fun addSkeletonRow(parent: ViewGroup): View {
        val row = layoutInflater.inflate(R.layout.skeleton_team_level, parent, false)
        parent.addView(row)
        return row
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

    override fun onPause() {
        super.onPause()
        // Avoid doing shimmer work off-screen
        binding.shimmerLevels.stopShimmer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
