package com.example.ai_smarttransportsystem.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.Bus
import com.example.ai_smarttransportsystem.data.model.Stop
import com.example.ai_smarttransportsystem.data.model.Supervisor
import com.example.ai_smarttransportsystem.data.repository.AdminRepository
import kotlinx.coroutines.launch

class AdminViewModel(
    private val repository: AdminRepository
) : ViewModel() {

    private val _adminState = MutableLiveData<AdminUiState>(AdminUiState.Idle)
    val adminState: LiveData<AdminUiState> = _adminState

    private val _buses = MutableLiveData<List<Bus>>()
    val buses: LiveData<List<Bus>> = _buses

    private val _stops = MutableLiveData<List<Stop>>()
    val stops: LiveData<List<Stop>> = _stops

    private val _supervisors = MutableLiveData<List<Pair<String, Supervisor>>>()
    val supervisors: LiveData<List<Pair<String, Supervisor>>> = _supervisors

    sealed class AdminUiState {
        object Idle : AdminUiState()
        object Loading : AdminUiState()
        data class Success(val message: String? = null) : AdminUiState()
        data class Error(val message: String) : AdminUiState()
    }

    /**
     * Load dashboard data (buses, stops, supervisors)
     */
    fun loadDashboardData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _adminState.value = AdminUiState.Loading

            val busesResult = repository.getAllBuses(forceRefresh)
            val stopsResult = repository.getAllStops(forceRefresh)
            val supResult = repository.getAllSupervisors(forceRefresh)

            if (busesResult.isSuccess && stopsResult.isSuccess && supResult.isSuccess) {
                _buses.value = busesResult.getOrNull()
                _stops.value = stopsResult.getOrNull()
                _supervisors.value = supResult.getOrNull()
                _adminState.value = AdminUiState.Success()
            } else {
                val err = listOf(busesResult, stopsResult, supResult)
                    .firstOrNull { it.isFailure }?.exceptionOrNull()?.message
                    ?: "Failed to load data"
                _adminState.value = AdminUiState.Error(err)
            }
        }
    }

    /**
     * Add new bus from admin form
     */
    fun addNewBus(busNumber: String, capacity: Int) {
        viewModelScope.launch {
            _adminState.value = AdminUiState.Loading

            val bus = Bus(
                busNumber = busNumber,
                capacity = capacity,
                status = "active"
            )

            val result = repository.addBus(bus)
            if (result.isSuccess) {
                _adminState.value = AdminUiState.Success("Bus added: ${result.getOrNull()}")
                loadDashboardData() // refresh list
            } else {
                _adminState.value = AdminUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to add bus"
                )
            }
        }
    }

    /**
     * Add new stop (called from map click later)
     */
    fun addNewStop(name: String, lat: Double, lng: Double, address: String? = null) {
        viewModelScope.launch {
            val stop = Stop(
                stopName = name,
                latitude = lat,
                longitude = lng,
            )

            val result = repository.addStop(stop)
            if (result.isSuccess) {
                _adminState.value = AdminUiState.Success("Stop added")
                loadDashboardData()
            } else {
                _adminState.value = AdminUiState.Error("Failed to add stop")
            }
        }
    }

    /**
     * Assign bus to supervisor
     */
    fun assignBus(supervisorUid: String, busId: String, routeId: String? = null) {
        viewModelScope.launch {
            val result = repository.assignBusToSupervisor(supervisorUid, busId, routeId)
            if (result.isSuccess) {
                _adminState.value = AdminUiState.Success("Bus assigned successfully")
            } else {
                _adminState.value = AdminUiState.Error("Assignment failed")
            }
        }
    }
}