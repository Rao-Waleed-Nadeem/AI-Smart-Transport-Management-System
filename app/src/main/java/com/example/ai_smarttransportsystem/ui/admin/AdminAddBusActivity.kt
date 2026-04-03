package com.example.ai_smarttransportsystem.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.repository.BusRepository
import com.example.ai_smarttransportsystem.databinding.AdminAddBusBinding
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.BusViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class AdminAddBusActivity : BaseActivity() {

    private lateinit var binding: AdminAddBusBinding

    private val busViewModel: BusViewModel by viewModels {
        BusViewModel.Factory(BusRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = AdminAddBusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStatusDropdown()
        binding.btnBack.setOnClickListener { finish() }

        // Observe ViewModel state
        busViewModel.busState.observe(this) { state ->
            when (state) {
                is BusViewModel.BusUiState.Loading -> setLoading(true)
                is BusViewModel.BusUiState.Success -> {
                    setLoading(false)
                    Toast.makeText(this, state.message ?: "Bus saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is BusViewModel.BusUiState.Error -> {
                    setLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> setLoading(false)
            }
        }

        binding.btnSaveBus.setOnClickListener {
            handleSaveBus()
        }

        setupBottomNavigation()
    }

    private fun setupStatusDropdown() {
        val statuses = listOf("Active", "Inactive", "Under Maintenance")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statuses)
        binding.acvStatus.setAdapter(adapter)
        binding.acvStatus.setText("Active", false)
    }

    private fun handleSaveBus() {
        val busNumber   = binding.etBusNumber.text.toString().trim()
        val plateNumber = binding.etPlateNumber.text.toString().trim()
        val capacityStr = binding.etCapacity.text.toString().trim()
        val status      = binding.acvStatus.text.toString().trim()

        if (busNumber.isEmpty()) {
            binding.etBusNumber.error = "Required"
            return
        }
        if (plateNumber.isEmpty()) {
            binding.etPlateNumber.error = "Required"
            return
        }
        if (capacityStr.isEmpty()) {
            binding.etCapacity.error = "Required"
            return
        }

        val capacity = capacityStr.toIntOrNull() ?: 40

        // Passing the 4 required parameters to ViewModel
        busViewModel.addNewBus(
            busNumber = busNumber,
            plateNumber = plateNumber,
            capacity = capacity,
            status = status
        )
    }

    private fun setLoading(loading: Boolean) {
        binding.progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSaveBus.isEnabled = !loading
        binding.btnSaveBus.text = if (loading) "Saving…" else "Save Bus"
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.admin_bottom_nav)
        bottomNav.selectedItemId = R.id.nav_dashboard

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_tracking -> {
                    startActivity(Intent(this, AdminTrackingActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_fees -> {
                    startActivity(Intent(this, AdminFeesActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_attendance -> {
                    startActivity(Intent(this, AdminAttendanceActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
