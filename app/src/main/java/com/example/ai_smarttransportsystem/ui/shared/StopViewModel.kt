package com.example.ai_smarttransportsystem.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.Stop
import com.example.ai_smarttransportsystem.data.repository.StopRepository
import kotlinx.coroutines.launch

class StopViewModel(
    private val repository: StopRepository
) : ViewModel() {

    private val _stopsState = MutableLiveData<StopUiState>(StopUiState.Idle)
    val stopsState: LiveData<StopUiState> = _stopsState

    private val _allStops = MutableLiveData<List<Stop>>()
    val allStops: LiveData<List<Stop>> = _allStops

    private val _selectedStop = MutableLiveData<Stop?>()
    val selectedStop: LiveData<Stop?> = _selectedStop

    sealed class StopUiState {
        object Idle : StopUiState()
        object Loading : StopUiState()
        data class Success(val message: String? = null) : StopUiState()
        data class Error(val message: String) : StopUiState()
        object Empty : StopUiState()
    }

    private val _orderedStops = MutableLiveData<List<Pair<String, Stop>>>()
    val orderedStops: LiveData<List<Pair<String, Stop>>> = _orderedStops

    /**
     * Fetch stops by their IDs in the exact order given (optimized_order from Route).
     * Returns List<Pair<docId, Stop>> preserving the optimized order.
     */
    fun loadStopsByIds(forceRefresh: Boolean = false,orderedIds: List<String>) {
        viewModelScope.launch {
            _stopsState.value = StopUiState.Loading
            val result = repository.getStopsByIds(forceRefresh,orderedIds)
            when {
                result.isSuccess -> {
                    val stopMap = result.getOrNull() ?: emptyMap()
                    // Rebuild list in optimized_order sequence, skip missing IDs
                    val ordered = orderedIds.mapNotNull { id ->
                        stopMap[id]?.let { Pair(id, it) }
                    }
                    _orderedStops.value = ordered
                    _stopsState.value = if (ordered.isEmpty())
                        StopUiState.Empty
                    else
                        StopUiState.Success("Loaded ${ordered.size} stops")
                }
                else -> {
                    _stopsState.value = StopUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load stops"
                    )
                }
            }
        }
    }

    /**
     * Load all stops (usually for selection list in registration or map)
     */
    fun loadAllStops(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _stopsState.value = StopUiState.Loading

            val result = repository.getAllStops(forceRefresh)
            when {
                result.isSuccess -> {
                    val stops = result.getOrNull() ?: emptyList()
                    _allStops.value = stops
                    _stopsState.value = if (stops.isEmpty()) {
                        StopUiState.Empty
                    } else {
                        StopUiState.Success("Loaded ${stops.size} stops")
                    }
                }
                else -> {
                    _stopsState.value = StopUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load stops"
                    )
                }
            }
        }
    }

    /**
     * Select a stop (called from list item click or map marker tap)
     */
    fun selectStop(forceRefresh: Boolean = false,stopId: String) {
        viewModelScope.launch {
            val result = repository.getStopById(forceRefresh,stopId)
            if (result.isSuccess) {
                _selectedStop.value = result.getOrNull()
                _stopsState.value = StopUiState.Success("Stop selected")
            } else {
                _stopsState.value = StopUiState.Error("Cannot load this stop")
            }
        }
    }

    /**
     * Clear current selection (useful when user wants to change choice)
     */
    fun clearSelection() {
        _selectedStop.value = null
    }

    // ────────────────────────────────────────────────
    // Factory (required because we pass repository dependency)
    // ────────────────────────────────────────────────
    class Factory(
        private val repository: StopRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StopViewModel::class.java)) {
                return StopViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}