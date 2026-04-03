package com.example.ai_smarttransportsystem.ui.student

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class StudentChallanActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.student_challan)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // ... your form fields, Register button logic here ...

        // Example: Register button clicked → go to challan
        // val registerBtn = findViewById<Button>(R.id.registerBtn)
        // registerBtn.setOnClickListener {
        //     // save to Firebase later
        //     startActivity(Intent(this, StudentChallanActivity::class.java))
        //     finish()
        // }

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.nav_seat   // Seat stays blue

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_seat -> true

                R.id.nav_home -> {
                    startActivity(Intent(this, StudentHomeActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }

                R.id.nav_attendance -> {
                    startActivity(Intent(this, StudentAttendanceActivity::class.java))
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