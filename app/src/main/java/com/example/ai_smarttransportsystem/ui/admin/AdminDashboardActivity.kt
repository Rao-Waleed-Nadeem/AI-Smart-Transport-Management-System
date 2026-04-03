package com.example.ai_smarttransportsystem.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.remote.RetrofitClient
import com.example.ai_smarttransportsystem.data.repository.BusRepository
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.data.repository.UserRepository
import com.example.ai_smarttransportsystem.data.repository.UtilsRepository
import com.example.ai_smarttransportsystem.databinding.AdminDashboardBinding
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.BusViewModel
import com.example.ai_smarttransportsystem.ui.shared.RouteViewModel
import com.example.ai_smarttransportsystem.ui.shared.UtilsViewModel
import com.example.ai_smarttransportsystem.ui.student.StudentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class AdminDashboardActivity : BaseActivity() {

    private lateinit var binding: AdminDashboardBinding
    private val userRepo = UserRepository()


    private val studentViewModel: StudentViewModel by viewModels {
        StudentViewModel.factory(StudentRepository())
    }

    private val busViewModel: BusViewModel by viewModels {
        BusViewModel.Factory(BusRepository())
    }

    private val routeViewModel: RouteViewModel by viewModels {
        RouteViewModel.Factory(RouteRepository())
    }

    private val utilsViewModel: UtilsViewModel by viewModels {
        UtilsViewModel.Factory(UtilsRepository())
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = AdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Manage Routes Card Click
        binding.manageRoutes.setOnClickListener {
            startActivity(Intent(this, AdminManageRoutesActivity::class.java))
        }

        // Add New Bus Card Click
        binding.addNewBus.setOnClickListener {
            startActivity(Intent(this, AdminAddBusActivity::class.java))
        }

        binding.assignSupervisor.setOnClickListener {
            startActivity(Intent(this, AdminAssignSupervisorActivity::class.java))
        }

        binding.optimizeRoutes.setOnClickListener {
            triggerRouteOptimization()
        }

        observeCounts()
        loadAllCounts()

        check_route_optimized()
        observeUtilsState()



        setupBottomNavigation()
    }

    private fun observeCounts() {
        // Total Students count
        studentViewModel.studentsList.observe(this) { students ->
            binding.tvTotalStudentsCount.text = students.size.toString()
        }

        // Total Buses count
        busViewModel.busesList.observe(this) { buses ->
            binding.tvTotalBusesCount.text = buses.size.toString()
            val active = buses.count { it.status?.lowercase() == "active" }
            binding.tvActiveBusesLabel.text = "$active Active"
        }

        // Active Routes count
        routeViewModel.allRoutes.observe(this) { routes ->
            binding.tvActiveRoutesCount.text = routes.size.toString()
        }
    }

    private fun loadAllCounts() {
        utilsViewModel.loadUtils()
        studentViewModel.loadAllStudents(forceRefresh = true)
        busViewModel.loadAllBuses(forceRefresh = true)
        routeViewModel.fetchAllRoutes(forceRefresh = true)
        loadSupervisorCount()
    }

    private fun check_route_optimized() {
        utilsViewModel.utils.observe(this) { utils ->
            if (utils == null) return@observe

            val optimizeCard = binding.optimizeRoutes

            if (utils.isOptimized == true) {
                // Disable the card
                optimizeCard.isEnabled = false
                optimizeCard.alpha = 0.6f                    // Makes it look disabled
                optimizeCard.isClickable = false
                optimizeCard.isFocusable = false

                // Update subtitle to show it's already optimized
                binding.thirdsubtitleText.text = "Routes already optimized"
            } else {
                // Enable the card
                optimizeCard.isEnabled = true
                optimizeCard.alpha = 1.0f
                optimizeCard.isClickable = true
                optimizeCard.isFocusable = true
                binding.thirdsubtitleText.text = "Optimze the stops to best routes"
            }
        }
    }

    private fun observeUtilsState() {
        utilsViewModel.utilsState.observe(this) { state ->
            if (state is UtilsViewModel.UtilsUiState.Error) {
                Toast.makeText(this, "DB Update Failed: ${state.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadSupervisorCount() {
        lifecycleScope.launch {
            val result = userRepo.getUsersByRole(forceRefresh = true,role="supervisor")
            val supervisors = result.getOrNull() ?: emptyList()
            binding.tvSupervisorsCount.text = supervisors.size.toString()
        }
    }


    private fun setUiLocked(locked: Boolean) {
        binding.loadingOverlay.visibility = if (locked) View.VISIBLE else View.GONE
    }

    private fun triggerRouteOptimization() {
        setUiLocked(true)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.optimizeRoutes()
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.get("status") == "success") {
                        // Immediate UI update to disable button before DB sync completes
                        binding.optimizeRoutes.isEnabled = false
                        binding.optimizeRoutes.alpha = 0.6f
                        binding.thirdsubtitleText.text = "Routes already optimized"

                        Toast.makeText(this@AdminDashboardActivity, "Waleed!", Toast.LENGTH_LONG).show()
                        utilsViewModel.toggleRouteOptimization(isOptimized = true)

                        val busCount = (body["bus_count"] as? Double)?.toInt() ?: 0
                        Toast.makeText(
                            this@AdminDashboardActivity,
                            "✅ Routes Generated! ($busCount buses)",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        showError("Generation failed: ${body?.get("message")}")
                    }
                } else {
                    showError("Server error: ${response.code()}")
                }
            } catch (e: HttpException) {
                showError("HTTP error: ${e.message()}")
            } catch (e: IOException) {
                showError("Network error: Is the backend running at ${RetrofitClient.BASE_URL}?")
            } catch (e: Exception) {
                showError("Unexpected error: ${e.message}")
            } finally {
                setUiLocked(false)
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setupBottomNavigation() {
        val bottomNav = binding.adminBottomNav
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
