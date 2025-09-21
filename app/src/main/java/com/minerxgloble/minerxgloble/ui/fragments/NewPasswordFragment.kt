package com.minerxgloble.minerxgloble.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentNewPasswordBinding
import com.minerxgloble.minerxgloble.repos.AuthRepository
import com.minerxgloble.minerxgloble.utils.PrefService
import com.minerxgloble.minerxgloble.viewModels.AuthViewModel
import com.minerxgloble.minerxgloble.viewModels.factory.AuthViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NewPasswordFragment : BaseFragment() {

    private var _binding: FragmentNewPasswordBinding? = null
    private val binding get() = _binding!!

    private val prefService by lazy { PrefService(requireContext()) }

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(
            AuthRepository(
                FirebaseAuth.getInstance(),
                FirebaseFirestore.getInstance(),
                prefService
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)
        // Observe password update result
        authViewModel.updatePasswordSuccess.observe(viewLifecycleOwner) { success ->
            hideLoading()
            if (success) {
                showSnackbar("Password updated successfully!")

                // ✅ Navigate to ProfileFragment and clear NewPasswordFragment from back stack
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.newPasswordFragment, true) // remove self
                    .build()

                findNavController().navigate(R.id.profileFragment, null, navOptions)
            } else {
                showSnackbar("Failed to update password.", isError = true)
            }
        }

        binding.resetBtn.setOnClickListener {
            val newPass = binding.newPasswordInput.text.toString().trim()
            val confirm = binding.confirmPasswordInput.text.toString().trim()

            when {
                newPass.length < 6 || confirm.length < 6 ->
                    showSnackbar("Password must be at least 6 characters.", isError = true)

                newPass != confirm ->
                    showSnackbar("New passwords do not match.", isError = true)

                else -> {
                    showLoading()
                    authViewModel.updateUserPassword(
                        prefService.getString("email").orEmpty(),
                        newPass
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

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
