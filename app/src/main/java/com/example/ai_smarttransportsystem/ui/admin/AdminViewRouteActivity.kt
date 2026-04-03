package com.example.ai_smarttransportsystem.ui.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.databinding.AdminViewRouteBinding
import com.example.ai_smarttransportsystem.ui.shared.RouteViewModel

class AdminViewRouteActivity : AppCompatActivity() {

    private lateinit var binding: AdminViewRouteBinding

    // Received from AdminManageRoutesActivity
    private var routeId: String? = null
    private var optimizedOrder: ArrayList<String>? = null

    private val routeViewModel: RouteViewModel by viewModels {
        RouteViewModel.Factory(RouteRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = AdminViewRouteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Read intent extras (both sent by AdminManageRoutesActivity) ────────
        routeId       = intent.getStringExtra("route_id")
        optimizedOrder = intent.getStringArrayListExtra("optimized_order")

        if (routeId == null) {
            Toast.makeText(this, "Route data missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        else{
            Toast.makeText(this, routeId, Toast.LENGTH_SHORT).show()
        }

        supportActionBar?.title = "View Route Details"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Load route data so currentRoute LiveData is populated for the stop button
        routeViewModel.loadBusRoute(forceRefresh = true, busId = routeId!!)

        routeViewModel.routeState.observe(this) { state ->
            when (state) {
                is RouteViewModel.RouteUiState.Error ->
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                else -> {}
            }
        }

        // ── Assign Bus button ─────────────────────────────────────────────────
        // Passes the route_id to AdminAssignBusActivity (same key used there)
        binding.btnAssignBus.setOnClickListener {
            startActivity(
                Intent(this, AdminAssignBusActivity::class.java).apply {
                    putExtra("route_id", routeId)           // key AdminAssignBusActivity reads
                }
            )
        }

        // ── View Stop Details button ──────────────────────────────────────────
        // Passes route_id + optimized_order to AdminViewStopDetailsActivity
        binding.btnViewStops.setOnClickListener {
            // Prefer freshly-loaded route data; fall back to what was passed in the intent
            val order = routeViewModel.currentRoute.value?.optimizedOrder
                ?: optimizedOrder

            startActivity(
                Intent(this, AdminViewStopDetailsActivity::class.java).apply {
                    putExtra("route_id", routeId)
                    if (order != null) {
                        putStringArrayListExtra("optimized_order", ArrayList(order))
                    }
                }
            )
        }

        setupBottomNavigation()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun setupBottomNavigation() {
        binding.adminBottomNav.selectedItemId = R.id.nav_dashboard
        binding.adminBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { startActivity(Intent(this, AdminDashboardActivity::class.java)); finish(); true }
                R.id.nav_tracking  -> { startActivity(Intent(this, AdminTrackingActivity::class.java));  finish(); true }
                R.id.nav_fees      -> { startActivity(Intent(this, AdminFeesActivity::class.java));      finish(); true }
                R.id.nav_attendance-> { startActivity(Intent(this, AdminAttendanceActivity::class.java));finish(); true }
                else -> false
            }
        }
    }
}