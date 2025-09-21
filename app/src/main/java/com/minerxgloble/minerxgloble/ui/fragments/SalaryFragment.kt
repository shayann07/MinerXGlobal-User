package com.minerxgloble.minerxgloble.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.snackbar.Snackbar
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentSalaryBinding
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.viewModels.SalaryUiState
import com.minerxgloble.minerxgloble.viewModels.SalaryViewModel

class SalaryFragment : BaseFragment() {
    private var _binding: FragmentSalaryBinding? = null
    private val binding get() = _binding!!
    private val vm: SalaryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        // Use your persisted UID
        val userId = PrefService(requireContext()).getUserId().toString()

        // Observe state
        vm.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SalaryUiState.Loading -> setLoading(true)
                is SalaryUiState.Success -> {
                    setLoading(false)
                    bindPreview(state.data)
                }

                is SalaryUiState.Error -> {
                    setLoading(false)
                    showSnackbar(state.message, true)
                }

                else -> {}
            }
        }

        // Kick off: current month live preview
        vm.loadPreview(userId)
    }

    // ---- UI binding helpers -------------------------------------------------

    // Level thresholds (Star 1..5)
    private val SELF_T = listOf(100, 200, 400, 800, 1600)
    private val DIRECT_T = listOf(1000, 2000, 4000, 8000, 16000)
    private val INDIRECT_T = listOf(3000, 6000, 12000, 24000, 48000)

    private fun formatMoney(n: Number?): String = "$" + ((n?.toDouble() ?: 0.0).toLong())

    private fun <T : Number> asDouble(m: Any?, key: String): Double {
        val v = (m as? Map<*, *>)?.get(key)
        return when (v) {
            is Number -> v.toDouble()
            else -> 0.0
        }
    }

    private fun asDouble(map: Map<String, Any?>, key: String): Double {
        val v = map[key]
        return if (v is Number) v.toDouble() else 0.0
    }

    // ───────────────── Single-active-level helpers ─────────────────

    /**
     * Returns the index (0..4) of the FIRST level where ANY requirement is unmet.
     * If all levels are already met, returns the last index (so bars show 100%).
     */
    private fun currentTargetLevelIndex(self: Double, direct: Double, indirect: Double): Int {
        for (i in SELF_T.indices) {
            val needSelf = SELF_T[i].toDouble()
            val needDirect = DIRECT_T[i].toDouble()
            val needIndirect = INDIRECT_T[i].toDouble()
            val unmet = (self < needSelf) || (direct < needDirect) || (indirect < needIndirect)
            if (unmet) return i
        }
        return SELF_T.lastIndex
    }

    /** Percentage capped to the target threshold (no overflow into next level). */
    private fun pctCapped(value: Double, target: Int): Int {
        if (target <= 0) return 100
        val capped = value.coerceAtMost(target.toDouble())
        return ((capped / target.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    // -------------------------------------------------------------------------

    private fun bindPreview(data: Map<String, Any?>) {
        val mode = (data["mode"] as? String) ?: "preview"
        val monthKey = (data["monthKey"] as? String) ?: "--"

        // Current MTD volumes
        val self = asDouble(data, "self")
        val direct = asDouble(data, "direct")
        val indirect = asDouble(data, "indirect")

        // Current star & salary (preview or snapshot)
        val starNow =
            (data["starIfLockedNow"] as? Number)?.toInt() ?: (data["star"] as? Number)?.toInt() ?: 0
        val salaryNow = (data["salaryIfLockedNow"] as? Number) ?: (data["salary"] as? Number) ?: 0

        // Remaining to next star (server-provided)
        val remaining = (data["remainingToNextStar"] as? Map<*, *>) ?: emptyMap<Any, Any>()
        val nextStar = (remaining["nextStar"] as? Number)?.toInt()
        val nextSalary = (remaining["nextSalary"] as? Number)

        // Header card
        binding.tvMonthKey.text = monthKey
        binding.chipMode.text = if (mode == "snapshot") "Snapshot" else "Preview"
        binding.chipCurrentStar.text = "Star $starNow"
        binding.tvSalaryPreview.text = formatMoney(salaryNow)
        binding.tvIfMonthEndedHint.visibility = if (mode == "snapshot") View.GONE else View.VISIBLE

        // Orange tiles (always show full actual amounts)
        binding.tvSelfInvestAmount.text = formatMoney(self.toLong())
        binding.tvDirectActive.text = formatMoney(direct.toLong())
        binding.indirectActive.text = formatMoney(indirect.toLong())

        // ── Single active level progress (cap to target; no bleed to next level)
        val lvl = currentTargetLevelIndex(self, direct, indirect)
        val sTarget = SELF_T[lvl]
        val dTarget = DIRECT_T[lvl]
        val iTarget = INDIRECT_T[lvl]

        // Text shows "currentCapped / target" for the active level
        binding.tvSelfProgress.text = "${
            formatMoney(
                self.coerceAtMost(sTarget.toDouble()).toLong()
            )
        } / ${formatMoney(sTarget)}"
        binding.tvDirectProgress.text =
            "${formatMoney(direct.coerceAtMost(dTarget.toDouble()).toLong())} / ${
                formatMoney(
                    dTarget
                )
            }"
        binding.tvIndirectProgress.text =
            "${formatMoney(indirect.coerceAtMost(iTarget.toDouble()).toLong())} / ${
                formatMoney(
                    iTarget
                )
            }"

        // Bars capped to the active level only
        binding.progressSelf.progress = pctCapped(self, sTarget)
        binding.progressDirect.progress = pctCapped(direct, dTarget)
        binding.progressIndirect.progress = pctCapped(indirect, iTarget)

        // Next star card
        binding.tvNextStar.text = "Next: Star ${nextStar ?: "—"}"
        binding.tvNextSalary.text =
            if (nextSalary != null) "Salary ${formatMoney(nextSalary)}" else "Salary —"
        binding.chipRemainSelf.text = "Self: ${formatMoney(remaining["self"] as? Number)}"
        binding.chipRemainDirect.text = "Direct: ${formatMoney(remaining["direct"] as? Number)}"
        binding.chipRemainIndirect.text =
            "Indirect: ${formatMoney(remaining["indirect"] as? Number)}"
    }

    private fun setLoading(loading: Boolean) {
        // Proper shimmer overlay instead of dimming root
        val shimmer: ShimmerFrameLayout = binding.shimmerOverlay
        if (loading) {
            // Show overlay and start shimmer; block touches
            shimmer.visibility = View.VISIBLE
            shimmer.startShimmer()
            // Optionally dim/hide real content during load to avoid jank
            binding.scrollContainer.alpha = 0f
            binding.scrollContainer.isEnabled = false
        } else {
            // Cross-fade content in, then stop shimmer
            binding.scrollContainer.animate().alpha(1f).setDuration(200).withEndAction {
                binding.scrollContainer.isEnabled = true
            }.start()
            shimmer.stopShimmer()
            shimmer.visibility = View.GONE
        }
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snack = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
        val bgColor = ContextCompat.getColor(
            requireContext(), if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bgColor)
        snack.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snack.show()
    }

    override fun onDestroyView() {
        // Ensure shimmer stops to avoid leaks
        binding.shimmerOverlay.stopShimmer()
        _binding = null
        super.onDestroyView()
    }
}