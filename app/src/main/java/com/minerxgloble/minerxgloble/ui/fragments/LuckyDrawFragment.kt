package com.minerxgloble.minerxgloble.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.WinnersAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentLuckyDrawBinding
import com.minerxgloble.minerxgloble.repos.LuckyDrawRepository
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.viewModels.LuckyDrawViewModel
import com.minerxgloble.minerxgloble.viewModels.factory.LuckyDrawVMFactory
import kotlinx.coroutines.flow.collectLatest

class LuckyDrawFragment : BaseFragment() {

    private var _binding: FragmentLuckyDrawBinding? = null
    private val binding get() = _binding!!

    private val repo by lazy { LuckyDrawRepository(FirebaseFirestore.getInstance()) }
    private val vm: LuckyDrawViewModel by viewModels { LuckyDrawVMFactory(repo) }

    private lateinit var winnersAdapter: WinnersAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLuckyDrawBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)

        setupDrawerTrigger(v)

        // Custom MXG userId
        val mxgUserId = PrefService(requireContext()).getUserId().orEmpty()

        // Greet
        PrefService(requireContext()).getString("name")?.takeIf { it.isNotBlank() }?.let { full ->
            binding.hiName.text = getString(R.string.hi_name, full.substringBefore(" "))
        }

        // Avatar
        ProfileImageUtil.loadOrRefresh(requireContext(), uid = mxgUserId, imageView = binding.avatar)

        // If userId missing, disable invest and inform user
        if (mxgUserId.isBlank()) {
            binding.btnInvest.isEnabled = false
            showSnackbar("User ID not found. Please log in again.", isError = true)
        } else {
            vm.bindUser(mxgUserId)
        }

        // Recycler
        winnersAdapter = WinnersAdapter()
        binding.rvWinners.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = winnersAdapter
        }

        // Collect UI state
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.ui.collectLatest { ui ->
                // Shimmer
                binding.shimmerWinners.isVisible = ui.loading
                if (ui.loading) binding.shimmerWinners.startShimmer() else binding.shimmerWinners.stopShimmer()

                // Winners list
                winnersAdapter.submit(ui.winners)

                // Header text
                binding.tvMyTotalInvested.text =
                    "Your total invested in draw: $${String.format("%.0f", ui.myTotalInvested)}"

                // Global loader via BaseFragment
                if (ui.investInFlight) showLoading() else hideLoading()

                // Invest button state
                binding.btnInvest.isEnabled = !ui.investInFlight && mxgUserId.isNotBlank()
                binding.btnInvest.alpha = if (ui.investInFlight) 0.6f else 1f

                // Snackbar (replaces Toast)
                ui.toast?.let {
                    // If your VM encodes success/error, map it here; default success style
                    showSnackbar(it)
                    vm.clearToast()
                }
            }
        }

        // Invest click => consent dialog
        binding.btnInvest.setOnClickListener { showConsentDialog() }
    }

    private fun showConsentDialog() {
        val ctx = context ?: return
        val dialog = Dialog(ctx)
        dialog.setContentView(R.layout.dialoge_download_xml) // your themed dialog layout
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.6f)
        dialog.setCanceledOnTouchOutside(true)

        dialog.findViewById<TextView>(R.id.dlTitle)?.text = getString(R.string.invest_1)
        dialog.findViewById<TextView>(R.id.dlSubtitle)?.text =
            "Do you want to invest $1 in the Lucky Draw?"

        dialog.findViewById<MaterialButton>(R.id.btnDownload)?.apply {
            text = getString(R.string.confirm)
            setOnClickListener {
                // Immediate feedback via BaseFragment loader
                showLoading()
                binding.btnInvest.isEnabled = false

                vm.investOneDollar()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    /** Centralized Snackbar helper (anchors to bottom nav if visible) */
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

    override fun onDestroyView() {
        super.onDestroyView()
        binding.shimmerWinners.stopShimmer()
        _binding = null
    }
}
