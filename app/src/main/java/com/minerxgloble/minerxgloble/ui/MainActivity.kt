package com.minerxgloble.minerxgloble.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.gms.security.ProviderInstaller
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.ActivityMainBinding
import com.minerxgloble.minerxgloble.repos.AuthRepository
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.utils.ProfileImageUtil
import com.minerxgloble.minerxgloble.utils.RemoteUpdateManager
import com.minerxgloble.minerxgloble.viewModels.ProfileViewModel
import com.minerxgloble.minerxgloble.viewModels.ProfileViewModelFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    internal lateinit var viewModel: ProfileViewModel
    private lateinit var updater: RemoteUpdateManager

    private val auth by lazy { FirebaseAuth.getInstance() }
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var userDocListener: ListenerRegistration? = null

    private val HOME_DEST = R.id.homeFragment

    // MainActivity.kt
    private var deferBarUntilHomeReady = false

    private lateinit var drawerItemViews: Map<Int, View>
    private lateinit var drawerRoutes: Map<Int, Int> // keep accessible in multiple places

    // 🔒 Grace window so Firebase can restore session before we judge null currentUser
    private var authGraceOver = false
    private val authGraceMs = 1500L

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showSnackbar("Notification permission denied", isError = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())

        val authRepo = AuthRepository(
            FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(), PrefService(this)
        )
        val authUid =
            FirebaseAuth.getInstance().currentUser?.uid.orEmpty()   // Firebase Auth UID (doc id)
        val mxg = PrefService(this).getUserId()
            .orEmpty()                     // MXG-xxxx (legacy/fallback)



        viewModel = ViewModelProvider(
            this, ProfileViewModelFactory(authRepo, authUid)
        )[ProfileViewModel::class.java]

// Prime the VM once (attaches listener + warms cache)
        viewModel.setAuthUid(authUid, mxg)
        viewModel.ensureProfileFresh() // optional initial refresh


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                ProviderInstaller.installIfNeeded(applicationContext)
            } catch (_: Exception) {
            }
        }

        // Handle incoming “?ref=…” param
        intent?.data?.getQueryParameter("ref")?.let { referrerId ->
            PrefService(this).saveReferralFromLink(referrerId)
        }
        updater = RemoteUpdateManager(this).also { it.clearFlagsIfUpdated() }



        // ⬇️ Show cached avatar immediately and refresh cache on app open
        val uidForAvatar = PrefService(this).getUserId().orEmpty()
        ProfileImageUtil.loadOrRefresh(
            this, uidForAvatar, binding.customDrawerHeader.drawerImageView
        )

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        navController.graph = navController.navInflater.inflate(R.navigation.nav_graph)

        // -------- Bottom Nav: Home clears the entire stack; others navigate normally (no transitions)
        binding.bottomNavBar.setOnItemSelectedListener { item ->
            if (item.itemId == HOME_DEST) {
                goHomeClearingAll() // Home becomes the only destination in the back stack
            } else {
                if (navController.currentDestination?.id != item.itemId) {
                    navController.navigate(item.itemId, null)
                }
            }
            true
        }

        binding.fabScan?.setOnClickListener {
            if (navController.currentDestination?.id != R.id.stackFragment) {
                navController.navigate(R.id.stackFragment, null)
            }
        }

        drawerRoutes = mapOf(
            R.id.menuHome to R.id.homeFragment,
            R.id.menuProfile to R.id.profileFragment,
            R.id.menuRank to R.id.rankFragment2,
            R.id.menuInvestmentWallet to R.id.walletFragment,
            R.id.menuEarningsWallet to R.id.earningsWalletFragment,
            R.id.menuTeam to R.id.teamLevelsFragment,
            R.id.menuSalary to R.id.salaryFragment,
            R.id.menuTransactions to R.id.salaryHistoryFragment,
            R.id.menuLuckyDraw to R.id.luckyDrawFragment,
            R.id.menuSupport to R.id.chatFragment,
            R.id.menuFaqs to R.id.faqsFragment
        )

