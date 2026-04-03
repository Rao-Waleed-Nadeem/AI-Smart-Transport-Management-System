package com.example.ai_smarttransportsystem.ui.admin

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.repository.AttendanceRepository
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class AdminAttendanceActivity : BaseActivity() {

    private val studentRepo = StudentRepository()
    private val attendanceRepo = AttendanceRepository()
    private val routeRepo = RouteRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.admin_attendance)

        loadAttendanceData()
        setupBottomNavigation()
    }

    private fun loadAttendanceData() {
        // Initial state with placeholders
        findViewById<TextView>(R.id.total_students).text = "--"
        findViewById<TextView>(R.id.present_today).text = "--"
        findViewById<TextView>(R.id.absent_today).text = "--"
        findViewById<TextView>(R.id.total_routes).text = "--"
        
        // Summary section placeholders
        findViewById<TextView>(R.id.total_students_summary).text = "--"
        findViewById<TextView>(R.id.profit_margin_summary).text = "--"
        findViewById<TextView>(R.id.fuel_per_student_summary).text = "--"
        findViewById<TextView>(R.id.profit_per_student_summary).text = "--"

        lifecycleScope.launch {
            // 1. Get all students and all routes
            val studentsResult = studentRepo.getAllStudents(forceRefresh = true)
            val routesResult = routeRepo.getAllRoutes(forceRefresh = true)

            val allStudents = studentsResult.getOrNull() ?: emptyList()
            val allRoutes = routesResult.getOrNull() ?: emptyList()

            // Update Total Students (Top card and summary)
            val studentCountStr = if (allStudents.isNotEmpty()) allStudents.size.toString() else "0"
            findViewById<TextView>(R.id.total_students).text = studentCountStr
            findViewById<TextView>(R.id.total_students_summary).text = studentCountStr

            // Update Total Routes
            if (allRoutes.isNotEmpty()) {
                findViewById<TextView>(R.id.total_routes).text = allRoutes.size.toString()
            } else {
                findViewById<TextView>(R.id.total_routes).text = "--"
            }

            val tableLayout = findViewById<TableLayout>(R.id.attendance_table)
            
            // Clear existing test rows (except header and its divider)
            val childCount = tableLayout.childCount
            if (childCount > 2) {
                tableLayout.removeViews(2, childCount - 2)
            }

            var totalPresent = 0
            var totalAbsent = 0
            var anyAttendanceData = false

            // 2. Process each route
            allRoutes.forEachIndexed { index, route ->
                val busId = route.busId ?: return@forEachIndexed
                
                // Fetch today's attendance for this bus
                val attendanceResult = attendanceRepo.getTodayAttendance(forceRefresh = true, busId = busId)
                val todayAttendance = attendanceResult.getOrNull()
                
                if (todayAttendance != null) anyAttendanceData = true
                
                // Students assigned to this route
                val studentsOnRoute = allStudents.filter { it.routeId == route.docId }
                val totalOnRoute = studentsOnRoute.size
                
                // Count presents/absents from the attendance map
                val presentCount = todayAttendance?.students?.values?.count { it } ?: 0
                val absentCount = if (todayAttendance != null) totalOnRoute - presentCount else 0
                
                totalPresent += presentCount
                totalAbsent += absentCount

                // Add row to table
                addRouteRow(
                    tableLayout,
                    route.routeName ?: "Route ${route.docId}",
                    totalOnRoute,
                    presentCount,
                    absentCount,
                    index % 2 != 0 // Alternate background color
                )
            }

            // Update Present/Absent top cards based on whether any data was found
            if (anyAttendanceData) {
                findViewById<TextView>(R.id.present_today).text = totalPresent.toString()
                findViewById<TextView>(R.id.absent_today).text = totalAbsent.toString()
            } else {
                findViewById<TextView>(R.id.present_today).text = "--"
                findViewById<TextView>(R.id.absent_today).text = "--"
            }
        }
    }

    private fun addRouteRow(
        table: TableLayout,
        routeName: String,
        total: Int,
        present: Int,
        absent: Int,
        isAlternate: Boolean
    ) {
        val row = TableRow(this).apply {
            setPadding(0, 0, 0, 0)
            setBackgroundColor(if (isAlternate) Color.parseColor("#FAFAFA") else Color.WHITE)
        }

        row.addView(createTableCell(routeName))
        row.addView(createTableCell(total.toString()))
        row.addView(createTableCell(present.toString()))
        row.addView(createTableCell(absent.toString()))
        
        // Use placeholders for other fields
        row.addView(createTableCell("--")) // Distance placeholder
        row.addView(createTableCell("--")) // Fuel placeholder
        row.addView(createTableCell("--")) // Profit placeholder

        table.addView(row)
    }

    private fun createTableCell(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.parseColor("#444444"))
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.admin_bottom_nav)
        bottomNav.selectedItemId = R.id.nav_attendance

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, AdminDashboardActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_tracking -> {
                    startActivity(Intent(this, AdminTrackingActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_fees -> {
                    startActivity(Intent(this, AdminFeesActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_attendance -> true
                else -> false
            }
        }
    }
}
