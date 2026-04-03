package com.example.ai_smarttransportsystem.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.data.repository.UserRepository
import com.example.ai_smarttransportsystem.databinding.LoginPageBinding
import com.example.ai_smarttransportsystem.ui.admin.AdminDashboardActivity
import com.example.ai_smarttransportsystem.ui.student.StudentHomeActivity
import com.example.ai_smarttransportsystem.ui.supervisor.SupervisorDashboardActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginPageBinding

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.factory(UserRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LoginPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Observe state
        authViewModel.authState.observe(this) { state ->
            when (state) {
                is AuthViewModel.AuthState.Loading -> setLoading(true)
                is AuthViewModel.AuthState.Success -> {
                    setLoading(false)
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    navigateToDashboard(state.role)
                }
                is AuthViewModel.AuthState.Error -> {
                    setLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> { /* idle */ }
            }
        }

        binding.btnSignIn.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authViewModel.login(forceRefresh = true,email, password)
        }

        binding.btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSignIn.isEnabled = !loading
        binding.btnSignIn.text = if (loading) "" else "Sign in"
        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        binding.emailInput.isEnabled = !loading
        binding.passwordInput.isEnabled = !loading
    }

    private fun navigateToDashboard(role: String) {
        val intent = when (role) {
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "supervisor" -> Intent(this, SupervisorDashboardActivity::class.java)
            "student" -> Intent(this, StudentHomeActivity::class.java)
            else -> null
        }

        if (intent != null) {
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Unknown role: $role", Toast.LENGTH_SHORT).show()
        }
    }
}
