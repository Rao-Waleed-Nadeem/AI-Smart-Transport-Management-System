package com.example.ai_smarttransportsystem.ui.student

import androidx.activity.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai_smarttransportsystem.data.model.Student
import com.example.ai_smarttransportsystem.data.model.User
import com.example.ai_smarttransportsystem.data.model.Utils
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.data.repository.UtilsRepository
import com.example.ai_smarttransportsystem.ui.shared.UtilsViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class StudentViewModel(
    private val repository: StudentRepository,
    private val utilsRepository: UtilsRepository
) : ViewModel() {

    private val utilsViewModel = UtilsViewModel(utilsRepository)

    private val _studentState = MutableLiveData<StudentUiState>(StudentUiState.Idle)
    val studentState: LiveData<StudentUiState> = _studentState
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val _currentStudent = MutableLiveData<Student?>()
    val currentStudent: LiveData<Student?> = _currentStudent
    private val _userProfile = MutableLiveData<User?>()
    val userProfile: LiveData<User?> = _userProfile

    private val _studentsList = MutableLiveData<List<Student>>()
    val studentsList: LiveData<List<Student>> = _studentsList

    // ── Stop-students screen (admin) ──────────────────────────────────────────
    /** Each entry is (Student, contactPhone) — contact fetched from users collection */
    private val _stopStudentsList = MutableLiveData<List<Pair<Student, String>>>()
    val stopStudentsList: LiveData<List<Pair<Student, String>>> = _stopStudentsList



    sealed class StudentUiState {
        object Idle : StudentUiState()
        object Loading : StudentUiState()
        object Empty : StudentUiState()
        data class Success(val message: String? = null) : StudentUiState()
        data class Error(val message: String) : StudentUiState()
        data class NeedsRegistration(val hasData: Boolean = false) : StudentUiState()
    }

    fun loadUtils() {
        utilsViewModel.loadUtils()
    }

    // Observe utils if needed
    val utils: LiveData<Utils?> = utilsViewModel.utils

    /**
     * Load current student's data and profile when screen opens
     */
    fun loadCurrentStudent(forceRefresh:Boolean=false) {
        viewModelScope.launch {
            _studentState.value = StudentUiState.Loading

            val userResult = repository.getUserProfile(forceRefresh)
            if (userResult.isSuccess) {
                _userProfile.value = userResult.getOrNull()
            }

            val result = repository.getCurrentStudent(forceRefresh)
            when {
                result.isSuccess -> {
                    val student = result.getOrNull()
                    _currentStudent.value = student
                    if (student == null) {
                        _studentState.value = StudentUiState.NeedsRegistration(true)
                    } else {
                        _studentState.value = StudentUiState.Success()
                    }
                }
                else -> {
                    _studentState.value = StudentUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load student data"
                    )
                }
            }
        }
    }

    /** Load ALL students — used by admin dashboard for total count */
    fun loadAllStudents(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _studentState.value = StudentUiState.Loading

            val result = repository.getAllStudents(forceRefresh)
            when {
                result.isSuccess -> {
                    val students = result.getOrNull() ?: emptyList()
                    _studentsList.value = students
                    _studentState.value = if (students.isEmpty())
                        StudentUiState.Empty
                    else
                        StudentUiState.Success()
                }
                else -> {
                    _studentState.value = StudentUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load students"
                    )
                }
            }
        }
    }


    /**
     * Load all students assigned to a specific route (used by Supervisor)
     */
    fun loadStudentsByRoute(forceRefresh: Boolean = false,routeId: String) {
        viewModelScope.launch {
            _studentState.value = StudentUiState.Loading

            val result = repository.getStudentsByRoute(forceRefresh,routeId)

            when {
                result.isSuccess -> {
                    val students = result.getOrNull() ?: emptyList()
                    _studentsList.value = students
                    _studentState.value = if (students.isEmpty())
                        StudentUiState.Empty
                    else
                        StudentUiState.Success()
                }
                else -> {
                    _studentState.value = StudentUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load students for this route"
                    )
                }
            }
        }
    }

    /**
     * Load only the students whose UIDs are in [studentIds] (from Stop.studentIds).
     * For each student, also fetches their contact from the users collection in parallel.
     * Populates _stopStudentsList as List<Pair<Student, contactString>>.
     */
    fun loadStudentsByIds(forceRefresh: Boolean = false,studentIds: List<String>) {
        viewModelScope.launch {
            _studentState.value = StudentUiState.Loading

            if (studentIds.isEmpty()) {
                _stopStudentsList.value = emptyList()
                _studentState.value = StudentUiState.Empty
                return@launch
            }

            val result = repository.getStudentsByIds(forceRefresh,studentIds)
            when {
                result.isFailure -> {
                    _studentState.value = StudentUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load students"
                    )
                }
                else -> {
                    val pairs = result.getOrNull() ?: emptyList()

                    if (pairs.isEmpty()) {
                        _stopStudentsList.value = emptyList()
                        _studentState.value = StudentUiState.Empty
                        return@launch
                    }

                    // Fetch contact for each student uid in parallel
                    val withContact: List<Pair<Student, String>> = pairs.map { (uid, student) ->
                        async {
                            val contact = repository.getUserContact(forceRefresh,uid)
                            Pair(student, contact)
                        }
                    }.awaitAll()
                        .sortedWith(compareBy(nullsLast()) { it.first.seatNumber })

                    _stopStudentsList.value = withContact
                    _studentState.value = StudentUiState.Success()
                }
            }
        }
    }

    /**
     * Register/update student seat data
     */
    fun registerStudent(
        forceRefresh: Boolean = false,
        rollNo: String,
        semester: String,
        stopId: String
    ) {
        viewModelScope.launch {
            _studentState.value = StudentUiState.Loading

            val userResult = repository.getUserProfile(forceRefresh)
            val user = userResult.getOrNull()

            val student = Student(
                name = user?.name,
                email = user?.email ?: auth.currentUser?.email,
                rollNo = rollNo,
                semester = semester,
                stopId = stopId,
                registrationStatus = "pending",
                feeStatus = "unpaid"
            )

            val result = repository.saveStudentData(student)
            if (result.isSuccess) {
                utilsViewModel.toggleRouteOptimization(isOptimized = false)
                _currentStudent.value = student
                _studentState.value = StudentUiState.Success("Registration submitted successfully")
            } else {
                _studentState.value = StudentUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to save registration"
                )
            }
        }
    }

    /**
     * Check if student needs to register seat
     */
    fun checkRegistrationStatus() {
        viewModelScope.launch {
            val result = repository.hasStudentData()
            if (result.isSuccess) {
                val hasData = result.getOrNull() ?: false
                if (!hasData) {
                    _studentState.value = StudentUiState.NeedsRegistration(true)
                }
            } else {
                _studentState.value = StudentUiState.Error("Failed to check registration status")
            }
        }
    }

    companion object {
        fun factory(repository: StudentRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(StudentViewModel::class.java)) {
                        return StudentViewModel(repository, UtilsRepository()) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}