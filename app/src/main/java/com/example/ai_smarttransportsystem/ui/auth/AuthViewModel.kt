package com.example.ai_smarttransportsystem.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.User
import com.example.ai_smarttransportsystem.data.repository.UserRepository
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: UserRepository
) : ViewModel() {               // ← ONLY ViewModel here — no Factory

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    sealed class AuthState {
        object Loading : AuthState()
        data class Success(val role: String) : AuthState()
        data class Error(val message: String) : AuthState()
        object Idle : AuthState()
    }

    fun login(forceRefresh: Boolean = false,email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = repository.login(email, password)
            if (result.isSuccess) {
                val uid = repository.getCurrentUserUid()
                if (uid != null) {
                    checkRoleAndNavigate(forceRefresh,uid)
                } else {
                    _authState.value = AuthState.Error("User ID not found")
                }
            } else {
                _authState.value = AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Login failed"
                )
            }
        }
    }

    fun register(
        name: String,
        email: String,
        password: String,
        contact: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val role = detectRoleFromEmail(email)
            if (role == "unknown") {
                _authState.value = AuthState.Error("Invalid email domain")
                return@launch
            }

            val createResult = repository.createUserWithEmailAndPassword(email, password)
            if (createResult.isFailure) {
                _authState.value = AuthState.Error(
                    createResult.exceptionOrNull()?.message ?: "Registration failed"
                )
                return@launch
            }

            val uid = createResult.getOrNull()!!
            val user = User(uid = uid, name = name, email = email, role = role, contact = contact)

            val saveResult = repository.saveUser(user)
            if (saveResult.isSuccess) {
                _authState.value = AuthState.Success(role)
            } else {
                _authState.value = AuthState.Error(
                    saveResult.exceptionOrNull()?.message ?: "Failed to save profile"
                )
            }
        }
    }

    private fun checkRoleAndNavigate(forceRefresh: Boolean = false,uid: String) {
        viewModelScope.launch {
            val result = repository.getUser(forceRefresh,uid)
            if (result.isSuccess) {
                val role = result.getOrNull()?.role ?: "unknown"
                _authState.value = AuthState.Success(role)
            } else {
                _authState.value = AuthState.Error("Failed to fetch user role")
            }
        }
    }

    private fun detectRoleFromEmail(email: String): String {
        val emailLower = email.lowercase()
        val domain = emailLower.substringAfter("@")
        return when {
            emailLower.contains("admin@nu.edu.pk") ||
                    emailLower.contains("a.d.m.i.n@nu.edu.pk") -> "admin"
            domain == "cfd.nu.edu.pk" -> "student"
            domain == "nu.edu.pk" -> "supervisor"
            else -> "unknown"
        }
    }

    // ────────────────────────────────────────────────
    // Correct place for the Factory – as a separate nested class
    // ────────────────────────────────────────────────
    companion object {
        fun factory(repository: UserRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                        return AuthViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}