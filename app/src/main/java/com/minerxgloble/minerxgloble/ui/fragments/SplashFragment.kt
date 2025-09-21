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
import com.minerxgloble.minerxgloble.ui.animation.DropIntroOverlay
import com.minerxgloble.minerxgloble.utils.PrefService

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private var overlay: DropIntroOverlay? = null
    private var isAnimationFinished = false
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
                removeOnFinish = false, // we'll fade & remove manually
                finishDelayMs = 350L
            )
        ) {
            // Natural end of the animation
            isAnimationFinished = true
            smoothNavigate()
        }

        // Tap-to-skip on the overlay itself (it sits on top of the layout)
        overlay?.setOnClickListener {
            if (!isAnimationFinished) {
                // 1) Instantly finish the animation visuals
                overlay?.forceFinish()   // must forward to DropIntroView.forceFinish()
                isAnimationFinished = true
            }
            // 2) Smooth fade + navigate (debounced)
            smoothNavigate()
        }
    }

    private fun smoothNavigate() {
        if (hasNavigated) return
        hasNavigated = true

        // Quick fade-out of the overlay before navigating to make it feel smooth
        val ov = overlay
        if (ov != null) {
            ov.isClickable = false
            ov.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction {
                    ov.cancel()          // stop any timers/cleanup inside overlay
                    overlay = null
                    doNavigate()
                }
                .start()
        } else {
            doNavigate()
        }
    }

    private fun doNavigate() {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.splashFragment) return

        val isLoggedIn = PrefService(requireContext()).checkLogin()

        val opts = navOptions {
            anim {
                enter = R.anim.mxg_fade_in
                exit = R.anim.mxg_fade_out
                popEnter = R.anim.mxg_fade_in
                popExit = R.anim.mxg_fade_out
            }
            // ✨ Key line: remove Splash from back stack
            popUpTo(R.id.splashFragment) { inclusive = true }
            launchSingleTop = true
        }

        if (isLoggedIn) {
            nav.navigate(SplashFragmentDirections.actionSplashToHome(), opts)
        } else {
            nav.navigate(SplashFragmentDirections.actionSplashToLogin(), opts)
        }
    }


    override fun onDestroyView() {
        overlay?.cancel()
        overlay = null
        _binding = null
        super.onDestroyView()
    }
}
