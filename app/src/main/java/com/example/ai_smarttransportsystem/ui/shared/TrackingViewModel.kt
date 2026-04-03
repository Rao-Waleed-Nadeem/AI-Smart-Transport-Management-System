package com.example.ai_smarttransportsystem.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.Tracking
import com.example.ai_smarttransportsystem.data.repository.TrackingRepository
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class TrackingViewModel(
    private val repository: TrackingRepository
) : ViewModel() {

    private val _trackingState = MutableLiveData<TrackingUiState>(TrackingUiState.Idle)
    val trackingState: LiveData<TrackingUiState> = _trackingState

    private val _liveLocation = MutableLiveData<Tracking?>()
    val liveLocation: LiveData<Tracking?> = _liveLocation

    private val _isSharingLocation = MutableLiveData(false)
    val isSharingLocation: LiveData<Boolean> = _isSharingLocation

    private var locationListener: ListenerRegistration? = null

    sealed class TrackingUiState {
        object Idle        : TrackingUiState()
        object Sharing     : TrackingUiState()   // supervisor actively sharing
        object Stopped     : TrackingUiState()   // supervisor stopped
        object Offline     : TrackingUiState()   // bus location null/unavailable
        data class Error(val message: String) : TrackingUiState()
    }

    // ── Supervisor side ───────────────────────────────────────────────────────

    /** Called by LocationTrackingService on each GPS update */
    fun pushLocation(lat: Double, lng: Double, speed: Float?) {
        viewModelScope.launch {
            val result = repository.updateMyLocation(lat, lng, speed)
            if (result.isSuccess) {
                _isSharingLocation.value = true
                _trackingState.value     = TrackingUiState.Sharing
            } else {
                _trackingState.value = TrackingUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to update location"
                )
            }
        }
    }

    fun stopSharing() {
        viewModelScope.launch {
            repository.clearMyLocation()
            _isSharingLocation.value = false
            _trackingState.value     = TrackingUiState.Stopped
        }
    }

    // ── Student / Admin side ──────────────────────────────────────────────────

    fun startListening(supervisorUid: String) {
        locationListener?.remove()
        locationListener = repository.listenToSupervisorLocation(
            supervisorUid = supervisorUid,
            onUpdate = { tracking ->
                _liveLocation.postValue(tracking)
                _trackingState.postValue(
                    if (tracking?.currentLatitude != null) TrackingUiState.Sharing
                    else TrackingUiState.Offline
                )
            },
            onError = { e ->
                _trackingState.postValue(TrackingUiState.Error(e.message ?: "Listen error"))
            }
        )
    }

    fun stopListening() {
        locationListener?.remove()
        locationListener = null
    }

    override fun onCleared() {
        super.onCleared()
        locationListener?.remove()
    }

    class Factory(private val repository: TrackingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrackingViewModel::class.java))
                return TrackingViewModel(repository) as T
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}