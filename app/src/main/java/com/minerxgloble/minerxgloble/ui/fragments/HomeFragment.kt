package com.minerxgloble.minerxgloble.ui.fragments

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
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

    private var statsFirstRealShown = false
    private var netFirstRealShown = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        // greet + avatar
        pref.getString("name")?.takeIf { it.isNotBlank() }?.let { full ->
            binding.hiName.text = getString(R.string.hi_name, full.substringBefore(" "))
        }
        ProfileImageUtil.loadOrRefresh(requireContext(), pref.getUserId().orEmpty(), binding.avatar)

        // ----- HERO WALLET (note: go through include's binding) -----
        binding.walletHero.tvWalletTitle.text = "MinerX Deposit Wallet"
        binding.walletHero.tvWalletTotalLabel.text = "Total USD"
        binding.walletHero.tvWalletAmount.text = "—"

        walletVm.wallet.observe(viewLifecycleOwner) { snap ->
            snap ?: return@observe
            val balance = snap.account.earnings.totalEarned
            binding.walletHero.tvWalletAmount.text = walletVm.money(balance)
        }

        // ----- QUICK ACTIONS -----
        binding.btnDeposit.setOnClickListener { /* TODO */ }
        binding.btnWithdraw.setOnClickListener { /* TODO */ }
        binding.btnInvite.setOnClickListener  { /* TODO */ }

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
        binding.rvNetworkMini.layoutManager = GridLayoutManager(requireContext(), 3)
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
                pref.setString(K_NET_MEMBERS, list[0].value)
                pref.setString(K_NET_WITHDRAW, list[1].value)
                pref.setString(K_NET_INVEST, list[2].value)
            }
            if (!netFirstRealShown) { netFirstRealShown = true; showNetworkMiniShimmer(false) }
            netAdapter.submit(list)
        }
        networkVm.startStatsListener()
        networkVm.load()

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
        val totalBusiness = directBusiness + indirectBusiness

        val cards = listOf(
            UserStatCard("Balance", walletVm.money(balance)),
            UserStatCard("MXGN Tokens", tokens.toString()),
            UserStatCard("Team Size", "Direct: $directUsers | Indirect: $indirectUsers"),
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
        val biz = pref.getString(K_TOTAL_BUSINESS)
        return if (!bal.isNullOrBlank() && tokens >= 0 && d >= 0 && i >= 0 && !biz.isNullOrBlank()) {
            listOf(
                UserStatCard("Balance", bal),
                UserStatCard("Tokens", tokens.toString()),
                UserStatCard("Team Size", "Direct: $d | Indirect: $i"),
                UserStatCard("Team Invested", biz)
            )
        } else null
    }

    private fun readCachedNetworkStats(): List<NetworkStat>? {
        val m = pref.getString(K_NET_MEMBERS)
        val w = pref.getString(K_NET_WITHDRAW)
        val v = pref.getString(K_NET_INVEST)
        return if (!m.isNullOrBlank() && !w.isNullOrBlank() && !v.isNullOrBlank()) {
            listOf(
                NetworkStat(desc = "All Members", value = m),
                NetworkStat(desc = "Withdrawn",  value = w),
                NetworkStat(desc = "Invested",   value = v)
            )
        } else null
    }

    // ===== Download dialog (unchanged) =====
    private fun showDownloadDialog(doc: DocumentItem) {
        val ctx = requireContext()
        val view = layoutInflater.inflate(R.layout.dialoge_download_xml, null)

        val titleTv  = view.findViewById<TextView>(R.id.dlTitle)
        val subTv    = view.findViewById<TextView>(R.id.dlSubtitle)
        val progress = view.findViewById<CircularProgressIndicator>(R.id.dlCircle)
        val statusTv = view.findViewById<TextView>(R.id.dlLabel)
        val btnDownload = view.findViewById<MaterialButton>(R.id.btnDownload)
        val progressContainer = view.findViewById<View>(R.id.dlProgressContainer)

        titleTv.text = "Download File"
        subTv.text   = doc.title.ifBlank { "Document" }

        val dialog = MaterialAlertDialogBuilder(ctx).setView(view).setCancelable(true).create()

        var downloadId = -1L
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id == downloadId && dialog.isShowing) {
                    statusTv.text = "Downloaded"
                    progress.isIndeterminate = false
                    progress.progress = 100
                    view.postDelayed({ dialog.dismiss() }, 600)
                    try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
        }

        btnDownload.setOnClickListener {
            btnDownload.isEnabled = false
            progressContainer.isVisible = true
            statusTv.text = "Downloading…"

            val safeName = (doc.title.ifBlank { "document" })
                .replace(Regex("[^\\w\\s.-]"), "_")
                .plus(if (doc.title.endsWith(".pdf", true)) "" else ".pdf")

            downloadId = (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                .enqueue(
                    DownloadManager.Request(Uri.parse(doc.fileUrl))
                        .setTitle(doc.title.ifBlank { safeName })
                        .setDescription(doc.description.ifBlank { "Downloading…" })
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, safeName)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                )

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ContextCompat.RECEIVER_EXPORTED else 0
            ContextCompat.registerReceiver(
                ctx, receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                flags
            )
        }

        dialog.setOnDismissListener { try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {} }
        dialog.show()
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
