package com.example.ai_smarttransportsystem.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.Attendance
import com.example.ai_smarttransportsystem.data.model.Student
import com.example.ai_smarttransportsystem.data.repository.AttendanceRepository
import kotlinx.coroutines.launch

class AttendanceViewModel(
    private val repository: AttendanceRepository
) : ViewModel() {

    private val _attendanceState = MutableLiveData<AttendanceUiState>(AttendanceUiState.Idle)
    val attendanceState: LiveData<AttendanceUiState> = _attendanceState

    private val _studentsList = MutableLiveData<List<Student>>()
    val studentsList: LiveData<List<Student>> = _studentsList

    private val _attendanceMap = MutableLiveData<MutableMap<String, Boolean>>()
    val attendanceMap: LiveData<MutableMap<String, Boolean>> = _attendanceMap

    private val _todayAttendance = MutableLiveData<Attendance?>()
    val todayAttendance: LiveData<Attendance?> = _todayAttendance

    // Stats for a single student
    private val _studentStats = MutableLiveData<Pair<Int, Int>>() // presents, absents
    val studentStats: LiveData<Pair<Int, Int>> = _studentStats

    // Add this new LiveData for per-student personal stats
    private val _personalStats = MutableLiveData<Pair<Int, Int>>(0 to 0)
    val personalStats: LiveData<Pair<Int, Int>> = _personalStats

    sealed class AttendanceUiState {
        object Idle : AttendanceUiState()
        object Loading : AttendanceUiState()
        data class Success(val message: String? = null) : AttendanceUiState()
        data class Error(val message: String) : AttendanceUiState()
        object AlreadyTaken : AttendanceUiState()
        object NoStudents : AttendanceUiState()
    }

    /**
     * Load attendance stats for a specific student based on their assigned route.
     * Logic: routeId -> find supervisor -> fetch all attendance from that supervisor -> calculate stats.
     */
    fun loadStudentAttendanceStats(forceRefresh: Boolean = false,busId: String, studentKey: String) {
        viewModelScope.launch {
            _attendanceState.value = AttendanceUiState.Loading
            val result = repository.getStudentAttendanceStats(forceRefresh,busId , studentKey)
            
            if (result.isSuccess) {
                _studentStats.value = result.getOrNull() ?: Pair(0, 0)
                _attendanceState.value = AttendanceUiState.Success("Stats loaded")
            } else {
                _attendanceState.value = AttendanceUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load attendance stats"
                )
            }
        }
    }

    /**
     * Load personal attendance stats for the current student
     * Updates studentStats LiveData with (presents, absents)
     */
    fun loadPersonalAttendanceStats(
        forceRefresh: Boolean = false,
        busId: String,
        studentKey: String   // roll_no or any key used in attendance map
    ) {
        viewModelScope.launch {
            _attendanceState.value = AttendanceUiState.Loading

            val result = repository.getStudentPersonalAttendanceStats(forceRefresh,studentKey, busId)
            if (result.isSuccess) {
                _studentStats.value = result.getOrNull()!!
                _attendanceState.value = AttendanceUiState.Idle
            } else {
                _attendanceState.value = AttendanceUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to load personal stats"
                )
            }
        }
    }
    /**
     * Full supervisor attendance flow:
     * 1. Check if today's attendance already taken for this bus
     * 2. If yes  → emit AlreadyTaken with the existing record
     * 3. If no   → load students by routeId and prepare fresh attendance map
     */
    fun loadAttendanceForSupervisor(forceRefresh: Boolean = false,routeId: String, busId: String) {
        viewModelScope.launch {
            _attendanceState.value = AttendanceUiState.Loading

            // Step 1 — check today's attendance
            val todayResult = repository.getTodayAttendance(forceRefresh,busId)
            if (todayResult.isSuccess) {
                val existing = todayResult.getOrNull()
                if (existing != null) {
                    _todayAttendance.value = existing
                    // Rebuild student list display from the saved map keys
                    _attendanceMap.value   = existing.students?.toMutableMap() ?: mutableMapOf()
                    _attendanceState.value = AttendanceUiState.AlreadyTaken
                    return@launch
                }
            }

            // Step 2 — load students by routeId
            val studentsResult = repository.getStudentsByRoute(forceRefresh,routeId)
            if (studentsResult.isFailure) {
                _attendanceState.value = AttendanceUiState.Error("Failed to load students")
                return@launch
            }

            val students = studentsResult.getOrNull() ?: emptyList()
            if (students.isEmpty()) {
                _attendanceState.value = AttendanceUiState.NoStudents
                return@launch
            }

            _studentsList.value = students
            // Key = rollNo (used as student identifier in attendance map)
            val initialMap = students.associate { (it.rollNo ?: it.email ?: "") to false }.toMutableMap()
            _attendanceMap.value   = initialMap
            _attendanceState.value = AttendanceUiState.Success("Ready to take attendance")
        }
    }

    /**
     * Load students assigned to supervisor's bus (original function kept for compatibility)
     */
    fun loadAttendanceData(forceRefresh: Boolean = false,busId: String) {
        viewModelScope.launch {
            _attendanceState.value = AttendanceUiState.Loading

            // Step 1: Get students for this bus
            val studentsResult = repository.getStudentsForBus(forceRefresh,busId)
            if (studentsResult.isFailure) {
                _attendanceState.value = AttendanceUiState.Error("Failed to load students")
                return@launch
            }

            val students = studentsResult.getOrNull() ?: emptyList()
            if (students.isEmpty()) {
                _attendanceState.value = AttendanceUiState.NoStudents
                return@launch
            }

            _studentsList.value = students

            // Step 2: Initialize attendance map (all absent by default)
            val initialMap = students.associate { it.rollNo.orEmpty() to false }.toMutableMap()
            _attendanceMap.value = initialMap

            // Step 3: Check if already taken today
            val attendanceResult = repository.getTodayAttendance(forceRefresh,busId)
            if (attendanceResult.isSuccess) {
                val existing = attendanceResult.getOrNull()
                if (existing != null) {
                    _todayAttendance.value = existing
                    _attendanceMap.value = existing.students?.toMutableMap()
                    _attendanceState.value = AttendanceUiState.AlreadyTaken
                    return@launch
                }
            }

            _attendanceState.value = AttendanceUiState.Success("Ready to take attendance")
        }
    }

    /**
     * Toggle a student's presence
     */
    fun toggleStudentPresence(studentId: String, isPresent: Boolean) {
        val currentMap = _attendanceMap.value ?: mutableMapOf()
        currentMap[studentId] = isPresent
        _attendanceMap.value = currentMap
    }

    /**
     * Submit attendance for today
     */
    fun submitAttendance(busId: String, supervisorId: String) {
        viewModelScope.launch {
            _attendanceState.value = AttendanceUiState.Loading

            val currentMap = _attendanceMap.value ?: emptyMap()
            if (currentMap.isEmpty()) {
                _attendanceState.value = AttendanceUiState.Error("No attendance data")
                return@launch
            }

            val result = repository.saveAttendance(
                busId = busId,
                supervisorId = supervisorId,
                studentsAttendance = currentMap
            )

            _attendanceState.value = if (result.isSuccess) {
                AttendanceUiState.Success("Attendance submitted successfully")
            } else {
                AttendanceUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to submit attendance"
                )
            }
        }
    }

    // ────────────────────────────────────────────────
    // Factory
    // ────────────────────────────────────────────────
    class Factory(
        private val repository: AttendanceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AttendanceViewModel::class.java)) {
                return AttendanceViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}