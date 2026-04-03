package com.example.ai_smarttransportsystem.ui.admin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Route
import com.example.ai_smarttransportsystem.data.model.Stop
import com.example.ai_smarttransportsystem.data.repository.BusRepository
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.StopRepository
import com.example.ai_smarttransportsystem.ui.shared.RouteViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch

class AdminAllRoutesMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private val routeViewModel: RouteViewModel by viewModels {
        RouteViewModel.Factory(RouteRepository())
    }

    private var googleMap: GoogleMap? = null
    private var pendingRoutes: List<Route>? = null
    
    // Map to store bus document ID -> bus number
    private val busNumberMap = mutableMapOf<String, String>()

    private val ROUTE_COLORS = listOf(
        Color.parseColor("#1565C0"), Color.parseColor("#B71C1C"),
        Color.parseColor("#1B5E20"), Color.parseColor("#E65100"),
        Color.parseColor("#4A148C"), Color.parseColor("#006064"),
        Color.parseColor("#880E4F"), Color.parseColor("#33691E"),
    )
    private val COLOR_START = Color.parseColor("#2E7D32")
    private val COLOR_STOP  = Color.parseColor("#1565C0")
    private val COLOR_DEST  = Color.parseColor("#C62828")
    private val DEPOT_LAT   = 31.60253
    private val DEPOT_LNG   = 73.03485

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.admin_all_routes_map)

        (supportFragmentManager.findFragmentById(R.id.map_full) as SupportMapFragment)
            .getMapAsync(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        observeRoutes()
        fetchBusData()
        routeViewModel.fetchRoutesWithBus(forceRefresh = true)
    }

    private fun fetchBusData() {
        lifecycleScope.launch {
            val result = BusRepository().getAllBusesWithIds(forceRefresh = true)
            if (result.isSuccess) {
                result.getOrNull()?.forEach { (id, bus) ->
                    bus.busNumber?.let { busNumberMap[id] = it }
                }
                // If routes already loaded, refresh legend
                routeViewModel.assignedRoutes.value?.let { buildLegend(it) }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.apply {
            isZoomControlsEnabled   = false
            isZoomGesturesEnabled   = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled   = false
            isMapToolbarEnabled     = false
        }
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        pendingRoutes?.let { drawAll(it) }
    }

    private fun observeRoutes() {
        routeViewModel.routeState.observe(this) { state ->
            val loading = state is RouteViewModel.RouteUiState.Loading
            findViewById<View>(R.id.progress_loading).visibility =
                if (loading) View.VISIBLE else View.GONE
        }

        routeViewModel.assignedRoutes.observe(this) { routes ->
            if (routes.isEmpty()) return@observe

            // Header info
            findViewById<TextView>(R.id.tv_route_count_badge).text = "${routes.size} routes"

            // Summary line
            val totalDist = routes.sumOf { it.totalDistanceKm ?: 0.0 }
            val totalStu  = routes.sumOf { it.totalStudents ?: 0 }
            val totalMin  = routes.sumOf { (it.estimatedTimeHours ?: 0.0) * 60 }.toInt()
            findViewById<TextView>(R.id.tv_summary).text =
                "${"%.0f".format(totalDist)} km · $totalStu students · ~$totalMin min"

            buildLegend(routes)
            if (googleMap != null) drawAll(routes) else pendingRoutes = routes
        }
    }

    // ── Draw all routes on the map ────────────────────────────────────────────

    private fun drawAll(routes: List<Route>) {
        val map = googleMap ?: return
        map.clear()
        val bounds = LatLngBounds.Builder()
        var has = false

        routes.forEachIndexed { index, route ->
            val color = ROUTE_COLORS[index % ROUTE_COLORS.size]

            // Polyline
            val pts = route.geometry?.map { LatLng(it.latitude, it.longitude) } ?: emptyList()
            if (pts.isNotEmpty()) {
                map.addPolyline(PolylineOptions().addAll(pts).color(color).width(9f).zIndex(1f))
                pts.forEach { bounds.include(it); has = true }
            }

            // Destination — geometry last point
            val destPt = pts.lastOrNull() ?: LatLng(DEPOT_LAT, DEPOT_LNG)
            map.addMarker(
                MarkerOptions().position(destPt)
                    .icon(makePin(COLOR_DEST, null, isLarge = true))
                    .title("Destination: University")
                    .snippet(route.routeName ?: "Route ${index + 1}")
                    .zIndex(3f)
            )
            bounds.include(destPt); has = true

            // Stops with numbers
            val ids = route.optimizedOrder ?: route.stopIds
            if (!ids.isNullOrEmpty()) {
                fetchAndDraw(route, index, ids)
            } else {
                pts.firstOrNull()?.let { pt ->
                    map.addMarker(
                        MarkerOptions().position(pt)
                            .icon(makePin(COLOR_START, "S", isLarge = true))
                            .title("Start · ${route.routeName ?: ""}").zIndex(3f)
                    )
                }
            }
        }

        if (has) map.setOnMapLoadedCallback {
            try { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)) }
            catch (_: Exception) { map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(DEPOT_LAT, DEPOT_LNG), 11f)) }
        }
    }

    private fun fetchAndDraw(route: Route, routeIndex: Int, orderedIds: List<String>) {
        lifecycleScope.launch {
            val stopMap = StopRepository().getStopsByIds(forceRefresh = true, stopIds = orderedIds).getOrNull() ?: return@launch
            val ordered = orderedIds.mapNotNull { stopMap[it] }
            runOnUiThread {
                val map = googleMap ?: return@runOnUiThread
                ordered.forEachIndexed { i, stop ->
                    val lat = stop.latitude  ?: return@forEachIndexed
                    val lng = stop.longitude ?: return@forEachIndexed
                    val num = i + 1
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .icon(if (i == 0) makePin(COLOR_START, num.toString(), isLarge = true)
                            else        makePin(COLOR_STOP,  num.toString(), isLarge = false))
                            .title(if (i == 0) "Start: ${stop.stopName ?: "Stop 1"}"
                            else stop.stopName ?: "Stop $num")
                            .snippet(snippet(stop, num))
                            .zIndex(2f)
                    )
                }
            }
        }
    }

    private fun snippet(stop: Stop, num: Int): String {
        val p = mutableListOf("Stop #$num")
        stop.studentCount?.let          { if (it > 0) p.add("$it students") }
        stop.distanceToUniversityKm?.let { p.add("${"%.1f".format(it)} km to uni") }
        stop.feePerStudentPkr?.let      { if (it > 0) p.add("PKR ${"%.0f".format(it)}") }
        return p.joinToString(" · ")
    }

    // ── Bottom-sheet legend ───────────────────────────────────────────────────

    private fun buildLegend(routes: List<Route>) {
        val container = findViewById<LinearLayout>(R.id.container_legend)
        container.removeAllViews()
        val dp = resources.displayMetrics.density

        routes.forEachIndexed { i, route ->
            val color = ROUTE_COLORS[i % ROUTE_COLORS.size]

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (14 * dp).toInt() }
            }

            // Thick vertical colored bar
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams((5 * dp).toInt(), (56 * dp).toInt())
                    .also { it.marginEnd = (12 * dp).toInt() }
                setBackgroundColor(color)
            })

            // Info column
            val col = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Name row with colored dot
            val nameRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = (2 * dp).toInt() }
            }
            nameRow.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams((10 * dp).toInt(), (10 * dp).toInt())
                    .also { it.marginEnd = (6 * dp).toInt() }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            })
            nameRow.addView(TextView(this).apply {
                text = route.routeName ?: "Route ${i + 1}"; textSize = 14f
                setTextColor(Color.parseColor("#1F1F1F")); setTypeface(typeface, Typeface.BOLD)
            })
            col.addView(nameRow)

            // Stats
            col.addView(TextView(this).apply {
                val d = route.totalDistanceKm?.let { "${"%.1f".format(it)} km" } ?: "—"
                val t = route.estimatedTimeHours?.let { "${(it * 60).toInt()} min" } ?: "—"
                val s = route.totalStudents?.let { "$it students" } ?: "—"
                text = "$d  ·  $t  ·  $s"; textSize = 12f
                setTextColor(Color.parseColor("#666666"))
            })

            // Bus Number
            col.addView(TextView(this).apply {
                val busNum = busNumberMap[route.busId] ?: route.busId ?: "—"
                text = "Bus No: $busNum"; textSize = 11f
                setTextColor(Color.parseColor("#AAAAAA"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (2 * dp).toInt() }
            })

            // Stops badge
            row.addView(col)
            row.addView(TextView(this).apply {
                text = "${route.numStops ?: "?"}\nstops"; textSize = 11f
                gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE)
                setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginStart = (8 * dp).toInt() }
                background = GradientDrawable().apply { setColor(color); cornerRadius = 8 * dp }
            })

            container.addView(row)

            if (i < routes.lastIndex) container.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    .also { it.bottomMargin = (14 * dp).toInt() }
                setBackgroundColor(Color.parseColor("#F0F0F0"))
            })
        }
    }

    // ── Teardrop pin ──────────────────────────────────────────────────────────

    private fun makePin(fillColor: Int, label: String?, isLarge: Boolean): BitmapDescriptor {
        val dp   = resources.displayMetrics.density
        val pinW = ((if (isLarge) 40 else 32) * dp).toInt()
        val pinH = ((if (isLarge) 58 else 46) * dp).toInt()
        val r    = pinW / 2f; val cx = r; val cy = r

        val bmp = Bitmap.createBitmap(pinW, pinH, Bitmap.Config.ARGB_8888)
        val c   = Canvas(bmp)
        val p   = Paint(Paint.ANTI_ALIAS_FLAG)

        // Shadow
        p.color = Color.argb(45, 0, 0, 0)
        c.drawPath(buildPinPath(cx + 1.5f, cy + 1.5f, r - 1.5f, pinH.toFloat() - 1.5f), p)

        // Fill
        p.style = Paint.Style.FILL; p.color = fillColor
        val path = buildPinPath(cx, cy, r - 2f, pinH.toFloat())
        c.drawPath(path, p)

        // Border
        p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = 2.5f * dp
        c.drawPath(path, p)

        // Label or dot
        p.style = Paint.Style.FILL; p.color = Color.WHITE
        if (!label.isNullOrEmpty()) {
            p.textSize = (if (isLarge) 13f else 11f) * dp
            p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER
            c.drawText(label, cx, cy - (p.descent() + p.ascent()) / 2f, p)
        } else {
            c.drawCircle(cx, cy, r * 0.30f, p)
        }

        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    private fun buildPinPath(cx: Float, cy: Float, r: Float, h: Float): Path {
        val path = Path()
        path.moveTo(cx - r * 0.6f, cy + r * 0.8f)
        path.arcTo(cx - r, cy - r, cx + r, cy + r, 150f, 240f, false)
        path.lineTo(cx + r * 0.6f, cy + r * 0.8f)
        path.lineTo(cx, h - 2f)
        path.close()
        return path
    }
}