// Cache the actual row views and give them the selector background
        drawerItemViews = drawerRoutes.keys.associateWith { menuId ->
            binding.navigationView.findViewById<View>(menuId).apply {
                background =
                    ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_drawer_item_selector)
                isClickable = true
            }
        }


        // -------- Drawer: Home clears the entire stack; others navigate normally
        drawerRoutes.forEach { (menuId, destId) ->
            binding.navigationView.findViewById<View>(menuId)?.setOnClickListener {
                binding.drawerLayout.closeDrawer(GravityCompat.START)

                // update highlight immediately
                setDrawerChecked(menuId)

                // avoid redundant nav
                val navHost =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val navController = navHost.navController

                if (navController.currentDestination?.id == destId) return@setOnClickListener

                if (destId == HOME_DEST) {
                    goHomeClearingAll()
                } else {
                    navController.navigate(destId, null)
                }
            }
        }


        binding.navigationView.findViewById<View>(R.id.logout)?.setOnClickListener {
            showLogoutConfirmation()
        }

        val barDestinations = setOf(
            R.id.homeFragment,
            R.id.walletFragment,
            R.id.plansFragment,
            R.id.teamLevelsFragment,
            R.id.profileFragment
        )
        val hideNavScreens = setOf(
            R.id.loginFragment,
            R.id.signupFragment,
        )
        navController.addOnDestinationChangedListener { _, dest, _ ->
            val barDestinations = setOf(
                R.id.homeFragment, R.id.walletFragment, R.id.plansFragment,
                R.id.teamLevelsFragment, R.id.profileFragment
            )
            val hideNavScreens = setOf(R.id.loginFragment, R.id.signupFragment)

            val shouldShowBar = (dest.id in barDestinations) && (dest.id !in hideNavScreens) && !deferBarUntilHomeReady
            binding.bottomNavBar.isVisible = shouldShowBar
            binding.fabScan?.isVisible = shouldShowBar

            binding.bottomNavBar.menu.findItem(dest.id)?.isChecked = true
            val activeMenuId = drawerRoutes.entries.firstOrNull { it.value == dest.id }?.key
            setDrawerChecked(activeMenuId)
        }


        setupDrawer(navController, barDestinations)

        // ⬇️ Name & email in drawer
        viewModel.profileData.observe(this) { bindDrawerProfile(it) }
        viewModel.loadProfile()

        // -------- Back: if on a bottom-tab (not Home) → go Home clearing entire stack; else default
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navHost =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val navController = navHost.navController

                // 1) If drawer is open (even partially), close it and STOP.
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START) ||
                    binding.drawerLayout.isDrawerVisible(GravityCompat.START)
                ) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }

               /* // 2) If on a bottom-tab (not Home), go Home clearing stack.
                val barDestinations = setOf(
                    R.id.homeFragment,
                    R.id.walletFragment,
                    R.id.plansFragment,
                    R.id.teamLevelsFragment,
                    R.id.profileFragment
                )
                val cur = navController.currentDestination?.id
                if (cur != null && cur != HOME_DEST && cur in barDestinations) {
                    goHomeClearingAll()
                    return
                }*/

                // 3) Otherwise try to pop; if nothing to pop, finish the Activity.
                if (!navController.popBackStack()) {
                    finish()
                }
            }
        })

    }
        private fun setupDrawer(
        navController: androidx.navigation.NavController,
        barDestinations: Set<Int>
    ) {
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (binding.bottomNavBar.isVisible) binding.bottomNavBar.alpha = 1f - slideOffset
                binding.navHostFragment.translationX = drawerView.width * slideOffset

                // Block touches while the drawer is at least partially open
                setContentTouchBlocked(slideOffset > 0f)
            }

            override fun onDrawerOpened(drawerView: View) {
                binding.navHostFragment.animate()
                    .translationX(drawerView.width.toFloat())
                    .setDuration(200)
                    .start()

                val currentAuthUid = FirebaseAuth.getInstance().currentUser?.uid
                val mxg = PrefService(this@MainActivity).getUserId()

                if (currentAuthUid.isNullOrBlank()) {
                    binding.customDrawerHeader.userNameTextView.text = ""
                    binding.customDrawerHeader.userEmailTextView.text = ""
                    binding.customDrawerHeader.drawerImageView.setImageResource(R.drawable.ic_profile)
                    return
                }
                val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val destId = navHost.navController.currentDestination?.id
                val activeMenuId = drawerRoutes.entries.firstOrNull { it.value == destId }?.key
                setDrawerChecked(activeMenuId)

                // keep VM in sync (in case account switched without Activity recreation)
                viewModel.setAuthUid(currentAuthUid, mxg, attachListener = true)

                // Refresh only if stale (no blocking fetch here)
                viewModel.ensureProfileFresh(maxAgeMs = 5_000)

                // Avatar: cached now + background refresh
                ProfileImageUtil.loadOrRefresh(
                    this@MainActivity,
                    mxg.orEmpty(), // if your avatar key is MXG; if you switched to authUid, pass that instead
                    binding.customDrawerHeader.drawerImageView
                )
            }

            override fun onDrawerClosed(drawerView: View) {
                binding.bottomNavBar.apply {
                    alpha = 1f
                    isVisible = navController.currentDestination?.id in barDestinations
                }
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })
    }

    /** ✅ Real-time guard aligned to schema: compare users/{MXG-xxxx}.authUid to Firebase UID. */
    private fun attachUserDocGuard() {
        val uid    = auth.currentUser?.uid ?: return
        val prefs  = PrefService(this)

        userDocListener?.remove()
        userDocListener = FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener

                /* 🔄  Skip guard until the user has finished first VERIFIED login */
                if (!prefs.checkLogin() || !authGraceOver) return@addSnapshotListener

                if (snap == null || !snap.exists()) {
                    forceLogoutWithReason("Your account was logged out. Please log in again.")
                } else if (snap.getBoolean("isBlocked") == true) {
                    forceLogoutWithReason("Your account is blocked. Please contact support.")
                }
            }
    }

    fun deferBottomBarForNextHome() {
        deferBarUntilHomeReady = true
        binding.bottomNavBar.isVisible = false
        binding.fabScan?.isVisible = false
    }

    fun onHomeFirstFrameReady() {
        if (deferBarUntilHomeReady) {
            deferBarUntilHomeReady = false
            binding.bottomNavBar.isVisible = true
            binding.fabScan?.isVisible = true
        }
    }

    private fun detachUserDocGuard() {
        userDocListener?.remove()
        userDocListener = null
    }


    /** ✅ Hard verify: only sign out for disabled/not-found. Network issues won’t boot user. */
    private fun hardVerifyAuth() {
        val prefs = PrefService(this)
        val user = auth.currentUser
        if (!prefs.checkLogin() || user == null || !user.isEmailVerified) return

        lifecycleScope.launch {
            try {
                user.reload().await()
            } catch (e: Exception) {
                val code = (e as? FirebaseAuthInvalidUserException)?.errorCode
                if (code == "ERROR_USER_NOT_FOUND") {
                    forceLogoutWithReason("Your account was removed. Please sign up again.")
                } else if (code == "ERROR_USER_DISABLED") {
                    forceLogoutWithReason("Your account is disabled. Please contact support.")
                } else {
                    // ignore
                }
            }
        }
    }

    private fun forceLogoutWithReason(message: String) {
        detachUserDocGuard()
        try { auth.signOut() } catch (_: Exception) {}
        PrefService.clearAllPrefs(this)

        Log.e("MainActivity", message)
        showSnackbar(message)

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        navController.navigate(
            R.id.loginFragment,
            null,
            NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, true)
                .setLaunchSingleTop(true)
                .build()
        )
    }

    override fun onStart() {
        super.onStart()
        authGraceOver = false
        Handler(Looper.getMainLooper()).postDelayed(authGraceMs) { authGraceOver = true }

        if (authListener == null) {
            authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    attachUserDocGuard()
                } else {
                    if (authGraceOver && PrefService(this).checkLogin()) {
                        forceLogoutWithReason("Your session expired. Please sign in again.")
                    }
                    detachUserDocGuard()
                }
            }
        }
        authListener?.let { auth.addAuthStateListener(it) }
    }

    override fun onStop() {
        super.onStop()
        authListener?.let { auth.removeAuthStateListener(it) }
    }

    override fun onDestroy() {
        detachUserDocGuard()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updater.checkForUpdate()
        hardVerifyAuth()


        val currentAuthUid = FirebaseAuth.getInstance().currentUser?.uid
        val mxg = PrefService(this).getUserId()
        viewModel.setAuthUid(currentAuthUid, mxg, attachListener = true)
    }
    private fun setDrawerChecked(menuId: Int?) {
        drawerItemViews.values.forEach { it.isActivated = false }
        menuId?.let { id -> drawerItemViews[id]?.isActivated = true }
    }


    private fun showLogoutConfirmation() {
        val dlg = AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ ->
                detachUserDocGuard()
                try {
                    auth.signOut()
                    this.viewModelStore.clear()
                } catch (_: Exception) {}

                // 1) Hard-clear shared prefs synchronously
                PrefService.clearAllPrefs(this)

                // 2) Immediately reset drawer UI so nothing stale appears
                binding.customDrawerHeader.userNameTextView.text = ""
                binding.customDrawerHeader.userEmailTextView.text = ""
                binding.customDrawerHeader.drawerImageView.setImageResource(R.drawable.ic_profile)

                // 3) Clear any cached avatar (prefs url + Glide memory/disk)
                ProfileImageUtil.clearAllProfileImageCache(
                    this,
                    binding.customDrawerHeader.drawerImageView
                )

                // 4) Navigate to login
                val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val navController = navHost.navController
                val opts = NavOptions.Builder()
                    .setPopUpTo(navController.graph.startDestinationId, true)
                    .build()
                navController.navigate(R.id.loginFragment, null, opts)

                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }

            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
    }




    /** Drawer name + email text */
    private fun bindDrawerProfile(profile: Map<String, Any?>?) {
        if (profile == null) {
            binding.customDrawerHeader.userNameTextView.text = ""
            binding.customDrawerHeader.userEmailTextView.text = ""
            binding.customDrawerHeader.drawerImageView.setImageResource(R.drawable.ic_profile)
            return
        }
        val fName= profile["name"]?.toString().orEmpty()
        val lName= profile["lastName"]?.toString().orEmpty()
        val fullName = "$fName $lName"
        binding.customDrawerHeader.userNameTextView.text =fullName
        binding.customDrawerHeader.userEmailTextView.text =
            profile["email"]?.toString().orEmpty()
    }

    fun openDrawer() = binding.drawerLayout.openDrawer(GravityCompat.START)
    private fun showSnackbar(message: String, isError: Boolean = false) {
        val bgColor = ContextCompat.getColor(
            this, if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        val textColor = ContextCompat.getColor(this, R.color.white)

        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)

        // Anchor above FAB if visible, else above BottomNavigationView (so it doesn't hide under it)
        val anchor: View? = when {
            binding.fabScan?.isShown == true -> binding.fabScan
            binding.bottomNavBar.isShown      -> binding.bottomNavBar
            else                              -> null
        }
        anchor?.let { snackbar.setAnchorView(it) }

        snackbar.setBackgroundTint(bgColor)
        snackbar.setTextColor(textColor)
        snackbar.show()
    }
    // Add this helper in MainActivity (e.g., under other private functions)
    private fun setContentTouchBlocked(block: Boolean) {
        val content = binding.navHostFragment
        if (block) {
            // Consume all touches while drawer is open/sliding
            content.setOnTouchListener { _, _ -> true }
            content.isClickable = true      // ensure it can receive the touch to consume
            content.isFocusable = false
        } else {
            // Restore normal interaction
            content.setOnTouchListener(null)
            content.isClickable = true
        }
    }


    /** 🔁 Helper: clear the entire back stack and land on Home */
    private fun goHomeClearingAll() {
        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        val opts = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(navController.graph.id, /* inclusive = */ true) // ← clears entire stack
            .build()
        navController.navigate(HOME_DEST, null, opts)
    }
    // ─── 2. helper that tries the app first, then falls back to browser ───
    private fun openLink(packageName: String, appUri: String, webUri: String = appUri) {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(appUri)).setPackage(packageName)
        try {
            startActivity(appIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUri)))
        }
    }

    // ─── 3. one-stop wiring for the three buttons ─────────────────
    private fun setupSocialLinks() {
        /* WhatsApp channel */
        binding.navigationView.findViewById<View>(R.id.btnWhatsApp).setOnClickListener {
            openLink(
                packageName = "com.whatsapp",
                /* If the WhatsApp app is installed this link opens the channel directly;
                   otherwise the browser page loads. */
                appUri = "https://whatsapp.com/channel/0029Vb5ZfdOKQuJNBXCF890H"
            )
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        /* Telegram channel */
        binding.navigationView.findViewById<View>(R.id.btnTelegram).setOnClickListener {
            openLink(
                packageName = "org.telegram.messenger",
                appUri = "tg://resolve?domain=bitbloomuk",
                webUri = "https://t.me/bitbloomuk"
            )
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

}
