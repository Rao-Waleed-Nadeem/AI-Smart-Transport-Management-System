package com.example.ai_smarttransportsystem.ui.admin

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Route
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.RouteViewModel
import com.example.ai_smarttransportsystem.ui.student.StudentViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import org.w3c.dom.Text

class AdminTrackingActivity : BaseActivity(), OnMapReadyCallback {

    private val routeViewModel: RouteViewModel by viewModels {
        RouteViewModel.Factory(RouteRepository())
    }
    private val studentViewModel: StudentViewModel by viewModels {
        StudentViewModel.factory(StudentRepository())
    }

    private var googleMap: GoogleMap? = null
    private var pendingRoutes: List<Route>? = null

    private val ROUTE_COLORS = listOf(
        Color.parseColor("#1565C0"), Color.parseColor("#B71C1C"),
        Color.parseColor("#1B5E20"), Color.parseColor("#E65100"),
        Color.parseColor("#4A148C"), Color.parseColor("#006064"),
        Color.parseColor("#880E4F"), Color.parseColor("#33691E"),
    )
    private val DEPOT_LAT = 31.60253
    private val DEPOT_LNG = 73.03485



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.admin_tracking)

        setupPreviewMap()
        observeRoutes()
        routeViewModel.fetchRoutesWithBus(forceRefresh = true)

        observeCounts()
        loadAllCounts()

        setupBottomNavigation()

        // ── Tap either the map card → full map ──
        val openFull = View.OnClickListener {
            startActivity(Intent(this, AdminAllRoutesMapActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.maps_card)?.setOnClickListener(openFull)
        findViewById<View>(R.id.map_container)?.setOnClickListener(openFull)
    }

    // ── Preview map (polylines only, non-interactive) ─────────────────────────

    private fun setupPreviewMap() {
        val frag = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.map_container, frag).commit()
        frag.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isScrollGesturesEnabled = false
        map.uiSettings.isZoomGesturesEnabled   = false
        map.uiSettings.isZoomControlsEnabled   = false
        map.uiSettings.isMapToolbarEnabled     = false
        map.mapType = GoogleMap.MAP_TYPE_NORMAL

        // Make the map itself clickable for navigation
        map.setOnMapClickListener {
            startActivity(Intent(this, AdminAllRoutesMapActivity::class.java))
        }

        pendingRoutes?.let { drawPreview(it) }
    }

    private fun observeRoutes() {
        routeViewModel.routeState.observe(this) { state ->
            val show = state is RouteViewModel.RouteUiState.Loading
            findViewById<View>(R.id.progress_map).visibility = if (show) View.VISIBLE else View.GONE
        }

        routeViewModel.assignedRoutes.observe(this) { routes ->
            if (routes.isEmpty()) {
                findViewById<View>(R.id.layout_empty).visibility = View.VISIBLE
                return@observe
            }
            findViewById<View>(R.id.layout_empty).visibility = View.GONE

            // Summary
            val totalStudents = routes.sumOf { it.totalStudents ?: 0 }
            val totalDist     = routes.sumOf { it.totalDistanceKm ?: 0.0 }
            val totalTimeMin  = routes.sumOf { (it.estimatedTimeHours ?: 0.0) * 60 }.toInt()
            findViewById<TextView>(R.id.tv_total_routes).text   = routes.size.toString()
            findViewById<TextView>(R.id.tv_total_students).text = totalStudents.toString()
            findViewById<TextView>(R.id.tv_total_distance).text = "${"%.0f".format(totalDist)} km"
            findViewById<TextView>(R.id.tv_total_time).text     = "Total est. $totalTimeMin min"

            buildLegend(routes)
            if (googleMap != null) drawPreview(routes) else pendingRoutes = routes
        }
    }

    private fun observeCounts() {
        // Total Students count


        // Active Routes count
        routeViewModel.allRoutes.observe(this) { routes ->
            findViewById<TextView>(R.id.tv_total_routes).text = routes.size.toString()
            findViewById<TextView>(R.id.tv_total_distance).text =routes.sumOf { it.totalDistanceKm ?: 0.0 }.toString()
            findViewById<TextView>(R.id.tv_total_students).text = routes.sumOf { it.totalStudents ?: 0 }.toString()
        }
    }

    private fun loadAllCounts() {
        studentViewModel.loadAllStudents(forceRefresh = true)
        routeViewModel.fetchAllRoutes(forceRefresh = true)

    }

    private fun drawPreview(routes: List<Route>) {
        val map = googleMap ?: return
        map.clear()
        val bounds = LatLngBounds.Builder(); var has = false
        routes.forEachIndexed { i, route ->
            val pts = route.geometry?.map { LatLng(it.latitude, it.longitude) } ?: return@forEachIndexed
            if (pts.isEmpty()) return@forEachIndexed
            map.addPolyline(PolylineOptions().addAll(pts)
                .color(ROUTE_COLORS[i % ROUTE_COLORS.size]).width(7f))
            pts.forEach { bounds.include(it); has = true }
        }
        if (has) map.setOnMapLoadedCallback {
            try { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 40)) }
            catch (_: Exception) { map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(DEPOT_LAT, DEPOT_LNG), 11f)) }
        }
    }

    private fun buildLegend(routes: List<Route>) {
        val container = findViewById<LinearLayout>(R.id.container_route_legend)
        container.removeAllViews()
        val dp = resources.displayMetrics.density
        routes.forEachIndexed { i, route ->
            val color = ROUTE_COLORS[i % ROUTE_COLORS.size]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = (10*dp).toInt() }
            }
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams((14*dp).toInt(), (14*dp).toInt())
                    .also { it.marginEnd = (10*dp).toInt() }
                setBackgroundColor(color)
            })
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this).apply {
                text = route.routeName ?: "Route ${i+1}"; textSize = 13f
                setTextColor(Color.parseColor("#1F1F1F")); setTypeface(typeface, Typeface.BOLD)
            })
            col.addView(TextView(this).apply {
                val d = route.totalDistanceKm?.let { "${"%.1f".format(it)} km" } ?: "—"
                val t = route.estimatedTimeHours?.let { "${(it*60).toInt()} min" } ?: "—"
                val s = route.totalStudents?.let { "$it students" } ?: "—"
                text = "$d · $t · $s"; textSize = 11f; setTextColor(Color.parseColor("#888888"))
            })
            row.addView(col)
            row.addView(TextView(this).apply {
                text = "${route.numStops ?: "?"} stops"; textSize = 11f
                setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE)
                setPadding((8*dp).toInt(), (3*dp).toInt(), (8*dp).toInt(), (3*dp).toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginStart = (8*dp).toInt() }
                setBackgroundColor(color)
            })
            container.addView(row)
            if (i < routes.lastIndex) container.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    .also { it.bottomMargin = (10*dp).toInt() }
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            })
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.admin_bottom_nav)
        bottomNav.selectedItemId = R.id.nav_tracking
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard  -> { startActivity(Intent(this, AdminDashboardActivity::class.java)); overridePendingTransition(0,0); finish(); true }
                R.id.nav_tracking   -> true
                R.id.nav_fees       -> { startActivity(Intent(this, AdminFeesActivity::class.java)); overridePendingTransition(0,0); finish(); true }
                R.id.nav_attendance -> { startActivity(Intent(this, AdminAttendanceActivity::class.java)); overridePendingTransition(0,0); finish(); true }
                else -> false
            }
        }
    }
}
