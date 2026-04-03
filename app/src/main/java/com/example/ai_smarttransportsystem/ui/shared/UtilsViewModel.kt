package com.example.ai_smarttransportsystem.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ai_smarttransportsystem.data.repository.UtilsRepository
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.Utils
import kotlinx.coroutines.launch

class UtilsViewModel(
    private val repository: UtilsRepository
) : ViewModel() {

    private val _utilsState = MutableLiveData<UtilsUiState>(UtilsUiState.Idle)
    val utilsState: LiveData<UtilsUiState> = _utilsState

    private val _utils = MutableLiveData<Utils?>()
    val utils: LiveData<Utils?> = _utils

    sealed class UtilsUiState {
        object Idle    : UtilsUiState()
        object Loading : UtilsUiState()
        data class Success(val message: String? = null) : UtilsUiState()
        data class Error(val message: String)           : UtilsUiState()
        object Empty   : UtilsUiState()
    }

    fun loadUtils() {
        viewModelScope.launch {
            _utilsState.value = UtilsUiState.Loading

            val result = repository.getUtils()

            if (result.isSuccess) {
                val utilsData = result.getOrNull()
                if (utilsData != null) {
                    _utils.value = utilsData
                    _utilsState.value = UtilsUiState.Success()
                } else {
                    _utilsState.value = UtilsUiState.Empty
                }
            } else {
                _utilsState.value = UtilsUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load utils"
                )
            }
        }
    }

    /**
     * Toggle route optimization flag and refresh UI immediately
     */
    fun toggleRouteOptimization(isOptimized: Boolean) {
        viewModelScope.launch {
            _utilsState.value = UtilsUiState.Loading

            // 1. First, update the local LiveData so UI changes immediately
            val currentUtils = _utils.value
            if (currentUtils != null) {
                _utils.value = currentUtils.copy(isOptimized = isOptimized)
            }

            // 2. Perform the database update
            val result = repository.routeOptimizedToggle(isOptimized)

            if (result.isSuccess) {
                _utilsState.value = UtilsUiState.Success()
            } else {
                // 3. If DB update fails, revert the local state
                if (currentUtils != null) {
                    _utils.value = currentUtils
                }
                _utilsState.value = UtilsUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to update optimization status"
                )
            }
        }
    }

    class Factory(private val repository: UtilsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UtilsViewModel::class.java))
                return UtilsViewModel(repository) as T
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
