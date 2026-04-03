package com.example.ai_smarttransportsystem.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.Bus
import com.example.ai_smarttransportsystem.data.repository.BusRepository
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import kotlinx.coroutines.launch

class BusViewModel(
    private val repository: BusRepository
) : ViewModel() {

    private val _busState = MutableLiveData<BusUiState>(BusUiState.Idle)
    val busState: LiveData<BusUiState> = _busState

    private val _busesList = MutableLiveData<List<Bus>>()
    val busesList: LiveData<List<Bus>> = _busesList

    private val _busesWithIds = MutableLiveData<List<Pair<String, Bus>>>()
    val busesWithIds: LiveData<List<Pair<String, Bus>>> = _busesWithIds

    private val _assignedBusesWithIds = MutableLiveData<List<Pair<String, Bus>>>()
    val assignedBusesWithIds: LiveData<List<Pair<String, Bus>>> = _assignedBusesWithIds

    /**
     * Buses that have a route but NO supervisor yet.
     * Used by AdminAssignSupervisorActivity.
     */
    private val _busesForSupervisorAssign = MutableLiveData<List<Pair<String, Bus>>>()
    val busesForSupervisorAssign: LiveData<List<Pair<String, Bus>>> = _busesForSupervisorAssign

    private val _selectedBus = MutableLiveData<Bus?>()
    val selectedBus: LiveData<Bus?> = _selectedBus

    sealed class BusUiState {
        object Idle    : BusUiState()
        object Loading : BusUiState()
        data class Success(val message: String? = null) : BusUiState()
        data class Error(val message: String)           : BusUiState()
        object Empty   : BusUiState()
    }

    fun loadAllBuses(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _busState.value = BusUiState.Loading
            val result = repository.getAllBusesWithIds(forceRefresh)
            when {
                result.isSuccess -> {
                    val pairs = result.getOrNull() ?: emptyList()
                    _busesWithIds.value = pairs
                    _busesList.value    = pairs.map { it.second }
                    _busState.value     = if (pairs.isEmpty()) BusUiState.Empty else BusUiState.Success()
                }
                else -> _busState.value = BusUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load buses"
                )
            }
        }
    }

    fun loadAssignedBuses(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _busState.value = BusUiState.Loading
            val result = repository.getAssignedBusesWithIds(forceRefresh)
            when {
                result.isSuccess -> {
                    val pairs = result.getOrNull() ?: emptyList()
                    _assignedBusesWithIds.value = pairs
                    _busState.value = if (pairs.isEmpty()) BusUiState.Empty else BusUiState.Success()
                }
                else -> _busState.value = BusUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load assigned buses"
                )
            }
        }
    }

    /**
     * Load buses with a route assigned but no supervisor yet.
     * [filterRouteId] — when null, loads all such buses; when set, only the
     * bus for that specific route (used when arriving from a route card).
     */
    fun loadBusesForSupervisorAssign(forceRefresh: Boolean = false,filterRouteId: String? = null) {
        viewModelScope.launch {
            _busState.value = BusUiState.Loading
            val result = repository.getBusesWithRouteNoSupervisor(forceRefresh,filterRouteId)
            when {
                result.isSuccess -> {
                    val pairs = result.getOrNull() ?: emptyList()
                    _busesForSupervisorAssign.value = pairs
                    _busState.value = if (pairs.isEmpty()) BusUiState.Empty else BusUiState.Success()
                }
                else -> _busState.value = BusUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load buses"
                )
            }
        }
    }

    fun loadBus(forceRefresh: Boolean = false,busId: String) {
        viewModelScope.launch {
            _busState.value = BusUiState.Loading
            val result = repository.getBusById(forceRefresh,busId)
            when {
                result.isSuccess -> {
                    _selectedBus.value = result.getOrNull()
                    _busState.value = if (_selectedBus.value != null) BusUiState.Success() else BusUiState.Empty
                }
                else -> _busState.value = BusUiState.Error("Failed to load bus")
            }
        }
    }

    fun addNewBus(busNumber: String, plateNumber: String, capacity: Int, status: String) {
        viewModelScope.launch {
            _busState.value = BusUiState.Loading
            val bus = Bus(busNumber = busNumber, plateNumber = plateNumber, capacity = capacity, status = status)
            val result = repository.addBus(bus)
            if (result.isSuccess) {
                _busState.value = BusUiState.Success("Bus added: ${result.getOrNull()}")
                loadAllBuses()
            } else {
                _busState.value = BusUiState.Error(result.exceptionOrNull()?.message ?: "Failed to add bus")
            }
        }
    }

    fun assignBusToRoute(
        forceRefresh: Boolean = false,
        busDocId: String,
        routeId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val routeRepo   = RouteRepository()
            val routeResult = routeRepo.assignBusToRoute(routeId, busDocId)
            if (routeResult.isFailure) {
                onError(routeResult.exceptionOrNull()?.message ?: "Failed to update route")
                return@launch
            }
            val busResult = repository.updateBus(
                busDocId, mapOf("is_available" to false, "route_id" to routeId)
            )
            if (busResult.isFailure) {
                onError(busResult.exceptionOrNull()?.message ?: "Failed to update bus")
                return@launch
            }
            val studentRepo    = StudentRepository()
            val studentsResult = studentRepo.getStudentsByRouteId(forceRefresh,routeId)
            if (studentsResult.isFailure) { onSuccess(); return@launch }
            val students = studentsResult.getOrNull() ?: emptyList()
            for ((studentDocId, _) in students) {
                runCatching {
                    studentRepo.updateStudentStatus(
                        studentDocId       = studentDocId,
                        registrationStatus = "successful",
                        busId              = busDocId
                    )
                }
            }
            onSuccess()
        }
    }

    class Factory(private val repository: BusRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BusViewModel::class.java))
                return BusViewModel(repository) as T
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}