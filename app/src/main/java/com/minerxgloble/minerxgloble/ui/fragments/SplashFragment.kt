package com.minerxgloble.minerxgloble.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentSplashBinding
import com.minerxgloble.minerxgloble.ui.MainActivity
import com.minerxgloble.minerxgloble.ui.animation.DropIntroOverlay
import com.minerxgloble.minerxgloble.utils.PrefService

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private var overlay: DropIntroOverlay? = null
    private var hasNavigated = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)

        overlay = DropIntroOverlay.play(
            parent = binding.root,
            config = DropIntroOverlay.Config(
                soundEnabled = true,
                hapticsEnabled = true,
                useVibratorApi = false,
                // remove immediately once the animation completes – no extra fade
                removeOnFinish = true,
                finishDelayMs = 0L
            )
        ) {
            // Animation finished naturally → navigate right away
            navigateOnce()
        }

        // Tap-to-skip: finish visuals instantly and navigate right away
        overlay?.setOnClickListener {
            overlay?.forceFinish()
            navigateOnce()
        }
    }

    private fun navigateOnce() {
        if (hasNavigated) return
        hasNavigated = true

        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.splashFragment) return

        // ⬇️ Hide bottom bar until Home reports its first frame
        (requireActivity() as? MainActivity)?.deferBottomBarForNextHome()

        // ⬇️ Make sure the overlay is gone right now
        overlay?.cancel()
        overlay = null

        val isLoggedIn = PrefService(requireContext()).checkLogin()

        val opts = navOptions {
            anim {
                enter = R.anim.mxg_fade_in
                exit = R.anim.mxg_fade_out
                popEnter = R.anim.mxg_fade_in
                popExit = R.anim.mxg_fade_out
            }
            popUpTo(R.id.splashFragment) { inclusive = true }
            launchSingleTop = true
        }

        // ⬇️ Post to the next frame so Splash is fully detached before we swap
        binding.root.post {
            if (isLoggedIn) {
                nav.navigate(SplashFragmentDirections.actionSplashToHome(), opts)
            } else {
                nav.navigate(SplashFragmentDirections.actionSplashToLogin(), opts)
            }
        }
    }

    override fun onDestroyView() {
        overlay?.cancel()
        overlay = null
        _binding = null
        super.onDestroyView()
    }
}
