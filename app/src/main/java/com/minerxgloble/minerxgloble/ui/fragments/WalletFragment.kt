package com.minerxgloble.minerxgloble.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnDetach
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentWalletBinding
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.viewModels.WalletViewModel

class WalletFragment : BaseFragment() {

    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding!!
    private val walletVm: WalletViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        walletVm.wallet.observe(viewLifecycleOwner) { snap ->
            if (snap == null) return@observe
            val acc = snap.account
            val raw = snap.raw

            setupDrawerTrigger(view)
            ProfileImageUtil.loadOrRefresh(
                requireContext(),
                uid = PrefService(requireContext()).getUserId().toString(),
                binding.walletCard.profileImage
            )
            // --- main three lines ---
            binding.depositAmt.text       = walletVm.money(acc.investment.totalDeposit)
            binding.fromEarningsAmt.text  = walletVm.money(acc.investment.depositFromEarnings)
            binding.totalInvestedamt.text = walletVm.money(acc.investment.totalInvestedInPlans)

            // --- big top card ---
            binding.walletCard.depositBalance.text =
                walletVm.money(acc.investment.currentBalance)

            // --- horizontal stats strip ---
            // Card 1: Total Invested
            binding.totalInvestedAmount.text =
                walletVm.money(acc.investment.totalInvestedInPlans)

            // Card 2: Total Deposit  ✅ use view binding (no findViewById)
            binding.totalDepositAmt.text =
                walletVm.money(acc.investment.totalDeposit)

            // Card 3: Total Withdraw (server-created: earnings.totalWithdrawn)
            val totalWithdrawn = walletVm.nestedDouble(raw, "earnings.totalWithdrawn")
            binding.withdrawAmt.text = walletVm.money(totalWithdrawn)
            // NEW: observe computed token USD and show it on the token card
            walletVm.tokenUsd.observe(viewLifecycleOwner) { usd ->
                binding.tokenAmt.text = walletVm.money(usd)
            }
        }

        binding.depositBtn.setOnClickListener {
            findNavController().navigate(R.id.action_walletFragment_to_depositFragment)
        }

        view.doOnDetach { _binding = null }
    }
}
