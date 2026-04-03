package com.example.ai_smarttransportsystem.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.data.model.User
import com.example.ai_smarttransportsystem.data.repository.UserRepository
import com.example.ai_smarttransportsystem.databinding.RegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// In RegisterActivity.kt
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: RegisterBinding
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.factory(UserRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = RegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Observe state
        authViewModel.authState.observe(this) { state ->
            when (state) {
                is AuthViewModel.AuthState.Loading -> setLoading(true)
                is AuthViewModel.AuthState.Success -> {
                    setLoading(false)
                    Toast.makeText(this, "Registration successful! Role: ${state.role}", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                is AuthViewModel.AuthState.Error -> {
                    setLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> { /* idle */ }
            }
        }

        binding.btnRegister.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()
            val confirmPassword = binding.confirmPasswordInput.text.toString().trim()
            val contact = binding.contactInput.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || contact.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authViewModel.register(name, email, password, contact)
        }

        // ... btnSignIn click remains the same for now ...
    }

    private fun setLoading(loading: Boolean) { /* same as before */ }
}