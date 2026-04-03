package com.example.ai_smarttransportsystem.ui.supervisor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.Supervisor
import com.example.ai_smarttransportsystem.data.model.User
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.SupervisorRepository
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

class SupervisorViewModel(
    private val repository: SupervisorRepository
) : ViewModel() {

    private val _supervisorState = MutableLiveData<SupervisorUiState>(SupervisorUiState.Idle)
    val supervisorState: LiveData<SupervisorUiState> = _supervisorState

    private val _currentSupervisor = MutableLiveData<Supervisor?>()
    val currentSupervisor: LiveData<Supervisor?> = _currentSupervisor

    private val _assignedBusId = MutableLiveData<String?>()
    val assignedBusId: LiveData<String?> = _assignedBusId

    private val _assignedRouteId = MutableLiveData<String?>()
    val assignedRouteId: LiveData<String?> = _assignedRouteId

    /** All users with role=supervisor (used in old flow) */
    private val _supervisorsForAssign = MutableLiveData<List<Pair<String, User>>>()
    val supervisorsForAssign: LiveData<List<Pair<String, User>>> = _supervisorsForAssign

    /**
     * Only supervisors NOT yet assigned (no doc in supervisors/ collection).
     * Used by AdminAssignSupervisorActivity.
     */
    private val _availableSupervisors = MutableLiveData<List<Pair<String, User>>>()
    val availableSupervisors: LiveData<List<Pair<String, User>>> = _availableSupervisors

    sealed class SupervisorUiState {
        object Idle    : SupervisorUiState()
        object Loading : SupervisorUiState()
        data class Success(val message: String? = null) : SupervisorUiState()
        data class Error(val message: String)           : SupervisorUiState()
        data class NoAssignment(val needsAssignment: Boolean = true) : SupervisorUiState()
    }

    fun loadSupervisorData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _supervisorState.value = SupervisorUiState.Loading
            val result = repository.getCurrentSupervisor(forceRefresh)
            when {
                result.isSuccess -> {
                    val supervisor = result.getOrNull()
                    _currentSupervisor.value = supervisor
                    if (supervisor?.assignedBus == null || supervisor.assignedRoute == null) {
                        _supervisorState.value = SupervisorUiState.NoAssignment(true)
                    } else {
                        _assignedBusId.value   = supervisor.assignedBus
                        _assignedRouteId.value = supervisor.assignedRoute
                        _supervisorState.value = SupervisorUiState.Success()
                    }
                }
                else -> _supervisorState.value = SupervisorUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load supervisor data"
                )
            }
        }
    }

    fun refreshAssignments(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val busResult   = repository.getAssignedBusId(forceRefresh)
            val routeResult = repository.getAssignedRouteId(forceRefresh)
            if (busResult.isSuccess && routeResult.isSuccess) {
                _assignedBusId.value   = busResult.getOrNull()
                _assignedRouteId.value = routeResult.getOrNull()
                if (_assignedBusId.value != null && _assignedRouteId.value != null) {
                    _supervisorState.value = SupervisorUiState.Success("Assignments updated")
                }
            } else {
                _supervisorState.value = SupervisorUiState.Error("Failed to refresh assignments")
            }
        }
    }

    /** Load ALL users with role=supervisor (legacy — kept for compatibility) */
    fun loadAllSupervisors(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _supervisorState.value = SupervisorUiState.Loading
            val result = repository.getAllSupervisorsFromUsers(forceRefresh)
            when {
                result.isSuccess -> {
                    val list = result.getOrNull() ?: emptyList()
                    _supervisorsForAssign.value = list
                    _supervisorState.value = if (list.isEmpty())
                        SupervisorUiState.NoAssignment(false)
                    else
                        SupervisorUiState.Success()
                }
                else -> _supervisorState.value = SupervisorUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load supervisors"
                )
            }
        }
    }

    /**
     * Load only supervisors who are NOT yet assigned to any bus/route.
     * A supervisor is "assigned" when their UID exists as a document in
     * the supervisors/ collection.
     */
    fun loadAvailableSupervisors(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _supervisorState.value = SupervisorUiState.Loading
            val result = repository.getAvailableSupervisors(forceRefresh)
            when {
                result.isSuccess -> {
                    val list = result.getOrNull() ?: emptyList()
                    _availableSupervisors.value = list
                    _supervisorState.value = if (list.isEmpty())
                        SupervisorUiState.NoAssignment(false)
                    else
                        SupervisorUiState.Success()
                }
                else -> _supervisorState.value = SupervisorUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load supervisors"
                )
            }
        }
    }

    /**
     * Assign a supervisor to a bus + route.
     *
     * Steps:
     * 1. Stamp supervisor_id onto the route document.
     * 2. Write the supervisor doc (assigned_bus, assigned_route) and
     *    stamp supervisor_id onto the bus document.
     */
    fun assignSupervisor(
        supervisorUid: String,
        busDocId: String,
        routeId: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _supervisorState.value = SupervisorUiState.Loading

            // Step 1 — stamp supervisor_id on the route document
            if (!routeId.isNullOrEmpty()) {
                val routeRepo   = RouteRepository()
                // ✅ Correct: routeId first, supervisorUid second
                val routeResult = routeRepo.assignSupervisorToRoute(routeId, supervisorUid)
                if (routeResult.isFailure) {
                    val msg = routeResult.exceptionOrNull()?.message ?: "Failed to update route"
                    _supervisorState.value = SupervisorUiState.Error(msg)
                    onError(msg)
                    return@launch
                }
            }

            // Step 2 — write supervisor doc + stamp bus
            val result = repository.assignSupervisorToBus(supervisorUid, busDocId, routeId)
            if (result.isSuccess) {
                _supervisorState.value = SupervisorUiState.Success("Supervisor assigned")
                onSuccess()
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Assignment failed"
                _supervisorState.value = SupervisorUiState.Error(msg)
                onError(msg)
            }
        }
    }

    class Factory(private val repository: SupervisorRepository) :
        androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SupervisorViewModel::class.java))
                return SupervisorViewModel(repository) as T
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}