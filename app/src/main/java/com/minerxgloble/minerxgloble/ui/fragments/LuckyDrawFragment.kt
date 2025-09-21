package com.minerxgloble.minerxgloble.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.WinnersAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentLuckyDrawBinding
import com.minerxgloble.minerxgloble.repos.LuckyDrawRepository
import com.minerxgloble.minerxgloble.viewModels.LuckyDrawViewModel
import com.minerxgloble.minerxgloble.viewModels.factory.LuckyDrawVMFactory
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
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
        // --- Use your PrefService to fetch the custom MXG userId ---
        val mxgUserId = PrefService(requireContext()).getUserId().orEmpty()
// greet user
        PrefService(requireContext()).getString("name")?.takeIf { it.isNotBlank() }?.let { full ->
            binding.hiName.text = getString(R.string.hi_name, full.substringBefore(" "))
        }

        ProfileImageUtil.loadOrRefresh(
            requireContext(),
            uid = mxgUserId,
            binding.avatar
        )
        // If userId missing, disable invest and inform user
        if (mxgUserId.isBlank()) {
            binding.btnInvest.isEnabled = false
            Toast.makeText(requireContext(), "User ID not found. Please log in again.", Toast.LENGTH_SHORT).show()
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
                // shimmer
                binding.shimmerWinners.isVisible = ui.loading
                if (ui.loading) binding.shimmerWinners.startShimmer()
                else binding.shimmerWinners.stopShimmer()

                // winners list
                winnersAdapter.submit(ui.winners)

                // header: total invested text
                binding.tvMyTotalInvested.text =
                    "Your total invested in draw: $${String.format("%.0f", ui.myTotalInvested)}"

                // invest button state
                binding.btnInvest.isEnabled = !ui.investInFlight && mxgUserId.isNotBlank()
                binding.btnInvest.alpha = if (ui.investInFlight) 0.6f else 1f

                // toast
                ui.toast?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
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
            "Do you Want to invest $1 dollar in the Lucky Draw?"

        dialog.findViewById<MaterialButton>(R.id.btnDownload)?.apply {
            text = getString(R.string.confirm)
            setOnClickListener {
                vm.investOneDollar()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.shimmerWinners.stopShimmer()
        _binding = null
    }
}
