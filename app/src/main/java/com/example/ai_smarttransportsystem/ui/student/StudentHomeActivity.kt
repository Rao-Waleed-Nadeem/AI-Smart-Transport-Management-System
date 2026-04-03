package com.example.ai_smarttransportsystem.ui.student

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Route
import com.example.ai_smarttransportsystem.data.repository.AttendanceRepository
import com.example.ai_smarttransportsystem.data.repository.BusRepository
import com.example.ai_smarttransportsystem.data.repository.FeeRepository
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.data.repository.TrackingRepository
import com.example.ai_smarttransportsystem.databinding.StudentHomeBinding
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.AttendanceViewModel
import com.example.ai_smarttransportsystem.ui.shared.BusViewModel
import com.example.ai_smarttransportsystem.ui.shared.FeeViewModel
import com.example.ai_smarttransportsystem.ui.shared.RouteMapActivity
import com.example.ai_smarttransportsystem.ui.shared.RouteViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class StudentHomeActivity : BaseActivity() {

    private lateinit var binding: StudentHomeBinding

    private val studentViewModel: StudentViewModel by viewModels {
        StudentViewModel.factory(StudentRepository())
    }
    private val routeViewModel: RouteViewModel by viewModels {
        RouteViewModel.Factory(RouteRepository())
    }
    private val attendanceViewModel: AttendanceViewModel by viewModels {
        AttendanceViewModel.Factory(AttendanceRepository())
    }
    private val feeViewModel: FeeViewModel by viewModels {
        FeeViewModel.Factory(FeeRepository(), StudentRepository())
    }
    private val busViewModel: BusViewModel by viewModels {
        BusViewModel.Factory(BusRepository())
    }

    private var currentRouteId: String? = null
    private var studentBusId: String? = null

    // University coordinates
    private val uniLat = 31.60253
    private val uniLng = 73.03485

    // Cached total route distance (km) for trip-progress computation
    private var totalRouteDistanceKm: Double? = null

    // Supervisor uid — resolved once bus is loaded, used for trip progress polling
    private var supervisorUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Configuration.getInstance().userAgentValue = packageName

        binding = StudentHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resetUiToPlaceholders()
        setupPreviewMap()

        observeStudent()
        observeRoute()
        observeAttendance()
        observeFee()
        observeBus()

        studentViewModel.loadCurrentStudent(forceRefresh = true)
        feeViewModel.loadCurrentFee(forceRefresh = true)

        setupCardClick()
        setupBottomNavigation()
    }

    private fun resetUiToPlaceholders() {
        binding.seatNumber.text           = "--"
        binding.attendance.text           = "--"
        binding.present.text              = "--"
        binding.absent.text               = "--"
        binding.attendancePercentage.text = "--"
        binding.feeStatus.text            = "--"
        binding.fees.text                 = "--"
        binding.route.text                = "--"
        binding.busNumber.text            = "--"
        binding.supervisorName.text       = "--"
        binding.supervisorBus.text        = "--"
        binding.supervisorPhone.text      = "--"
        binding.btnSupervisorRoute.text   = "--"
        binding.tvRouteNameMap.text       = "--"
        binding.tvRouteStopsMap.text      = "--"
        binding.tvRouteDistance.text      = "--"
        binding.tvRouteTime.text          = "--"

        binding.mapLoading.visibility    = View.VISIBLE
        binding.routeMetaRow.visibility  = View.GONE
        binding.routeFooter.visibility   = View.GONE

        // Trip progress — start in "unavailable" state
        setTripProgressUnavailable("Waiting for bus assignment...")
    }

    private fun setupPreviewMap() {
        binding.mapPreview.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapPreview.setMultiTouchControls(false)
        binding.mapPreview.zoomController.setVisibility(
            org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
        )
        binding.mapPreview.controller.setZoom(12.0)
        binding.mapPreview.isClickable = false
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeStudent() {
        studentViewModel.currentStudent.observe(this) { student ->
            if (student == null) {
                resetUiToPlaceholders()
                return@observe
            }

            binding.seatNumber.text = student.seatNumber?.toString() ?: "--"
            binding.feeStatus.text  = student.feeStatus?.uppercase() ?: "--"

            val routeId    = student.routeId
            val studentKey = student.rollNo ?: student.email

            if (!routeId.isNullOrEmpty()) {
                currentRouteId = routeId
                studentBusId   = student.busId
                routeViewModel.loadStudentRoute(forceRefresh = true,routeId=routeId)
                if (studentKey != null && student.busId != null) {
                    attendanceViewModel.loadStudentAttendanceStats(forceRefresh = true, busId = studentBusId.toString(), studentKey)
                }
            } else {
                binding.route.text = "Not Assigned"
                setTripProgressUnavailable("No route assigned yet")
            }

            student.busId?.let { busViewModel.loadBus(forceRefresh = true, busId = it) } ?: run {
                binding.busNumber.text = "Not Assigned"
                setTripProgressUnavailable("No bus assigned yet")
            }
        }
    }

    private fun observeRoute() {
        routeViewModel.currentRoute.observe(this) { route ->
            if (route == null) return@observe

            binding.mapLoading.visibility   = View.GONE
            binding.routeMetaRow.visibility = View.VISIBLE
            binding.routeFooter.visibility  = View.VISIBLE

            binding.tvRouteNameMap.text  = route.routeName ?: "Route"
            binding.tvRouteStopsMap.text = "${route.numStops ?: 0} stops"
            binding.tvRouteDistance.text =
                route.totalDistanceKm?.let { "%.1f km".format(it) } ?: "--"
            binding.tvRouteTime.text =
                route.estimatedTimeHours?.let { "${(it * 60).toInt()} min" } ?: "--"

            binding.route.text           = route.routeName ?: "Route Assigned"
            binding.btnSupervisorRoute.text = "Route ${route.routeName ?: ""}"

            totalRouteDistanceKm = route.totalDistanceKm
            drawPreviewRoute(route)

            // Now that we have the route, try fetching trip progress if supervisor is known
            supervisorUid?.let { fetchAndShowTripProgress(it) }
        }
    }

    private fun observeAttendance() {
        attendanceViewModel.studentStats.observe(this) { stats ->
            val presents = stats.first
            val absents  = stats.second
            val total    = presents + absents

            val percentage = if (total > 0) "${(presents * 100) / total}%" else "--"
            binding.attendance.text           = presents.toString()
            binding.present.text              = presents.toString()
            binding.absent.text               = absents.toString()
            binding.totalDays.text            = total.toString()
            binding.attendancePercentage.text = percentage
        }
    }

    private fun observeFee() {
        feeViewModel.currentFee.observe(this) { fee ->
            if (fee?.paymentStatus?.lowercase() == "paid") {
                binding.fees.text = "PKR 0"
            } else {
                binding.fees.text = fee?.amount?.let { "PKR ${it.toInt()}" } ?: "--"
            }
        }
    }

    private fun observeBus() {
        busViewModel.selectedBus.observe(this) { bus ->
            if (bus == null) {
                binding.busNumber.text      = "--"
                binding.supervisorName.text = "Not Assigned"
                binding.supervisorPhone.text = "--"
                binding.supervisorBus.text  = "--"
                setTripProgressUnavailable("No bus assigned yet")
                return@observe
            }

            binding.busNumber.text = bus.busNumber ?: "--"

            val supId = bus.supervisorId
            if (supId != null) {
                supervisorUid = supId
                // Fetch supervisor user info
                lifecycleScope.launch {
                    try {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val userDoc = db.collection("users").document(supId).get().await()
                        if (userDoc.exists()) {
                            val name    = userDoc.getString("name")    ?: "--"
                            val contact = userDoc.getString("contact") ?: "--"
                            binding.supervisorName.text  = name
                            binding.supervisorPhone.text = contact
                            binding.supervisorBus.text   = "Bus ${bus.busNumber ?: ""} Supervisor"
                            binding.supervisorPhone.setOnClickListener {
                                if (contact != "--") {
                                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contact")))
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }

                // Fetch trip progress (route distance may already be cached)
                fetchAndShowTripProgress(supId)
            } else {
                setTripProgressUnavailable("No supervisor assigned")
            }
        }
    }

    // ── Trip Progress ──────────────────────────────────────────────────────────

    /**
     * Fetches the supervisor's latest tracking location from Firestore,
     * then computes how far the bus has travelled toward the university.
     *
     * Progress = 1 − (distanceBusToUni / totalRouteDistance)
     * clamped to [0, 100].
     *
     * If tracking is unavailable or data is missing, shows a status message instead.
     */
    private fun fetchAndShowTripProgress(supUid: String) {
        lifecycleScope.launch {
            try {
                val trackingResult = TrackingRepository().getLatestLocation(forceRefresh = true,supervisorUid=supUid)
                val tracking       = trackingResult.getOrNull()

                val busLat = tracking?.currentLatitude
                val busLng = tracking?.currentLongitude

                if (busLat == null || busLng == null) {
                    setTripProgressUnavailable("Bus is not sharing location")
                    return@launch
                }

                val routeDistKm = totalRouteDistanceKm
                if (routeDistKm == null || routeDistKm <= 0.0) {
                    setTripProgressUnavailable("Route data loading...")
                    return@launch
                }

                // Haversine distance from bus to university
                val distToUniKm = haversineKm(busLat, busLng, uniLat, uniLng)

                // Progress: bus started ~routeDistKm away and moves towards uni
                // progress = (routeDist - distToUni) / routeDist * 100, clamped 0–100
                val rawProgress = ((routeDistKm - distToUniKm) / routeDistKm * 100)
                    .coerceIn(0.0, 100.0)
                val progress = rawProgress.toInt()

                runOnUiThread {
                    binding.tripProgress.visibility       = View.VISIBLE
                    binding.tvTripProgressStatus.visibility = View.GONE
                    binding.tvTripProgressPct.visibility  = View.VISIBLE

                    binding.tripProgress.progress         = progress
                    binding.tvTripProgressPct.text        = "$progress%"

                    // Badge colour: green when live, grey otherwise
                    binding.btnTripStatusBadge.text = "Live"
                    binding.btnTripStatusBadge.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                }

            } catch (_: Exception) {
                setTripProgressUnavailable("Could not load bus location")
            }
        }
    }

    /** Shows the status-message TextView and hides the progress bar. */
    private fun setTripProgressUnavailable(message: String) {
        binding.tripProgress.visibility         = View.GONE
        binding.tvTripProgressPct.visibility    = View.GONE
        binding.tvTripProgressStatus.visibility = View.VISIBLE
        binding.tvTripProgressStatus.text       = message

        // Badge turns grey when not live
        binding.btnTripStatusBadge.text = "Offline"
        binding.btnTripStatusBadge.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
    }

    /**
     * Haversine formula — returns distance in kilometres between two lat/lng points.
     */
    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r    = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a    = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // ── Map ────────────────────────────────────────────────────────────────────

    private fun drawPreviewRoute(route: Route) {
        binding.mapPreview.overlays.clear()
        val geoPoints = route.geometry?.map { GeoPoint(it.latitude, it.longitude) } ?: emptyList()
        if (geoPoints.isEmpty()) return

        val polyline = Polyline(binding.mapPreview).apply {
            setPoints(geoPoints)
            outlinePaint.color       = Color.parseColor("#1976D2")
            outlinePaint.strokeWidth = 6f
            outlinePaint.alpha       = 200
        }
        binding.mapPreview.overlays.add(polyline)

        binding.mapPreview.overlays.add(Marker(binding.mapPreview).apply {
            position = geoPoints.first()
            icon = buildDot(Color.parseColor("#2E7D32"))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        })

        binding.mapPreview.overlays.add(Marker(binding.mapPreview).apply {
            position = geoPoints.last()
            icon = buildDot(Color.parseColor("#C62828"))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        })

        val bbox = BoundingBox.fromGeoPoints(geoPoints)
        binding.mapPreview.post {
            binding.mapPreview.zoomToBoundingBox(bbox.increaseByScale(1.15f), true, 40)
        }
        binding.mapPreview.invalidate()
    }

    private fun buildDot(colorInt: Int, sizeDp: Int = 28): android.graphics.drawable.Drawable {
        val px  = (resources.displayMetrics.density * sizeDp).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val c   = android.graphics.Canvas(bmp)
        val p   = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = colorInt
        c.drawCircle(px / 2f, px / 2f, px / 2.5f, p)
        p.color       = Color.WHITE
        p.style       = android.graphics.Paint.Style.STROKE
        p.strokeWidth = px / 8f
        c.drawCircle(px / 2f, px / 2f, px / 2.5f, p)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    // ── Card clicks ────────────────────────────────────────────────────────────

    private fun setupCardClick() {
        val openMap = View.OnClickListener {
            val routeId = currentRouteId ?: return@OnClickListener
            startActivity(
                Intent(this, RouteMapActivity::class.java)
                    .putExtra(RouteMapActivity.EXTRA_ROUTE_ID, routeId)
            )
        }
        binding.cardLiveLocation.setOnClickListener(openMap)
        binding.mapTouchBlocker.setOnClickListener(openMap)
        binding.tvViewFullMap.setOnClickListener(openMap)

        binding.btnTrackLiveBus.setOnClickListener {
            checkLiveTrackingAndOpen()
        }
    }

    private fun checkLiveTrackingAndOpen() {
        val btn = binding.btnTrackLiveBus
        btn.isEnabled = false
        btn.text = "Checking..."

        lifecycleScope.launch {
            try {
                val studentResult = StudentRepository().getCurrentStudent(forceRefresh = true)
                val student       = studentResult.getOrNull()
                val busId         = student?.busId
                if (busId == null) {
                    showNotAvailableDialog("No bus is assigned to your account yet.")
                    resetTrackButton(btn); return@launch
                }

                val busDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("buses").document(busId).get().await()
                val supUid = busDoc.getString("supervisor_id")
                if (supUid == null) {
                    showNotAvailableDialog("No supervisor has been assigned to your bus yet.")
                    resetTrackButton(btn); return@launch
                }

                val tracking = TrackingRepository().getLatestLocation(forceRefresh = true,supervisorUid=supUid).getOrNull()
                val isLive   = tracking?.currentLatitude != null && tracking.currentLongitude != null

                if (!isLive) {
                    showNotAvailableDialog("Bus location is not being shared right now.")
                    resetTrackButton(btn); return@launch
                }

                startActivity(
                    Intent(this@StudentHomeActivity, StudentLiveTrackingActivity::class.java)
                        .putExtra("EXTRA_SUPERVISOR_UID", supUid)
                )
                resetTrackButton(btn)
            } catch (_: Exception) {
                showNotAvailableDialog("Could not reach the server.")
                resetTrackButton(btn)
            }
        }
    }

    private fun showNotAvailableDialog(message: String) {
        runOnUiThread {
            MaterialAlertDialogBuilder(this)
                .setTitle("Location Not Available")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun resetTrackButton(btn: MaterialButton) {
        runOnUiThread { btn.isEnabled = true; btn.text = "Track Live Bus" }
    }

    // ── Bottom nav ─────────────────────────────────────────────────────────────

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home       -> true
                R.id.nav_seat       -> { navigateAndFinish(StudentSeatRegistrationActivity::class.java); true }
                R.id.nav_attendance -> { navigateAndFinish(StudentAttendanceActivity::class.java); true }
                R.id.nav_wallet     -> { navigateAndFinish(StudentFeesActivity::class.java); true }
                else -> false
            }
        }
    }

    private fun navigateAndFinish(clazz: Class<*>) {
        startActivity(Intent(this, clazz))
        overridePendingTransition(0, 0)
        finish()
    }

    override fun onResume() { super.onResume(); binding.mapPreview.onResume() }
    override fun onPause()  { super.onPause();  binding.mapPreview.onPause()  }
}
