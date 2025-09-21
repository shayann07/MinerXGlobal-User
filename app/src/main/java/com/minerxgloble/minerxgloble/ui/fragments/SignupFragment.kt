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
import com.minerxgloble.minerxgloble.databinding.FragmentSignupBinding
import com.minerxgloble.minerxgloble.repos.AuthRepository
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.viewModels.AuthViewModel
import com.minerxgloble.minerxgloble.viewModels.factory.AuthViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class
SignupFragment : BaseFragment() {

    private var _binding: FragmentSignupBinding? = null
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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setupDrawerTrigger(view)

        /* --- LiveData observers --------------------------------------------------- */
        authViewModel.registrationSuccess.observe(viewLifecycleOwner) { ok ->
            if (ok == true) {
                hideLoading()
                findNavController().currentBackStackEntry
                    ?.savedStateHandle
                    ?.set("signup_msg", "Registration successful! Verification link sent.")
                findNavController().navigate(R.id.loginFragment)

            }
        }

        authViewModel.registrationError.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                hideLoading()
                showSnackbar(it, isError = true)
            }
        }
        // ─── Pre-fill referral field from SharedPref (if any) ────────────────
        PrefService(requireContext()).getReferralFromLink()?.let { savedRef ->
            binding.referralInput.setText(savedRef)
        }
        /* --- Click listeners ------------------------------------------------------ */
        binding.signUpBtn.setOnClickListener { tryRegister() }
        binding.loginLink.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }
    }

    private fun tryRegister() {

        val name = binding.nameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()
//        val confirmPassword = binding.confirmPasswordInput.text.toString().trim()
        val referral = binding.referralInput.text.toString().trim()
        val phone = binding.phoneInput.text.toString().trim()

        when {
            name.isEmpty() || email.isEmpty() ||
                    password.isEmpty()  -> {
                showSnackbar("Please fill all fields", isError = true); return
            }


        }

        showLoading()
        authViewModel.registerUser(name, lastName, email, password, phone, referral)
    }

    /* --------------------------------------------------------------------------- */

    private fun showSnackbar(message: String, isError: Boolean = false) {


        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        val bg = ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bg)
        snack.setTextColor(
            ContextCompat.getColor(requireContext(), android.R.color.white)
        )
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