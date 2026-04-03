package com.example.ai_smarttransportsystem.ui.student

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ai_smarttransportsystem.MapStopSelectionActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.databinding.StudentSeatRegistrationBinding
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity

class StudentSeatRegistrationActivity : BaseActivity() {

    private lateinit var binding: StudentSeatRegistrationBinding

    private val viewModel: StudentViewModel by viewModels {
        StudentViewModel.factory(StudentRepository())
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = StudentSeatRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide form initially - show only progress
        hideFormShowProgress()

        setupObservers()

        // Start checking immediately
        viewModel.loadCurrentStudent(forceRefresh = true)
    }

    private fun hideFormShowProgress() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSelectLocation.visibility = View.GONE
        binding.etRoll.visibility = View.GONE
        binding.etPhone.visibility = View.GONE
        binding.tvSelectedLocation.visibility = View.GONE
        binding.btnRegister.visibility = View.GONE
        binding.tvName.visibility = View.GONE
        // Hide any other form elements if present
    }

    private fun showForm() {
        binding.progressBar.visibility = View.GONE
        binding.btnSelectLocation.visibility = View.VISIBLE
        binding.etRoll.visibility = View.VISIBLE
        binding.etPhone.visibility = View.VISIBLE
        binding.tvSelectedLocation.visibility = View.VISIBLE
        binding.btnRegister.visibility = View.VISIBLE
        binding.tvName.visibility = View.VISIBLE
    }

    private fun setupObservers() {
        viewModel.currentStudent.observe(this) { student ->
            if (student == null) {
                // Still loading or error
                return@observe
            }

            // Check if already registered (has seat or route)
            if (!student.seatNumber.isNullOrEmpty() || !student.routeId.isNullOrEmpty()) {
                // Already registered → go to success screen
                goToSuccessScreen()
            } else {
                // Not registered → show registration form
                showForm()
                setupRegistrationForm()
            }
        }

        viewModel.studentState.observe(this) { state ->
            when (state) {
                is StudentViewModel.StudentUiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is StudentViewModel.StudentUiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    goToSuccessScreen()
                }
                is StudentViewModel.StudentUiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    showForm()   // fallback to form in case of error
                }
                else -> {}
            }
        }
    }

    private fun setupRegistrationForm() {
        binding.btnSelectLocation.setOnClickListener {
            val intent = Intent(this, MapStopSelectionActivity::class.java)
            mapSelectionLauncher.launch(intent)
        }

        binding.btnRegister.setOnClickListener {
            val roll = binding.etRoll.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()

            if (roll.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please enter roll number and phone", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedLat == null || selectedLng == null) {
                Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.registerStudent(
                rollNo = roll,
                semester = "Spring 2026",
                stopId = ""
            )
        }
    }

    private fun goToSuccessScreen() {
        startActivity(Intent(this, StudentRegistrationSuccessfulActivity::class.java))
        finish()
    }

    // Keep your existing map launcher and other code
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null

    private val mapSelectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val lat = result.data?.getDoubleExtra("LAT", 0.0) ?: 0.0
            val lng = result.data?.getDoubleExtra("LNG", 0.0) ?: 0.0
            if (lat != 0.0 && lng != 0.0) {
                selectedLat = lat
                selectedLng = lng
                binding.tvSelectedLocation.text = "Location Selected: $lat, $lng"
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_seat
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_seat -> true
                R.id.nav_home -> { startActivity(Intent(this, StudentHomeActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                R.id.nav_attendance -> { startActivity(Intent(this, StudentAttendanceActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                R.id.nav_wallet -> { startActivity(Intent(this, StudentFeesActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                else -> false
            }
        }
    }
}