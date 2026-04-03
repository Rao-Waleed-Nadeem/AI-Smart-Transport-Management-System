package com.example.ai_smarttransportsystem.ui.supervisor

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.repository.SupervisorRepository
import com.example.ai_smarttransportsystem.service.LocationTrackingService
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SupervisorTrackingActivity : BaseActivity() {

    private var isSharing = false
    private var pulseAnimator: ObjectAnimator? = null

    // ── Step 1: Request fine + coarse location only ────────────────────────────
    // POST_NOTIFICATIONS is requested separately after location is granted.
    // ACCESS_BACKGROUND_LOCATION must NEVER be bundled with foreground perms.
    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine   = grants[Manifest.permission.ACCESS_FINE_LOCATION]   == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        when {
            fine || coarse -> {
                // Location granted — now ask for notification perm on Android 13+
                requestNotificationPermission()
            }
            // Permanently denied: shouldShowRationale returns false after denial
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                showPermanentDenialDialog()
            }
            else -> {
                // Denied but can ask again — show rationale
                showLocationRationaleDialog()
            }
        }
    }

    // ── Step 2: Notification permission (Android 13+ only) ────────────────────
    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notification is optional — proceed to start tracking regardless
        startTracking()
    }

    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.supervisor_tracking_control)

        loadAssignmentInfo()

        // Permission warning card taps → open system App Settings directly
        // (Only shown when permanently denied; tapping re-opens settings)
        findViewById<MaterialCardView>(R.id.card_permission_warning).setOnClickListener {
            openAppSettings()
        }

        findViewById<MaterialButton>(R.id.btn_toggle_tracking).setOnClickListener {
            if (isSharing) stopTracking() else checkPermissionAndStart()
        }

        setupBottomNavigation()
    }

    // ── Load supervisor assignment info ────────────────────────────────────────

    private fun loadAssignmentInfo() {
        lifecycleScope.launch {
            val result = SupervisorRepository().getCurrentSupervisor()
            val sup    = result.getOrNull()
            val isAssigned = sup?.assignedBus != null && sup.assignedRoute != null

            runOnUiThread {
                findViewById<TextView>(R.id.tv_bus_info).text   =
                    "Bus: ${sup?.assignedBus   ?: "Not assigned"}"
                findViewById<TextView>(R.id.tv_route_info).text =
                    "Route: ${sup?.assignedRoute ?: "Not assigned"}"

                if (!isAssigned) {
                    val btn = findViewById<MaterialButton>(R.id.btn_toggle_tracking)
                    btn.isEnabled = false
                    btn.text      = "Bus or route not assigned"
                    btn.alpha     = 0.5f
                }
            }
        }
    }

    // ── Permission entry point ─────────────────────────────────────────────────

    private fun checkPermissionAndStart() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        when {
            fineGranted || coarseGranted -> {
                // Already have location — just handle notification then start
                requestNotificationPermission()
            }
            // Show rationale if the OS says it's appropriate
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationRationaleDialog()
            }
            else -> {
                // First time asking, or rationale not needed — request directly
                requestLocationPermission()
            }
        }
    }

    // ── Actual permission requests ─────────────────────────────────────────────

    private fun requestLocationPermission() {
        locationPermLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            // ✅ Do NOT include POST_NOTIFICATIONS or ACCESS_BACKGROUND_LOCATION here.
            // Android treats mixed permission groups unpredictably and may deny all.
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!notifGranted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // Notification already granted or not needed (< Android 13) — start now
        startTracking()
    }

    // ── Rationale & denial dialogs ─────────────────────────────────────────────

    /**
     * Shown when the user denied once — explains WHY the app needs location,
     * then re-launches the system permission dialog on "Allow".
     */
    private fun showLocationRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Needed")
            .setMessage(
                "This app shares your bus location with students in real-time so they " +
                        "know when the bus will arrive.\n\n" +
                        "Please allow location access to start sharing."
            )
            .setPositiveButton("Allow") { _, _ -> requestLocationPermission() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Shown when the user has permanently denied (tapped "Don't ask again").
     * The OS will no longer show the dialog — we must send them to Settings.
     */
    private fun showPermanentDenialDialog() {
        // Also make the warning card visible so there's a persistent tap-to-fix hint
        findViewById<MaterialCardView>(R.id.card_permission_warning).visibility = View.VISIBLE

        AlertDialog.Builder(this)
            .setTitle("Permission Permanently Denied")
            .setMessage(
                "Location permission has been permanently denied.\n\n" +
                        "To enable it:\n" +
                        "1. Tap \"Open Settings\" below\n" +
                        "2. Go to Permissions → Location\n" +
                        "3. Select \"Allow only while using the app\" or \"Allow all the time\"\n" +
                        "4. Return here and try again."
            )
            .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Opens this app's system permission settings page directly.
     */
    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    // ── Start / stop tracking ──────────────────────────────────────────────────

    private fun startTracking() {
        // Hide any previous warning
        findViewById<MaterialCardView>(R.id.card_permission_warning).visibility = View.GONE

        val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isSharing = true
        updateUI()
        Toast.makeText(this, "Location sharing started", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(serviceIntent)
        isSharing = false
        updateUI()
        Toast.makeText(this, "Location sharing stopped", Toast.LENGTH_SHORT).show()
    }

    // ── UI updates ─────────────────────────────────────────────────────────────

    private fun updateUI() {
        val btn       = findViewById<MaterialButton>(R.id.btn_toggle_tracking)
        val statusLbl = findViewById<TextView>(R.id.tv_status_label)
        val statusSub = findViewById<TextView>(R.id.tv_status_sub)
        val lastUpd   = findViewById<TextView>(R.id.tv_last_update)
        val dot       = findViewById<View>(R.id.view_status_dot)
        val ring      = findViewById<View>(R.id.view_pulse_ring)

        if (isSharing) {
            btn.text = "Stop Sharing Location"
            btn.setBackgroundColor(Color.parseColor("#C62828"))
            statusLbl.text = "Sharing Location"
            statusLbl.setTextColor(Color.parseColor("#2E7D32"))
            statusSub.text = "Students can see your bus location in real-time"
            dot.setBackgroundColor(Color.parseColor("#4caf50"))
            ring.visibility = View.VISIBLE
            startPulseAnimation(ring)
            lastUpd.visibility = View.VISIBLE
            lastUpd.text = "Started at ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())}"
        } else {
            btn.text = "Start Sharing Location"
            btn.setBackgroundColor(Color.parseColor("#4caf50"))
            statusLbl.text = "Not Sharing"
            statusLbl.setTextColor(Color.parseColor("#1F1F1F"))
            statusSub.text = "Students cannot see your location"
            dot.setBackgroundColor(Color.parseColor("#CCCCCC"))
            ring.visibility = View.GONE
            stopPulseAnimation()
            lastUpd.visibility = View.GONE
        }
    }

    private fun startPulseAnimation(view: View) {
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.4f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.4f),
            PropertyValuesHolder.ofFloat(View.ALPHA,   0.6f, 0f)
        ).apply {
            duration    = 1200
            repeatCount = ObjectAnimator.INFINITE
            repeatMode  = ObjectAnimator.RESTART
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPulseAnimation()
    }

    // ── Bottom nav ─────────────────────────────────────────────────────────────

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.supervisor_bottom_nav)
        bottomNav.selectedItemId = R.id.nav_location
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_location -> true
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, SupervisorDashboardActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_route -> {
                    startActivity(Intent(this, SupervisorRouteDetails::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_attendance -> {
                    startActivity(Intent(this, SupervisorAttendanceActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                else -> false
            }
        }
    }
}