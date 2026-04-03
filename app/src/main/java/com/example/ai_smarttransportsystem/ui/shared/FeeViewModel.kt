package com.example.ai_smarttransportsystem.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.Fee
import com.example.ai_smarttransportsystem.data.repository.FeeRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class FeeViewModel(
    private val feeRepo: FeeRepository,
    private val studentRepo: StudentRepository
) : ViewModel() {

    private val _feeState = MutableLiveData<FeeUiState>(FeeUiState.Idle)
    val feeState: LiveData<FeeUiState> = _feeState

    private val _currentFee = MutableLiveData<Fee?>()
    val currentFee: LiveData<Fee?> = _currentFee

    private val _allFees = MutableLiveData<List<Fee>>()
    val allFees: LiveData<List<Fee>> = _allFees

    sealed class FeeUiState {
        object Idle : FeeUiState()
        object Loading : FeeUiState()
        data class Success(val message: String? = null) : FeeUiState()
        data class Error(val message: String) : FeeUiState()
        object NoFeeData : FeeUiState()
    }

    /**
     * Load student's fee for current semester
     */
    fun loadCurrentFee(forceRefresh: Boolean = false, semester: String = "Spring 2026") {
        viewModelScope.launch {
            _feeState.value = FeeUiState.Loading

            val result = feeRepo.getFeeForSemester(forceRefresh, semester)
            when {
                result.isSuccess -> {
                    val fee = result.getOrNull()
                    _currentFee.value = fee
                    _feeState.value = if (fee != null) {
                        FeeUiState.Success()
                    } else {
                        FeeUiState.NoFeeData
                    }
                }
                else -> {
                    _feeState.value = FeeUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load fee"
                    )
                }
            }
        }
    }

    /**
     * Load all fee records for the student
     */
    fun loadAllFees(forceRefresh: Boolean = false, semester: String = "Spring 2026") {
        viewModelScope.launch {
            _feeState.value = FeeUiState.Loading
            val result = feeRepo.getAllFeesForStudent(forceRefresh, semester)
            if (result.isSuccess) {
                _allFees.value = result.getOrNull() ?: emptyList()
                _feeState.value = FeeUiState.Success()
            } else {
                _feeState.value = FeeUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load fees"
                )
            }
        }
    }

    /**
     * Admin: Load all fees for all students
     */
    fun loadAllFeesAdmin(forceRefresh: Boolean = false, semester: String = "Spring 2026") {
        viewModelScope.launch {
            _feeState.value = FeeUiState.Loading
            val result = feeRepo.getAllFeesAdmin(forceRefresh, semester)
            if (result.isSuccess) {
                _allFees.value = result.getOrNull() ?: emptyList()
                _feeState.value = FeeUiState.Success()
            } else {
                _feeState.value = FeeUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load admin fees"
                )
            }
        }
    }

    /**
     * Simulate fee calculation
     */
    fun calculateAndDisplayFee(
        routeDistanceKm: Double,
        ratePerKm: Double = 50.0,
        semesterDays: Int = 120
    ) {
        val amount = feeRepo.calculateFee(routeDistanceKm, ratePerKm, semesterDays)

        val fee = Fee(
            semester = "Spring 2026",
            amount = amount,
            paymentStatus = "unpaid",
            routeDistance = routeDistanceKm,
            studentId = FirebaseAuth.getInstance().currentUser?.uid,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        _currentFee.value = fee
        _feeState.value = FeeUiState.Success("Fee calculated: PKR ${String.format("%.0f", amount)}")
    }

    /**
     * Mark the current student's fee as paid for a specific semester
     */
    fun markFeeAsPaid(forceRefresh: Boolean = false, semester: String = "Spring 2026") {
        viewModelScope.launch {
            _feeState.value = FeeUiState.Loading
            val result = feeRepo.markFeeAsPaid(semester)
            if (result.isSuccess) {
                _feeState.value = FeeUiState.Success("Fee marked as paid!")
                // Refresh both observers so the card and history update
                loadCurrentFee(forceRefresh, semester)
                loadAllFees()
            } else {
                _feeState.value = FeeUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to mark fee as paid"
                )
            }
        }
    }

    class Factory(
        private val feeRepo: FeeRepository,
        private val studentRepo: StudentRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FeeViewModel::class.java)) {
                return FeeViewModel(feeRepo, studentRepo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
