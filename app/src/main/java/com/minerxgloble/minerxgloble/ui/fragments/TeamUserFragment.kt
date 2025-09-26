package com.minerxgloble.minerxgloble.ui.fragments

import android.view.View
import android.view.ViewGroup
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.TeamUserAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentTeamUserBinding
import com.minerxgloble.minerxgloble.models.TeamUser
import com.minerxgloble.minerxgloble.viewModels.TeamSelectionViewModel

class TeamUserFragment : BaseFragment() {


    private var _binding: FragmentTeamUserBinding? = null
    private val binding get() = _binding!!

    private val selectionVM: TeamSelectionViewModel by activityViewModels()

    private lateinit var adapter: TeamUserAdapter
    private var lastSkeletonWidth = 0
    private var lastSkeletonHeight = 0

    // 🔐 Source of truth for this screen: users from the selected LEVEL only.
    private var baseUsers: List<TeamUser> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)

        setupDrawerTrigger(v)
        adapter = TeamUserAdapter()
        binding.rvTeamUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTeamUsers.setHasFixedSize(true)
        binding.rvTeamUsers.adapter = adapter

        showUsersShimmer(true)

        // Observe the level selected in TeamLevelsFragment
        selectionVM.selectedLevel.observe(viewLifecycleOwner) { level ->
            if (level == null) {
                baseUsers = emptyList()
                applyFilterAndSubmit(baseUsers, getString(R.string.fragment_deposit_all))
                showUsersShimmer(false)
                showEmpty(true)
                return@observe
            }

            // Title like: "Team Users — Level 3 (Unlocked/Locked)"
            val state = if (level.levelUnlocked) "Unlocked" else "Locked"
            binding.title.text = getString(R.string.team_users_title_fmt, level.level, state)

            // 🚩 ONLY take users from this level (minimal: userId, name, status)
            baseUsers = level.users.toList()

            // Apply current filter label (default: "All")
            val currentLabel = binding.btnFilter.text?.toString()
                ?: getString(R.string.fragment_deposit_all)
            applyFilterAndSubmit(baseUsers, currentLabel)

            showUsersShimmer(false)
            showEmpty(baseUsers.isEmpty())
        }

        // Filter (All / Active / Inactive) operates on baseUsers only
        binding.btnFilter.setOnClickListener { showFilterChooser() }

        // Keep shimmer skeletons filling viewport
        binding.userListContainer.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            if (binding.shimmerUsers.isVisible) {
                val w = r - l
                val h = b - t
                if (w != lastSkeletonWidth || h != lastSkeletonHeight) {
                    lastSkeletonWidth = w
                    lastSkeletonHeight = h
                    populateUserSkeletons()
                }
            }
        }
    }

    private fun applyFilterAndSubmit(source: List<TeamUser>, label: String) {
        val filtered = when (label.lowercase()) {
            "active" -> source.filter { it.status.equals("active", true) }
            "inactive" -> source.filter { !it.status.equals("active", true) }
            else -> source
        }
        adapter.submit(filtered)
        binding.btnFilter.text = label.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }

    private fun showFilterChooser() {
        val labels = arrayOf(
            getString(R.string.fragment_deposit_all),
            "Active",
            "Inactive"
        )
        val current = 0
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter Users")
            .setSingleChoiceItems(labels, current) { d, which ->
                val selected = labels[which]
                applyFilterAndSubmit(baseUsers, selected)
                d.dismiss()
            }
            .show()
    }

    private fun showUsersShimmer(show: Boolean) {
        binding.rvTeamUsers.isVisible = !show
        binding.shimmerUsers.isVisible = show
        binding.emptyUsers.isVisible = false
        if (show) {
            populateUserSkeletons()
            binding.shimmerUsers.startShimmer()
        } else {
            binding.shimmerUsers.stopShimmer()
        }
    }

    private fun populateUserSkeletons() {
        val container = binding.skeletonUserList
        container.removeAllViews()
        binding.userListContainer.post {
            val viewportH = binding.userListContainer.height
            if (viewportH <= 0) {
                repeat(3) { addSkeletonRow(container) }
                return@post
            }
            val probe = addSkeletonRow(container)
            probe.post {
                val lp = probe.layoutParams as ViewGroup.MarginLayoutParams
                val itemFullH =
                    (probe.measuredHeight + lp.topMargin + lp.bottomMargin).coerceAtLeast(dp(86))
                val count =
                    (Math.ceil(viewportH / itemFullH.toDouble()) + 1).toInt().coerceAtLeast(2)
                repeat(count - 1) { addSkeletonRow(container) }
            }
        }
    }

    private fun addSkeletonRow(parent: ViewGroup): View {
        val row = layoutInflater.inflate(R.layout.skeleton_team_user, parent, false)
        parent.addView(row)
        return row
    }

    private fun showEmpty(show: Boolean) {
        binding.emptyUsers.isVisible = show
        binding.rvTeamUsers.isVisible = !show
        binding.shimmerUsers.isVisible = false
        if (!show) binding.shimmerUsers.stopShimmer()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        binding.shimmerUsers.stopShimmer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
