package com.example.ai_smarttransportsystem.ui.student

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.repository.AttendanceRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.AttendanceViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class StudentAttendanceActivity : BaseActivity() {

    private val studentViewModel: StudentViewModel by viewModels {
        StudentViewModel.factory(StudentRepository())
    }
    
    private val attendanceViewModel: AttendanceViewModel by viewModels {
        AttendanceViewModel.Factory(AttendanceRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.student_attendance)

//        resetUiToPlaceholders()
        observeStudent()
        observeAttendance()
        setupBottomNavigation()
        
        studentViewModel.loadCurrentStudent(forceRefresh = true)
    }

    private fun resetUiToPlaceholders() {
        findViewById<TextView>(R.id.present).text = "--"
        findViewById<TextView>(R.id.absent).text = "--"
        findViewById<TextView>(R.id.attendance_percentage).text = "--"
    }

    private fun observeStudent() {
        studentViewModel.currentStudent.observe(this) { student ->
            if (student == null) {
                resetUiToPlaceholders()
                return@observe
            }

            val busId = student.busId
            val studentKey = student.rollNo ?: student.email
            
            if (busId != null && studentKey != null) {
                attendanceViewModel.loadStudentAttendanceStats(forceRefresh = true,busId=busId, studentKey)
            }
            else {
                // Reset attendance if no key found
                findViewById<TextView>(R.id.present).text = "--"
                findViewById<TextView>(R.id.absent).text = "--"
                findViewById<TextView>(R.id.attendance_percentage).text = "--"
            }
        }
    }

    private fun observeAttendance() {
        attendanceViewModel.studentStats.observe(this) { stats ->

            // 1. If whole object is null
            if (stats == null) {
                setAttendancePlaceholders()
                return@observe
            }

            // 2. Safe extraction
            val presents = stats.first ?: 0
            val absents = stats.second ?: 0

            // 3. Validate values (optional but good)
            if (presents < 0 || absents < 0) {
                setAttendancePlaceholders()
                return@observe
            }

            val total = presents + absents

            // 4. If no valid data
            if (total == 0) {
                setAttendancePlaceholders()
                return@observe
            }

            // 5. Safe calculation
            val percentageText = "${(presents * 100) / total}%"

            // 6. Set values
            findViewById<TextView>(R.id.present).text = presents.toString()
            findViewById<TextView>(R.id.absent).text = absents.toString()
            findViewById<TextView>(R.id.attendance_percentage).text = percentageText
        }
    }

    private fun setAttendancePlaceholders() {
        findViewById<TextView>(R.id.present).text = "--"
        findViewById<TextView>(R.id.absent).text = "--"
        findViewById<TextView>(R.id.attendance_percentage).text = "--"
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.nav_attendance

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_attendance -> true

                R.id.nav_home -> {
                    startActivity(Intent(this, StudentHomeActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }

                R.id.nav_seat -> {
                    startActivity(Intent(this, StudentSeatRegistrationActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }

                R.id.nav_wallet -> {
                    startActivity(Intent(this, StudentFeesActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }

                else -> false
            }
        }
    }
}
