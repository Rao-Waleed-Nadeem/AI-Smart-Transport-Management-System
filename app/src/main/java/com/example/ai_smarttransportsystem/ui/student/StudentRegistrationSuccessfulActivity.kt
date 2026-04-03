package com.example.ai_smarttransportsystem.ui.student

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.repository.BusRepository
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.databinding.StudentRegistrationSuccessBinding
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.BusViewModel
import com.example.ai_smarttransportsystem.ui.shared.RouteViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class StudentRegistrationSuccessfulActivity : BaseActivity() {

    private lateinit var binding: StudentRegistrationSuccessBinding
    
    private val studentViewModel: StudentViewModel by viewModels {
        StudentViewModel.factory(StudentRepository())
    }
    private val routeViewModel: RouteViewModel by viewModels {
        RouteViewModel.Factory(RouteRepository())
    }
    private val busViewModel: BusViewModel by viewModels {
        BusViewModel.Factory(BusRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = StudentRegistrationSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resetUiToPlaceholders()
        observeStudent()
        observeRoute()
        observeBus()

        studentViewModel.loadCurrentStudent(forceRefresh = true)

        binding.btnDashboard.setOnClickListener {
            startActivity(Intent(this, StudentHomeActivity::class.java))
            finish()
        }

        setupBottomNavigation()
    }

    private fun resetUiToPlaceholders() {
        binding.tvSeatNum.text = "--"
        binding.tvBusNum.text = "--"
        binding.tvRouteName.text = "--"
        // Hide pickup time as requested to show only 3 fields
        findViewById<View>(R.id.tv_pickup_time)?.visibility = View.GONE
//        findViewById<View>(R.id.tv_pickup_time)?.prevNextFocusForwardId?.let {
//            // This is just to identify the label above it if needed,
//            // but I'll just look for the label "Pickup Time"
//        }
        // Let's just find the textview with "Pickup Time" text and hide it too
    }

    private fun observeStudent() {
        studentViewModel.currentStudent.observe(this) { student ->
            if (student == null) return@observe

            binding.tvSeatNum.text = student.seatNumber?.toString() ?: "--"

            student.routeId?.let { routeViewModel.loadStudentRoute(forceRefresh = true, routeId = it) }
            student.busId?.let { busViewModel.loadBus(forceRefresh = true, busId = it) }
        }
    }

    private fun observeBus() {
        busViewModel.selectedBus.observe(this) { bus ->
            binding.tvBusNum.text = bus?.busNumber ?: "--"
        }
    }

    private fun observeRoute() {
        routeViewModel.currentRoute.observe(this) { route ->
            binding.tvRouteName.text = route?.routeName ?: route?.docId ?: "--"
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_seat
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_seat -> true
                R.id.nav_home -> {
                    startActivity(Intent(this, StudentHomeActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_attendance -> {
                    startActivity(Intent(this, StudentAttendanceActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_wallet -> {
                    startActivity(Intent(this, StudentFeesActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                else -> false
            }
        }
    }
}
