package com.example.ai_smarttransportsystem.ui.supervisor

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class SupervisorRouteDetails: BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.supervisor_route_details)

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.supervisor_bottom_nav)
        bottomNav.selectedItemId = R.id.nav_route  // Highlight Home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, SupervisorDashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }

                R.id.nav_route -> true

                R.id.nav_attendance -> {
                    startActivity(Intent(this, SupervisorAttendanceActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_location -> {
                    startActivity(Intent(this, SupervisorTrackingActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }

                else -> false
            }
        }
    }
}