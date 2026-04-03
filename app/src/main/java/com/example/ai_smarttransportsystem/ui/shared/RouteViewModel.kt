package com.example.ai_smarttransportsystem.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.Route
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import kotlinx.coroutines.launch

class RouteViewModel(
    private val repository: RouteRepository
) : ViewModel() {

    private val _routeState = MutableLiveData<RouteUiState>(RouteUiState.Idle)
    val routeState: LiveData<RouteUiState> = _routeState

    private val _currentRoute = MutableLiveData<Route?>()
    val currentRoute: LiveData<Route?> = _currentRoute

    private val _allRoutes = MutableLiveData<List<Route>>()
    val allRoutes: LiveData<List<Route>> = _allRoutes

    sealed class RouteUiState {
        object Idle : RouteUiState()
        object Loading : RouteUiState()
        data class Success(val message: String? = null) : RouteUiState()
        data class Error(val message: String) : RouteUiState()
        object NoRouteAssigned : RouteUiState()
    }

    private val _assignedRoutes = MutableLiveData<List<Route>>()
    val assignedRoutes: LiveData<List<Route>> = _assignedRoutes

    /**
     * Load all routes that have a bus assigned (bus_id != null).
     * Used by admin tracking screen.
     */
    fun fetchRoutesWithBus(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _routeState.value = RouteUiState.Loading
            val result = repository.getAllRoutes(forceRefresh)
            if (result.isSuccess) {
                val routes = (result.getOrNull() ?: emptyList())
                    .filter { it.busId?.isNotBlank() == true }
                _assignedRoutes.value = routes
                _routeState.value = RouteUiState.Success("${routes.size} assigned routes loaded")
            } else {
                _routeState.value = RouteUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to fetch routes"
                )
            }
        }
    }

    /**
     * Load all routes (admin use case)
     */
    fun fetchAllRoutes(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _routeState.value = RouteUiState.Loading
            val result = repository.getAllRoutes(forceRefresh)
            if (result.isSuccess) {
                _allRoutes.value = result.getOrNull() ?: emptyList()
                _routeState.value = RouteUiState.Success("Routes loaded")
            } else {
                _routeState.value = RouteUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to fetch all routes"
                )
            }
        }
    }

    /**
     * Load route for the current student's assigned routeId
     * (You get routeId from Student data first)
     */
    fun loadStudentRoute(forceRefresh: Boolean = false,routeId: String) {
        viewModelScope.launch {
            _routeState.value = RouteUiState.Loading

            val result = repository.getRouteById(forceRefresh,routeId)
            when {
                result.isSuccess -> {
                    val route = result.getOrNull()
                    if (route != null) {
                        _currentRoute.value = route
                        _routeState.value = RouteUiState.Success("Route loaded")
                    } else {
                        _routeState.value = RouteUiState.NoRouteAssigned
                    }
                }
                else -> {
                    _routeState.value = RouteUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load route"
                    )
                }
            }
        }
    }

    /**
     * Load route for a specific bus (supervisor use case)
     */
    fun loadBusRoute(forceRefresh: Boolean = false,busId: String) {
        viewModelScope.launch {
            _routeState.value = RouteUiState.Loading

            val result = repository.getRouteForBus(forceRefresh,busId)
            when {
                result.isSuccess -> {
                    val route = result.getOrNull()
                    if (route != null) {
                        _currentRoute.value = route
                        _routeState.value = RouteUiState.Success("Bus route loaded")
                    } else {
                        _routeState.value = RouteUiState.NoRouteAssigned
                    }
                }
                else -> {
                    _routeState.value = RouteUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load bus route"
                    )
                }
            }
        }
    }

    /**
     * Clear current route data
     */
    fun clearRoute() {
        _currentRoute.value = null
        _routeState.value = RouteUiState.Idle
    }

    // ────────────────────────────────────────────────
    // Factory for dependency injection
    // ────────────────────────────────────────────────
    class Factory(
        private val repository: RouteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RouteViewModel::class.java)) {
                return RouteViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}