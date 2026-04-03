package com.example.ai_smarttransportsystem.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.google.firebase.auth.FirebaseAuth

abstract class BaseActivity : AppCompatActivity() {

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val profileImage = findViewById<ImageView?>(R.id.profileIcon)
        setupLogout(profileImage)
    }

    /**
     * Call this in every activity after setContentView()
     */
    protected fun setupLogout(profileImage: ImageView?) {
        profileImage?.setOnClickListener {
            showLogoutDialog()
        }
    }

    /**
     * Confirmation dialog before logout
     */
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setCancelable(true)
            .setPositiveButton("Yes") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Actual logout logic
     */
    private fun performLogout() {

        // 1. Firebase logout
        FirebaseAuth.getInstance().signOut()

        // 2. Clear local session (SharedPreferences)
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().clear().apply()

        // 3. Navigate to Login and clear back stack
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)

        // 4. Finish current activity
        finish()
    }
}