package com.example.ai_smarttransportsystem

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.ui.admin.AdminDashboardActivity
import com.example.ai_smarttransportsystem.ui.auth.LoginActivity
import com.example.ai_smarttransportsystem.ui.student.StudentHomeActivity
import com.example.ai_smarttransportsystem.ui.supervisor.SupervisorDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        val firestore = Firebase.firestore
//        firestore.collection("test").document("test_doc").set(mapOf("test" to "working"))
//            .addOnSuccessListener {
//                Toast.makeText(this, "Firebase working", Toast.LENGTH_SHORT).show()
//            }
//            .addOnFailureListener { e ->
//                Toast.makeText(this, "Firebase error: ${e.message}", Toast.LENGTH_SHORT).show()
//            }
//
//        // Show login screen first
//        if (savedInstanceState == null) {
//            startActivity(Intent(this, LoginActivity::class.java))
//            finish()   // Important: close MainActivity so user can't go back to empty screen
//        }

        // If not logged in → go to login
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            // Already logged in → go to role-based dashboard (later)
            checkUserRoleAndRedirect()
        }
        finish()  // Close MainActivity
    }

    private fun checkUserRoleAndRedirect() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance().collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role") ?: "unknown"

                when (role) {
                    "student" -> startActivity(Intent(this, StudentHomeActivity::class.java))
                    "supervisor" -> startActivity(Intent(this, SupervisorDashboardActivity::class.java))
                    "admin" -> startActivity(Intent(this, AdminDashboardActivity::class.java))
                    else -> startActivity(Intent(this, LoginActivity::class.java))
                }
                finish()
            }
            .addOnFailureListener {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Logout when this activity is destroyed (app closed or killed)
        FirebaseAuth.getInstance().signOut()
        Log.d("Auth", "Auto-logout on app close")
    }
}