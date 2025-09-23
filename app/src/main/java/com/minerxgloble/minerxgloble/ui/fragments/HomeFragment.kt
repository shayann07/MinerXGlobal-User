package com.minerxgloble.minerxgloble.ui.fragments

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.AnnouncementImageAdapter
import com.minerxgloble.minerxgloble.adapters.DocumentsAdapter
import com.minerxgloble.minerxgloble.adapters.NetworkMiniAdapter
import com.minerxgloble.minerxgloble.adapters.StatGridAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentHomeBinding
import com.minerxgloble.minerxgloble.models.DocumentItem
import com.minerxgloble.minerxgloble.models.NetworkStat
import com.minerxgloble.minerxgloble.models.TeamLevel
import com.minerxgloble.minerxgloble.models.UserStatCard
import com.minerxgloble.minerxgloble.repos.AuthRepository
import com.minerxgloble.minerxgloble.repos.WalletRepo
import com.minerxgloble.minerxgloble.ui.MainActivity
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.viewModels.*
import com.minerxgloble.minerxgloble.viewModels.factory.AuthViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val walletVm: WalletViewModel by viewModels()
    private val teamVm: TeamLevelViewModel by viewModels()
    private val authVm: AuthViewModel by viewModels {
        AuthViewModelFactory(
            AuthRepository(
                FirebaseAuth.getInstance(),
                FirebaseFirestore.getInstance(),
                PrefService(requireContext())
            )
        )
    }
    private val networkVm: NetworkStatsViewModel by viewModels()

    private var announcementPagerJob: Job? = null
    private val pref by lazy { PrefService(requireContext()) }

    private val K_BALANCE        = "cache_balance"
    private val K_DIRECT_USERS   = "cache_direct_users"
    private val K_INDIRECT_USERS = "cache_indirect_users"
    private val K_TOTAL_BUSINESS = "cache_total_business"
    private val K_NET_MEMBERS    = "cache_net_members"
    private val K_NET_WITHDRAW   = "cache_net_withdraw"
    private val K_NET_INVEST     = "cache_net_invest"
    private val K_TOKENS         = "cache_tokens"

    private val K_TOKEN_RATE = "cache_token_rate"

    private lateinit var userId: String
    private var statsFirstRealShown = false
    private var netFirstRealShown = false



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure we start at top & only reveal after first pre-draw
        view.doOnPreDraw {
            // hard reset scroll (if you use NestedScrollView)
            view.findViewById<NestedScrollView?>(R.id.scrollContainer)?.scrollTo(0, 0)

            startPostponedEnterTransition()
            (requireActivity() as? MainActivity)?.onHomeFirstFrameReady()
        }
        setupDrawerTrigger(view)

        userId=pref.getUserId().toString()
        // greet + avatar
        pref.getString("name")?.takeIf { it.isNotBlank() }?.let { full ->
            binding.hiName.text = getString(R.string.hi_name, full.substringBefore(" "))
        }
        ProfileImageUtil.loadOrRefresh(requireContext(), pref.getUserId().orEmpty(), binding.avatar)

        // ----- HERO WALLET (note: go through include's binding) -----
        binding.walletHero.tvWalletTitle.text = "Wallet Balance"
        binding.walletHero.tvWalletTotalLabel.text = "Total USD"
        binding.walletHero.tvWalletAmount.text = "—"

        walletVm.wallet.observe(viewLifecycleOwner) { snap ->
            snap ?: return@observe
            val balance = snap.account.earnings.totalEarned
            binding.walletHero.tvWalletAmount.text = walletVm.money(balance)
        }

        // ----- QUICK ACTIONS -----
        binding.walletHero.btnDeposit.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_depositFragment)
        }
        binding.walletHero.btnWithdraw.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_withdrawFragment)
        }
        // Referral copy
        binding.walletHero.inputReferral.apply {
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


        // ----- STATS GRID (2×2) -----
        val statsAdapter = StatGridAdapter()
        binding.rvStatsGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvStatsGrid.adapter = statsAdapter

        val cachedUser = readCachedUserCards()
        if (cachedUser != null) {
            showStatsGridShimmer(false)
            statsAdapter.submit(cachedUser)
        } else {
            showStatsGridShimmer(true)
        }

        walletVm.wallet.observe(viewLifecycleOwner) { snap ->
            if (snap != null) updateStatsWithFx(statsAdapter, snap, teamVm.levels.value ?: emptyList())
        }
        teamVm.levels.observe(viewLifecycleOwner) { levels ->
            walletVm.wallet.value?.let { snap -> updateStatsWithFx(statsAdapter, snap, levels) }
        }
        teamVm.load()

        // ----- ANNOUNCEMENTS -----
        binding.notificationTv.isVisible = false
        binding.announcementSlider.isVisible = false
        showNotificationTitleShimmer(true)
        showAnnouncementShimmer(true)

        authVm.getAnnouncementImageUrls()
        authVm.announcementImageUrls.observe(viewLifecycleOwner) { urls ->
            val hasAny = !urls.isNullOrEmpty()
            if (hasAny) {
                showNotificationTitleShimmer(false)
                binding.notificationTv.isVisible = true

                val annAdapter = AnnouncementImageAdapter(urls!!)
                binding.announcementSlider.adapter = annAdapter
                binding.announcementSlider.setPageTransformer { page, position ->
                    val scale = 0.9f + (1 - kotlin.math.abs(position)) * 0.1f
                    page.scaleY = scale
                    page.scaleX = scale
                    page.translationX = -position * 20
                }

                showAnnouncementShimmer(false)
                binding.announcementSlider.isVisible = true

                announcementPagerJob?.cancel()
                announcementPagerJob = viewLifecycleOwner.lifecycleScope.launch {
                    while (isActive) {
                        delay(10_000)
                        val vp = binding.announcementSlider
                        val count = vp.adapter?.itemCount ?: 0
                        if (count > 1) vp.setCurrentItem((vp.currentItem + 1) % count, true)
                    }
                }
            } else {
                announcementPagerJob?.cancel()
                showAnnouncementShimmer(false)
                showNotificationTitleShimmer(false)
                binding.notificationTv.isVisible = false
                binding.announcementSlider.isVisible = false
            }
        }

        // ----- NETWORK MINI (3 cards in a row) -----
        val netAdapter = NetworkMiniAdapter()
        binding.rvNetworkMini.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvNetworkMini.adapter = netAdapter

        val cachedNet = readCachedNetworkStats()
        if (cachedNet != null) {
            showNetworkMiniShimmer(false)
            netAdapter.submit(cachedNet)
        } else {
            showNetworkMiniShimmer(true)
        }

        networkVm.stats.observe(viewLifecycleOwner) { list ->
            list ?: return@observe
            if (list.size >= 3) {
                pref.setString(K_NET_MEMBERS,  list[0].value)
                pref.setString(K_NET_WITHDRAW, list[1].value)
                pref.setString(K_NET_INVEST,   list[2].value)
            }
            if (!netFirstRealShown) { netFirstRealShown = true; showNetworkMiniShimmer(false) }
            netAdapter.submit(list)                  // still passes ONLY the 3 items
        }
        networkVm.startStatsListener()
        networkVm.load()

        networkVm.tokenRate.observe(viewLifecycleOwner) { rate ->
            rate ?: return@observe
            pref.setString(K_TOKEN_RATE, rate.toString())
            netAdapter.setTokenRate(rate)            // adapter merges it as a 4th row
        }
        // ADD these lines
        networkVm.loadTokenRate()
        networkVm.startTokenRateListener()

        // ----- DOCUMENTS -----
        val docsAdapter = DocumentsAdapter(emptyList()) { doc -> showDownloadDialog(doc) }
        binding.rvDocuments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDocuments.adapter = docsAdapter
        networkVm.docs.observe(viewLifecycleOwner) { docs -> docs?.let { docsAdapter.submit(it) } }
        networkVm.load()
    }

    // ===== Shimmers =====
    private fun showStatsGridShimmer(show: Boolean) {
        binding.rvStatsGrid.isVisible = !show
        binding.shimmerStatsGrid.isVisible = show
        if (show) binding.shimmerStatsGrid.startShimmer() else binding.shimmerStatsGrid.stopShimmer()
    }
    private fun showNetworkMiniShimmer(show: Boolean) {
        binding.rvNetworkMini.isVisible = !show
        binding.shimmerNetworkMini.isVisible = show
        if (show) binding.shimmerNetworkMini.startShimmer() else binding.shimmerNetworkMini.stopShimmer()
    }
    private fun showAnnouncementShimmer(show: Boolean) {
        binding.announcementSlider.isVisible = !show
        binding.shimmerAnnouncements.isVisible = show
        if (show) binding.shimmerAnnouncements.startShimmer() else binding.shimmerAnnouncements.stopShimmer()
    }
    private fun showNotificationTitleShimmer(show: Boolean) {
        val shimmer = binding.shimmerNotificationTitle
        shimmer.isVisible = show
        binding.notificationTv.isVisible = !show
        if (show) shimmer.startShimmer() else shimmer.stopShimmer()
    }

    // ===== Stats mapping → grid =====
    private fun updateStatsWithFx(
        adapter: StatGridAdapter,
        wallet: WalletRepo.WalletSnapshot,
        levels: List<TeamLevel>
    ) {
        if (levels.isEmpty()) return

        val balance = wallet.account.earnings.totalEarned
        val tokens = walletVm.nestedDouble(wallet.raw, "earnings.tokens").toInt()
        val directUsers = levels.firstOrNull()?.totalUsers ?: 0
        val indirectUsers = levels.drop(1).sumOf { it.totalUsers }
        val directBusiness = levels.firstOrNull()?.totalDeposit ?: 0.0
        val indirectBusiness = levels.drop(1).sumOf { it.totalDeposit }
        val totalSize =directUsers+indirectUsers
        val totalBusiness = directBusiness + indirectBusiness

        val cards = listOf(
            UserStatCard("Balance", walletVm.money(balance)),
            UserStatCard("MXGN Tokens", tokens.toString()),
            UserStatCard("Team Size", totalSize.toString()),
            UserStatCard("Team Invested", walletVm.money(totalBusiness))
        )

        pref.setString(K_BALANCE, walletVm.money(balance))
        pref.setInt(K_DIRECT_USERS, directUsers)
        pref.setInt(K_INDIRECT_USERS, indirectUsers)
        pref.setString(K_TOTAL_BUSINESS, walletVm.money(totalBusiness))
        pref.setInt(K_TOKENS, tokens)

        if (!statsFirstRealShown) { statsFirstRealShown = true; showStatsGridShimmer(false) }
        adapter.submit(cards)
    }

    private fun readCachedUserCards(): List<UserStatCard>? {
        val bal = pref.getString(K_BALANCE)
        val tokens = pref.getInt(K_TOKENS, -1)
        val d = pref.getInt(K_DIRECT_USERS, -1)
        val i = pref.getInt(K_INDIRECT_USERS, -1)
        val totalSize=d+i
        val biz = pref.getString(K_TOTAL_BUSINESS)
        return if (!bal.isNullOrBlank() && tokens >= 0 && d >= 0 && i >= 0 && !biz.isNullOrBlank()) {

            listOf(
                UserStatCard("Balance", bal),
                UserStatCard("Tokens", tokens.toString()),
                UserStatCard("Team Size", totalSize.toString()),
                UserStatCard("Team Invested", biz)
            )
        } else null
    }

    private fun readCachedNetworkStats(): List<NetworkStat>? {
        val m  = pref.getString(K_NET_MEMBERS)
        val w  = pref.getString(K_NET_WITHDRAW)
        val v  = pref.getString(K_NET_INVEST)
        // still read TR here if you want, but don't use the adapter in this function
        // val tr = pref.getString(K_TOKEN_RATE)

        return if (!m.isNullOrBlank() && !w.isNullOrBlank() && !v.isNullOrBlank()) {
            listOf(
                NetworkStat(desc = "All Members", value = m),
                NetworkStat(desc = "Withdrawn",   value = w),
                NetworkStat(desc = "Invested",    value = v)
            )
        } else null
    }


    @SuppressLint("ShowToast")
    private fun showDownloadDialog(doc: DocumentItem) {
        val act = requireActivity()
        val appCtx = act.applicationContext
        val dialogView = layoutInflater.inflate(R.layout.dialoge_download_xml, null)

        val titleTv  = dialogView.findViewById<TextView>(R.id.dlTitle)
        val subTv    = dialogView.findViewById<TextView>(R.id.dlSubtitle)
        val btnDownload = dialogView.findViewById<MaterialButton>(R.id.btnDownload)

        titleTv.text = "Download File"
        subTv.text   = (doc.title ?: "").ifBlank { "Document" }

        val dialog = MaterialAlertDialogBuilder(act)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnDownload.setOnClickListener {
            // 1) Validate URL
            val url = doc.fileUrl?.trim().orEmpty()
            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                // Dismiss and inform user
                dialog.dismiss()
                try {
                   showSnackbar("Invalid download link", true)
                } catch (_: Exception) {
                    showSnackbar("Invalid download link", true)
                }
                return@setOnClickListener
            }

            // 2) Safe filename
            val baseName = (doc.title ?: "").ifBlank { "document" }
            val safeName = baseName.replace(Regex("[^\\w\\s.-]"), "_")
                .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }

            // 3) Enqueue download
            val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            try {
                dm.enqueue(
                    DownloadManager.Request(Uri.parse(url))
                        .setTitle(baseName)
                        .setDescription((doc.description ?: "").ifBlank { "Downloading…" })
                        .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        .setDestinationInExternalFilesDir(
                            appCtx, Environment.DIRECTORY_DOWNLOADS, safeName
                        )
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                )

                // 4) Dismiss dialog immediately & show Snackbar
                dialog.dismiss()
                val rootForSnack = runCatching { requireView() }.getOrNull()
                if (rootForSnack != null) {
                   showSnackbar("Downloading…")
                } else {
                    // Fallback if fragment view not attached
                    showSnackbar("Downloading…")
                }

            } catch (e: Exception) {
                dialog.dismiss()
                val rootForSnack = runCatching { requireView() }.getOrNull()
                if (rootForSnack != null) {
                    showSnackbar("Failed to start download", true)
                } else {
                    Toast.makeText(act, "Failed to start download", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
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
    override fun onPause() {
        super.onPause()
        announcementPagerJob?.cancel()
        binding.shimmerStatsGrid.stopShimmer()
        binding.shimmerNetworkMini.stopShimmer()
        binding.shimmerAnnouncements.stopShimmer()
        binding.shimmerNotificationTitle.stopShimmer()
        networkVm.stopStatsListener()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
        networkVm.stopStatsListener()
    }
}
