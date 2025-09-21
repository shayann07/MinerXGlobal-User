package com.minerxgloble.minerxgloble.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentLoginBinding
import com.minerxgloble.minerxgloble.repos.AuthRepository
import com.minerxgloble.minerxgloble.repos.LoginResult
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.viewModels.AuthViewModel
import com.minerxgloble.minerxgloble.viewModels.factory.AuthViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginFragment : BaseFragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(
            AuthRepository(
                FirebaseAuth.getInstance(),
                FirebaseFirestore.getInstance(),
                PrefService(requireContext())
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginBtn.setOnClickListener { loginUser() }
        binding.signUpLink.setOnClickListener { findNavController().navigate(R.id.signupFragment) }
        binding.forgotPassword.setOnClickListener { findNavController().navigate(R.id.forgetPasswordFragment) }

        // Show success banner coming from the signup screen
        findNavController().previousBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<String>("signup_msg")
            ?.observe(viewLifecycleOwner) { msg ->
                msg?.let {
                    showSnackbar(it)                                 // green banner
                    // remove so it won’t re-show on config change
                    findNavController()
                        .previousBackStackEntry
                        ?.savedStateHandle
                        ?.remove<String>("signup_msg")
                }
            }

        authViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            hideLoading()
            when (result) {
                LoginResult.SUCCESS -> {
                    PrefService(requireContext()).saveLogin()

                    val navOptions = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.loginFragment, true)
                        .build()

                    findNavController().navigate(R.id.homeFragment, null, navOptions)
                    findNavController().currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("login_success", true)
                }
                LoginResult.UNVERIFIED_EMAIL -> {
                    val email = binding.emailInput.text.toString().trim()
                    val password = binding.passwordInput.text.toString().trim()
                    showSnackbar("Please verify your email", isError = true, actionLabel = getString(R.string.resend_email)) {
                        authViewModel.resendVerificationEmail(email, password)
                    }
                }
                else -> {
                    showSnackbar("Invalid email or password", isError = true)
                }
            }
        }

        authViewModel.verificationEmailSent.observe(viewLifecycleOwner) { success ->
            val messageRes = if (success) R.string.verification_email_sent else R.string.verification_email_failed
            showSnackbar(getString(messageRes), isError = !success)
        }
    }

    private fun loginUser() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            showSnackbar("Please enter email and password", isError = true)
            return
        }

        showLoading()
        authViewModel.loginUser(email, password)
    }

    private fun showSnackbar(
        message: String,
        isError: Boolean = false,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {

        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        val bgColor = ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bgColor)
        snack.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        if (actionLabel != null && action != null) {
            snack.setAction(actionLabel) { action() }
        }
        // Add bottom margin
        val lp = snack.view.layoutParams
        val marginPx = (24 * resources.displayMetrics.density).toInt()
        when (lp) {
            is ViewGroup.MarginLayoutParams -> {
                lp.bottomMargin = lp.bottomMargin + marginPx
                snack.view.layoutParams = lp
            }
        }
        snack.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
