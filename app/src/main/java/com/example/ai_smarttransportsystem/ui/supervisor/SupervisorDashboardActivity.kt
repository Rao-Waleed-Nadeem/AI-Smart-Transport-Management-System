package com.example.ai_smarttransportsystem.ui.supervisor

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlin.random.Random
import androidx.lifecycle.lifecycleScope
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Route
import com.example.ai_smarttransportsystem.data.model.Student
import com.example.ai_smarttransportsystem.data.repository.AttendanceRepository
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.data.repository.SupervisorRepository
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.AttendanceViewModel
import com.example.ai_smarttransportsystem.ui.student.StudentViewModel
import com.example.ai_smarttransportsystem.ui.shared.RouteViewModel
import com.example.ai_smarttransportsystem.ui.shared.RouteMapActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class SupervisorDashboardActivity : BaseActivity() {

    private val routeViewModel: RouteViewModel by viewModels {
        RouteViewModel.Factory(RouteRepository())
    }

    private val supervisorViewModel: SupervisorViewModel by viewModels {
        SupervisorViewModel.Factory(SupervisorRepository())
    }

    private val attendanceViewModel: AttendanceViewModel by viewModels {
        AttendanceViewModel.Factory(AttendanceRepository())
    }

    private val studentViewModel: StudentViewModel by viewModels {
        StudentViewModel.factory(StudentRepository())
    }

    private lateinit var mapPreview: MapView

    private lateinit var studentsListContainer: LinearLayout
    private var currentRouteId: String? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.supervisor_dashboard)

        studentsListContainer = findViewById(R.id.students_list_container)

        mapPreview = findViewById(R.id.map_preview)
        setupPreviewMap()

        // Initialize UI with placeholders to override XML test values
        initializePlaceholders()

        observeRoute()
        observeAttendance()
        observeStudentsList()




        loadSupervisorData()
        setupCardClick()
        setupBottomNavigation()
    }

    private fun initializePlaceholders() {
        // Route Card
        findViewById<TextView>(R.id.tv_bus_route_title).text = "No Route Assigned"
        findViewById<TextView>(R.id.tv_bus_route_sub).text   = "Bus: -- | Route: --"

        // Attendance Cards
        findViewById<TextView>(R.id.total_students).text     = "--"
        findViewById<TextView>(R.id.presents).text           = "--"
        findViewById<TextView>(R.id.absents).text            = "--"

        // Status Card
        findViewById<TextView>(R.id.present_status).text     = "--\nPresent"
        findViewById<TextView>(R.id.absent_status).text      = "--\nAbsent"
        findViewById<TextView>(R.id.attendance_percentage).text = "--\nRate"

        // Map Preview Labels
        findViewById<TextView>(R.id.tv_route_name_map).text  = "--"
        findViewById<TextView>(R.id.tv_route_stops_map).text = "-- stops"
        findViewById<TextView>(R.id.tv_route_distance).text  = "--"
        findViewById<TextView>(R.id.tv_route_time).text      = "--"
    }

    // ── Load supervisor → get busId/routeId ───────────────────────────────────

    private fun loadSupervisorData() {
        lifecycleScope.launch {
            val result = SupervisorRepository().getCurrentSupervisor(forceRefresh = true)
            if (result.isFailure || result.getOrNull() == null) {
                Toast.makeText(
                    this@SupervisorDashboardActivity,
                    "No assignment found. Contact admin.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val supervisor = result.getOrNull()!!
            val routeId = supervisor.assignedRoute
            val busId   = supervisor.assignedBus

            studentViewModel.loadStudentsByRoute(forceRefresh = true,routeId.toString())  // Load students for the route (if routeId is null, it will load empty list and show "No students allocated")

            // Update route card header with bus info
            findViewById<TextView>(R.id.tv_bus_route_sub).text =
                "Bus: ${busId ?: "--"} | Route: ${routeId ?: "--"}"

            if (routeId == null) {
                Toast.makeText(
                    this@SupervisorDashboardActivity,
                    "Route not assigned yet.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            currentRouteId = routeId

            // Load route geometry
            routeViewModel.loadStudentRoute(forceRefresh = true,routeId)
            
            // Load attendance data
            if (busId != null) {
                attendanceViewModel.loadAttendanceForSupervisor(forceRefresh = true,routeId, busId)
            }
        }
    }

    // ── OSMDroid preview map setup ─────────────────────────────────────────────

    private fun setupPreviewMap() {
        mapPreview.setTileSource(TileSourceFactory.MAPNIK)
        mapPreview.setMultiTouchControls(false)
        mapPreview.zoomController.setVisibility(
            org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
        )
        mapPreview.controller.setZoom(12.0)
        mapPreview.isClickable = false
    }

    // ── Observe route ──────────────────────────────────────────────────────────

    private fun observeRoute() {
        routeViewModel.routeState.observe(this) { state ->
            when (state) {
                is RouteViewModel.RouteUiState.Error,
                is RouteViewModel.RouteUiState.NoRouteAssigned -> {
                    findViewById<View>(R.id.map_loading).visibility = View.GONE
                }
                else -> {}
            }
        }

        routeViewModel.currentRoute.observe(this) { route ->
            if (route == null) {
                findViewById<TextView>(R.id.tv_bus_route_title).text = "No Route Assigned"
                return@observe
            }

            // Hide loading shimmer, show meta + footer
            findViewById<View>(R.id.map_loading).visibility   = View.GONE
            findViewById<View>(R.id.route_meta_row).visibility = View.VISIBLE
            findViewById<View>(R.id.route_footer).visibility   = View.VISIBLE

            // Shows total students in route
            findViewById<TextView>(R.id.total_students).text = route.totalStudents?.toString() ?: "--"



            // Update route card title
            val routeName = route.routeName ?: "Route ${route.busId ?: "--"}"
            findViewById<TextView>(R.id.tv_bus_route_title).text = routeName
            findViewById<TextView>(R.id.tv_route_name_map).text  = routeName
            findViewById<TextView>(R.id.tv_route_stops_map).text =
                "${route.numStops ?: "--"} stops"
            findViewById<TextView>(R.id.tv_route_distance).text  =
                route.totalDistanceKm?.let { "${"%.1f".format(it)} km" } ?: "--"
            findViewById<TextView>(R.id.tv_route_time).text      =
                route.estimatedTimeHours?.let { "${(it * 60).toInt()} min" } ?: "--"

            drawPreviewRoute(route)
        }
    }

    // ── Observe Attendance ─────────────────────────────────────────────────────

    private fun observeAttendance() {

        attendanceViewModel.todayAttendance.observe(this) { attendance ->
            if (attendance != null && attendance.students != null) {
                val presentCount = attendance.students.values.count { it }
                val absentCount = attendance.students.values.count { !it }
                val totalCount = attendance.students.size
                val rate = if (totalCount > 0) (presentCount * 100 / totalCount) else 0

                findViewById<TextView>(R.id.presents).text = presentCount.toString()
                findViewById<TextView>(R.id.absents).text = absentCount.toString()

                // Update Today's Attendance Status Card
                findViewById<TextView>(R.id.present_status).text = "$presentCount\nPresent"
                findViewById<TextView>(R.id.absent_status).text = "$absentCount\nAbsent"
                findViewById<TextView>(R.id.attendance_percentage).text = "$rate%\nRate"
            } else {
                findViewById<TextView>(R.id.presents).text = "--"
                findViewById<TextView>(R.id.absents).text = "--"

                findViewById<TextView>(R.id.present_status).text = "--\nPresent"
                findViewById<TextView>(R.id.absent_status).text = "--\nAbsent"
                findViewById<TextView>(R.id.attendance_percentage).text = "--\nRate"
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun observeStudentsList() {
        studentViewModel.studentsList.observe(this) { students ->
            studentsListContainer.removeAllViews()

            if (students.isNullOrEmpty()) {
                val emptyTv = TextView(this).apply {
                    text = "No students allocated yet"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@SupervisorDashboardActivity, android.R.color.darker_gray))
                    setPadding(0, 40, 0, 40)
                    gravity = Gravity.CENTER
                }
                studentsListContainer.addView(emptyTv)
                return@observe
            }

            students.forEach { student ->
                val row = createStudentRow(student)
                studentsListContainer.addView(row)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createStudentRow(student: Student): View {
        val dp = resources.displayMetrics.density

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
        }

        // Random colored circle background
        val colors = arrayOf("#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#F44336", "#3F51B5")
        val randomColor = colors[Random.nextInt(colors.size)]

        // Avatar using your baseline_person_24 drawable
        val avatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((42 * dp).toInt(), (42 * dp).toInt()).apply {
                marginEnd = (12 * dp).toInt()
            }
            setImageResource(R.drawable.baseline_person_24)   // Your existing drawable
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(android.graphics.Color.WHITE)     // White icon
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor(randomColor))
                setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            }
        }

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameTv = TextView(this).apply {
            text = student.name ?: "Unknown Student"
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#1F1F1F"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val detailTv = TextView(this).apply {
            text = "Roll: ${student.rollNo ?: "—"}   •   Seat: ${student.seatNumber ?: "—"}"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#666666"))
        }

        infoLayout.addView(nameTv)
        infoLayout.addView(detailTv)

        row.addView(avatar)
        row.addView(infoLayout)

        return row
    }

    // ── Draw route on preview map ──────────────────────────────────────────────
    private fun drawPreviewRoute(route: Route) {
        mapPreview.overlays.clear()

        val geoPoints = route.geometry?.map { gp ->
            GeoPoint(gp.latitude, gp.longitude)
        } ?: emptyList()

        if (geoPoints.isEmpty()) return

        // Green polyline — matches supervisor theme
        mapPreview.overlays.add(Polyline(mapPreview).apply {
            setPoints(geoPoints)
            outlinePaint.color       = Color.parseColor("#4caf50")
            outlinePaint.strokeWidth = 6f
            outlinePaint.isAntiAlias = true
            outlinePaint.alpha       = 200
        })

        // Start marker — dark green
        mapPreview.overlays.add(Marker(mapPreview).apply {
            position = geoPoints.first()
            title    = "Start"
            icon     = buildDot(Color.parseColor("#1B5E20"), 28)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        })

        // End marker — red
        mapPreview.overlays.add(Marker(mapPreview).apply {
            position = geoPoints.last()
            title    = "University"
            icon     = buildDot(Color.parseColor("#C62828"), 28)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        })

        val bbox = BoundingBox.fromGeoPoints(geoPoints)
        mapPreview.post {
            mapPreview.zoomToBoundingBox(bbox.increaseByScale(1.15f), true, 40)
        }
        mapPreview.invalidate()
    }

    private fun buildDot(colorInt: Int, sizeDp: Int): android.graphics.drawable.Drawable {
        val px   = (resources.displayMetrics.density * sizeDp).toInt()
        val bmp  = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val c    = android.graphics.Canvas(bmp)
        val p    = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color  = colorInt
        c.drawCircle(px / 2f, px / 2f, px / 2.5f, p)
        p.color       = Color.WHITE
        p.style       = android.graphics.Paint.Style.STROKE
        p.strokeWidth = px / 8f
        c.drawCircle(px / 2f, px / 2f, px / 2.5f, p)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    // ── Card click — open full RouteMapActivity ────────────────────────────────

    private fun setupCardClick() {
        val openMap = View.OnClickListener {
            val routeId = currentRouteId ?: return@OnClickListener
            startActivity(
                Intent(this, RouteMapActivity::class.java)
                    .putExtra(RouteMapActivity.EXTRA_ROUTE_ID, routeId)
            )
        }
        findViewById<View>(R.id.card_live_location).setOnClickListener(openMap)
        findViewById<View>(R.id.map_touch_blocker).setOnClickListener(openMap)
        findViewById<View>(R.id.tv_view_full_map).setOnClickListener(openMap)
    }

    // ── OSMDroid lifecycle ─────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        mapPreview.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapPreview.onPause()
    }

    // ── Bottom nav ─────────────────────────────────────────────────────────────

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.supervisor_bottom_nav)
        bottomNav.selectedItemId = R.id.nav_dashboard

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true

                R.id.nav_route -> {
                    startActivity(Intent(this, SupervisorRouteDetails::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }

                R.id.nav_attendance -> {
                    startActivity(Intent(this, SupervisorAttendanceActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
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
