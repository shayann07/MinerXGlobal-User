package com.minerxgloble.minerxgloble.ui.fragments

import android.animation.ValueAnimator
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.adapters.AnnouncementImageAdapter
import com.minerxgloble.minerxgloble.adapters.DocumentsAdapter
import com.minerxgloble.minerxgloble.adapters.NetworkStatsAdapter
import com.minerxgloble.minerxgloble.adapters.UserStatsAdapter
import com.minerxgloble.minerxgloble.databinding.FragmentHomeBinding
import com.minerxgloble.minerxgloble.models.DocumentItem
import com.minerxgloble.minerxgloble.models.TeamLevel
import com.minerxgloble.minerxgloble.models.UserStatCard
import com.minerxgloble.minerxgloble.repos.AuthRepository
import com.minerxgloble.minerxgloble.repos.WalletRepo
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.viewModels.AuthViewModel
import com.minerxgloble.minerxgloble.viewModels.NetworkStatsViewModel
import com.minerxgloble.minerxgloble.viewModels.TeamLevelViewModel
import com.minerxgloble.minerxgloble.viewModels.WalletViewModel
import com.minerxgloble.minerxgloble.viewModels.factory.AuthViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

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

    private var statsPagerJob: Job? = null
    private var announcementPagerJob: Job? = null
    private var networkPagerJob: Job? = null

    private val pref by lazy { PrefService(requireContext()) }

    // ---- cache keys
    private val K_BALANCE        = "cache_balance"
    private val K_DIRECT_USERS   = "cache_direct_users"
    private val K_INDIRECT_USERS = "cache_indirect_users"
    private val K_TOTAL_BUSINESS = "cache_total_business"

    private val K_NET_MEMBERS    = "cache_net_members"
    private val K_NET_WITHDRAW   = "cache_net_withdraw"
    private val K_NET_INVEST     = "cache_net_invest"

    private val K_TOKENS         = "cache_tokens"

    // flags to ensure we stop shimmer exactly once on first real load
    private var statsFirstRealShown = false
    private var netFirstRealShown = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        // greet user
        pref.getString("name")?.takeIf { it.isNotBlank() }?.let { full ->
            binding.hiName.text = getString(R.string.hi_name, full.substringBefore(" "))
        }

        ProfileImageUtil.loadOrRefresh(
            requireContext(),
            uid = pref.getUserId().orEmpty(),
            binding.avatar
        )

        // ================== USER STATS SLIDER ==================
        val statsAdapter = UserStatsAdapter(emptyList())
        binding.userStatsPager.adapter = statsAdapter

        val cachedUserCards = readCachedUserCards()
        if (cachedUserCards != null) {
            showUserStatsShimmer(false)
            statsAdapter.submitList(cachedUserCards)
        } else {
            showUserStatsShimmer(true)
        }

        walletVm.wallet.observe(viewLifecycleOwner) { snap ->
            if (snap != null) updateStatsWithFx(statsAdapter, snap, teamVm.levels.value ?: emptyList())
        }
        teamVm.levels.observe(viewLifecycleOwner) { levels ->
            walletVm.wallet.value?.let { snap -> updateStatsWithFx(statsAdapter, snap, levels) }
        }
        teamVm.load()

        statsPagerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                val vp = binding.userStatsPager
                val next = (vp.currentItem + 1) % (vp.adapter?.itemCount ?: 1)
                vp.setCurrentItem(next, true)
            }
        }

        // ================== ANNOUNCEMENTS (title + slider shimmer) ==================
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
                    val scale = 0.85f + (1 - abs(position)) * 0.15f
                    page.scaleY = scale
                    page.scaleX = scale
                    page.elevation = if (position == 0f) 8f else 0f
                }

                showAnnouncementShimmer(false)
                binding.announcementSlider.isVisible = true

                announcementPagerJob?.cancel()
                announcementPagerJob = viewLifecycleOwner.lifecycleScope.launch {
                    while (isActive) {
                        delay(10_000)
                        val vp = binding.announcementSlider
                        val count = vp.adapter?.itemCount ?: 0
                        if (count > 1) {
                            val next = (vp.currentItem + 1) % count
                            vp.setCurrentItem(next, true)
                        }
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

        // ================== NETWORK STATS SLIDER ==================
        val netAdapter = NetworkStatsAdapter(emptyList())
        binding.networkStatsPager.adapter = netAdapter

        binding.networkStatsPager.setPageTransformer { page, position ->
            val scale = 0.85f + (1 - abs(position)) * 0.15f
            page.scaleY = scale
            page.scaleX = scale
            page.elevation = if (position == 0f) 8f else 0f
        }

        val cachedNet = readCachedNetworkStats()
        if (cachedNet != null) {
            showNetworkStatsShimmer(false)
            netAdapter.submitList(cachedNet)
        } else {
            showNetworkStatsShimmer(true)
        }

        networkVm.stats.observe(viewLifecycleOwner) { list ->
            list ?: return@observe

            if (list.size >= 3) {
                pref.setString(K_NET_MEMBERS, list[0].value)
                pref.setString(K_NET_WITHDRAW, list[1].value)
                pref.setString(K_NET_INVEST, list[2].value)
            }

            if (!netFirstRealShown) {
                netFirstRealShown = true
                showNetworkStatsShimmer(false)
            }
            netAdapter.submitList(list)
        }
        networkVm.startStatsListener()
        networkVm.loadStats()

        networkPagerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                val vp = binding.networkStatsPager
                val count = vp.adapter?.itemCount ?: 0
                if (count > 1) {
                    val next = (vp.currentItem + 1) % count
                    vp.setCurrentItem(next, true)
                }
            }
        }

        // ================== DOCUMENTS LIST ==================
        val docsAdapter = DocumentsAdapter(emptyList()) { doc -> showDownloadDialog(doc) }
        binding.rvDocuments.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvDocuments.adapter = docsAdapter

        networkVm.docs.observe(viewLifecycleOwner) { docs -> docs?.let { docsAdapter.submit(it) } }
        networkVm.load()
    }

    // ----------- SHIMMER TOGGLES -----------

    private fun showUserStatsShimmer(show: Boolean) {
        binding.userStatsPager.isVisible = !show
        binding.shimmerUserStats.isVisible = show
        if (show) binding.shimmerUserStats.startShimmer() else binding.shimmerUserStats.stopShimmer()
    }

    private fun showNetworkStatsShimmer(show: Boolean) {
        binding.networkStatsPager.isVisible = !show
        binding.shimmerNetworkStats.isVisible = show
        if (show) binding.shimmerNetworkStats.startShimmer() else binding.shimmerNetworkStats.stopShimmer()
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

    // ----------- HELPERS -----------

    private fun updateStatsWithFx(
        adapter: UserStatsAdapter,
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

        // cache for next launch
        pref.setString(K_BALANCE, walletVm.money(balance))
        pref.setInt(K_DIRECT_USERS, directUsers)
        pref.setInt(K_INDIRECT_USERS, indirectUsers)
        pref.setString(K_TOTAL_BUSINESS, walletVm.money(totalBusiness))
        pref.setInt(K_TOKENS, tokens)

        if (!statsFirstRealShown) {
            statsFirstRealShown = true
            showUserStatsShimmer(false)
        }
        adapter.submitList(cards)
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

    private fun readCachedNetworkStats(): List<com.minerxgloble.minerxgloble.models.NetworkStat>? {
        val m = pref.getString(K_NET_MEMBERS)
        val w = pref.getString(K_NET_WITHDRAW)
        val inv = pref.getString(K_NET_INVEST)
        return if (!m.isNullOrBlank() && !w.isNullOrBlank() && !inv.isNullOrBlank()) {
            listOf(
                com.minerxgloble.minerxgloble.models.NetworkStat(m, "All Members in Network"),
                com.minerxgloble.minerxgloble.models.NetworkStat(w, "Total Withdrawal"),
                com.minerxgloble.minerxgloble.models.NetworkStat(inv, "Total Investment")
            )
        } else null
    }

    // optional: number "count up" helpers (best used inside ViewHolder bindings)
    private fun TextView.countToInt(
        from: Int,
        to: Int,
        duration: Long = 600,
        format: (Int) -> String = { it.toString() }
    ) {
        if (from == to) { text = format(to); return }
        val animator = ValueAnimator.ofInt(from, to).setDuration(duration)
        animator.addUpdateListener { text = format(it.animatedValue as Int) }
        animator.start()
    }

    private fun TextView.countToMoney(
        from: Double,
        to: Double,
        duration: Long = 700,
        format: (Double) -> String
    ) {
        if (from == to) { text = format(to); return }
        val animator = ValueAnimator.ofFloat(from.toFloat(), to.toFloat()).setDuration(duration)
        animator.addUpdateListener { text = format((it.animatedValue as Float).toDouble()) }
        animator.start()
    }

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
                        .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        .setDestinationInExternalFilesDir(
                            ctx, Environment.DIRECTORY_DOWNLOADS, safeName
                        )
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

        dialog.setOnDismissListener {
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
        }

        dialog.show()
    }

    override fun onPause() {
        super.onPause()
        statsPagerJob?.cancel()
        announcementPagerJob?.cancel()
        networkPagerJob?.cancel()
        binding.shimmerUserStats.stopShimmer()
        binding.shimmerNetworkStats.stopShimmer()
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
