package com.minerxgloble.minerxgloble.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentForgetPasswordBinding
import com.minerxgloble.minerxgloble.repos.AuthRepository
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.viewModels.AuthViewModel
import com.minerxgloble.minerxgloble.viewModels.factory.AuthViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ForgetPasswordFragment : BaseFragment() {

    private var _binding: FragmentForgetPasswordBinding? = null
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
        _binding = FragmentForgetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        binding.sendBtn.setOnClickListener {
            sendResetEmail()
        }

        authViewModel.resetEmailSent.observe(viewLifecycleOwner) { sent ->
            hideLoading()
            if (sent) {
                showSnackbar("Reset email sent! Check your inbox.")
                findNavController().navigate(R.id.loginFragment)
            } else {
                showSnackbar("Failed to send reset email.", isError = true)
            }
        }
    }

    private fun sendResetEmail() {
        val email = binding.emailInput.text.toString().trim()
        if (email.isEmpty()) {
            showSnackbar("Please enter your email", isError = true)
            return
        }
        showLoading()
        authViewModel.sendResetEmail(email)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Optionally keep this for toast/snackbar feedback elsewhere
    private fun showSnackbar(message: String, isError: Boolean = false) {

        val snack = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
        val bgColor = ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bgColor)
        snack.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snack.show()
    }
}
