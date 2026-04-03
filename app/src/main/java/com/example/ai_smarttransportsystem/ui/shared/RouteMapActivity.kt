package com.example.ai_smarttransportsystem.ui.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Route
import com.example.ai_smarttransportsystem.data.model.Stop
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.StopRepository
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class RouteMapActivity : AppCompatActivity() {

    private lateinit var mapFull: MapView

    private val routeViewModel: RouteViewModel by viewModels {
        RouteViewModel.Factory(RouteRepository())
    }
    private val stopViewModel: StopViewModel by viewModels {
        StopViewModel.Factory(StopRepository())
    }

    // University depot — fallback if geometry is empty
    private val DEPOT_LAT = 31.60253
    private val DEPOT_LNG = 73.03485

    companion object {
        const val EXTRA_ROUTE_ID = "route_id"
        const val EXTRA_BUS_ID   = "bus_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.route_map)

        mapFull = findViewById(R.id.map_full)
        setupMap()
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        observeRoute()
        observeStops()

        val routeId = intent.getStringExtra(EXTRA_ROUTE_ID)
        val busId   = intent.getStringExtra(EXTRA_BUS_ID)
        when {
            routeId != null -> routeViewModel.loadStudentRoute(forceRefresh = true,routeId=routeId)
            busId   != null -> routeViewModel.loadBusRoute(forceRefresh = true,busId=busId)
            else -> { Toast.makeText(this, "No route info", Toast.LENGTH_SHORT).show(); finish() }
        }
    }

    // ── Map base setup ─────────────────────────────────────────────────────────

    private fun setupMap() {
        mapFull.setTileSource(TileSourceFactory.MAPNIK)
        mapFull.setMultiTouchControls(true)
        mapFull.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapFull.controller.setZoom(13.0)
        mapFull.isTilesScaledToDpi = true
    }

    // ── Observe route ──────────────────────────────────────────────────────────

    private fun observeRoute() {
        routeViewModel.routeState.observe(this) { state ->
            when (state) {
                is RouteViewModel.RouteUiState.Loading         -> setLoading(true)
                is RouteViewModel.RouteUiState.Error           -> { setLoading(false); Toast.makeText(this, state.message, Toast.LENGTH_LONG).show() }
                is RouteViewModel.RouteUiState.NoRouteAssigned -> { setLoading(false); Toast.makeText(this, "No route assigned", Toast.LENGTH_SHORT).show() }
                else -> {}
            }
        }

        routeViewModel.currentRoute.observe(this) { route ->
            if (route == null) return@observe
            drawPolyline(route)
            updateInfoCard(route)

            // Fetch actual stop documents in optimized order
            val orderedIds = route.optimizedOrder ?: route.stopIds
            if (!orderedIds.isNullOrEmpty()) {
                stopViewModel.loadStopsByIds(forceRefresh = true,orderedIds)
            } else {
                // No stop IDs at all — just place geometry endpoints
                placeGeometryEndpoints(route)
                setLoading(false)
            }
        }
    }

    // ── Observe stops ──────────────────────────────────────────────────────────

    private fun observeStops() {
        stopViewModel.stopsState.observe(this) { state ->
            if (state is StopViewModel.StopUiState.Error || state is StopViewModel.StopUiState.Empty) {
                setLoading(false)
                routeViewModel.currentRoute.value?.let { placeGeometryEndpoints(it) }
            }
        }

        stopViewModel.orderedStops.observe(this) { orderedStops ->
            if (orderedStops.isEmpty()) return@observe
            setLoading(false)
            placeStopMarkers(orderedStops)
        }
    }

    // ── Polyline from route geometry ───────────────────────────────────────────

    private fun drawPolyline(route: Route) {
        mapFull.overlays.removeAll { it is Polyline }

        val geoPoints = route.geometry?.map { GeoPoint(it.latitude, it.longitude) } ?: emptyList()
        if (geoPoints.isEmpty()) return

        mapFull.overlays.add(0, Polyline(mapFull).apply {
            setPoints(geoPoints)
            outlinePaint.color       = Color.parseColor("#1565C0")
            outlinePaint.strokeWidth = 10f
            outlinePaint.isAntiAlias = true
            outlinePaint.alpha       = 230
        })

        val bbox = BoundingBox.fromGeoPoints(geoPoints)
        mapFull.post { mapFull.zoomToBoundingBox(bbox.increaseByScale(1.2f), true, 80) }
        mapFull.invalidate()
    }

    // ── Place stop markers ─────────────────────────────────────────────────────
    //
    //  KEY FIX:
    //  optimized_order contains ONLY pickup stops — the university (depot) is
    //  never a stop document. Python appends it to the geometry as the last
    //  GeoPoint but never writes it to the stops collection.
    //
    //  So:
    //  • index 0               → green pin  (first pickup)
    //  • index 1..lastIndex    → blue pin   (all other pickups — including the last one)
    //  • university destination → red pin   (taken from route.geometry.last(), NOT from orderedStops)

    private fun placeStopMarkers(orderedStops: List<Pair<String, Stop>>) {
        mapFull.overlays.removeAll { it is Marker }

        // All pickup stops from Firestore — green for first, blue for the rest
        orderedStops.forEachIndexed { index, (_, stop) ->
            val lat = stop.latitude  ?: return@forEachIndexed
            val lng = stop.longitude ?: return@forEachIndexed

            mapFull.overlays.add(Marker(mapFull).apply {
                position = GeoPoint(lat, lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                if (index == 0) {
                    icon    = makePinDrawable(Color.parseColor("#2E7D32"), isLarge = true)
                    title   = "Start: ${stop.stopName ?: "First Stop"}"
                    snippet = buildSnippet(stop, index + 1)
                } else {
                    icon    = makePinDrawable(Color.parseColor("#1565C0"), isLarge = false)
                    title   = stop.stopName ?: "Stop ${index + 1}"
                    snippet = buildSnippet(stop, index + 1)
                }
            })
        }

        // Red destination marker — ALWAYS the last point of route.geometry
        // This is the university/depot, not a stop in optimized_order
        val route = routeViewModel.currentRoute.value
        val destGeo = route?.geometry?.lastOrNull()
        val destPoint = if (destGeo != null)
            GeoPoint(destGeo.latitude, destGeo.longitude)
        else
            GeoPoint(DEPOT_LAT, DEPOT_LNG)

        mapFull.overlays.add(Marker(mapFull).apply {
            position = destPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon    = makePinDrawable(Color.parseColor("#C62828"), isLarge = true)
            title   = "Destination: University"
            snippet = "Final stop · ${route?.routeName ?: ""}"
        })

        mapFull.invalidate()
    }

    // Fallback when no stop IDs exist — just geometry first/last points
    private fun placeGeometryEndpoints(route: Route) {
        mapFull.overlays.removeAll { it is Marker }
        val geoPoints = route.geometry?.map { GeoPoint(it.latitude, it.longitude) } ?: return

        if (geoPoints.isNotEmpty()) {
            mapFull.overlays.add(Marker(mapFull).apply {
                position = geoPoints.first()
                title    = "Start"
                icon     = makePinDrawable(Color.parseColor("#2E7D32"), isLarge = true)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
        }
        mapFull.overlays.add(Marker(mapFull).apply {
            position = if (geoPoints.isNotEmpty()) geoPoints.last() else GeoPoint(DEPOT_LAT, DEPOT_LNG)
            title    = "Destination: University"
            icon     = makePinDrawable(Color.parseColor("#C62828"), isLarge = true)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        })
        mapFull.invalidate()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildSnippet(stop: Stop, orderNum: Int): String {
        val parts = mutableListOf("Stop #$orderNum")
        stop.studentCount?.let         { if (it > 0) parts.add("$it students") }
        stop.distanceToUniversityKm?.let { parts.add("${"%.1f".format(it)} km to uni") }
        stop.feePerStudentPkr?.let     { if (it > 0) parts.add("PKR ${"%.0f".format(it)}/student") }
        return parts.joinToString(" · ")
    }

    private fun updateInfoCard(route: Route) {
        val name  = route.routeName ?: "Route"
        val dist  = route.totalDistanceKm?.let { "${"%.1f".format(it)} km" } ?: "—"
        val time  = route.estimatedTimeHours?.let { "${(it * 60).toInt()} min" } ?: "—"
        val stops = route.numStops?.toString() ?: "—"

        findViewById<TextView>(R.id.tv_route_title).text     = name
        findViewById<TextView>(R.id.tv_stop_count).text      = "${route.numStops ?: "?"} stops"
        findViewById<TextView>(R.id.tv_route_name_full).text = name
        findViewById<TextView>(R.id.tv_info_distance).text   = dist
        findViewById<TextView>(R.id.tv_info_time).text       = time
        findViewById<TextView>(R.id.tv_info_stops).text      = stops
    }

    // ── Teardrop pin marker (proper map-style pin, no PNG needed) ─────────────

    private fun makePinDrawable(fillColor: Int, isLarge: Boolean): Drawable {
        val dp     = resources.displayMetrics.density
        val pinW   = ((if (isLarge) 36 else 28) * dp).toInt()
        val pinH   = ((if (isLarge) 52 else 40) * dp).toInt()
        val radius = pinW / 2f
        val cx     = pinW / 2f
        val cy     = radius

        val bmp    = Bitmap.createBitmap(pinW, pinH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

        // Shadow
        paint.color = Color.argb(40, 0, 0, 0)
        canvas.drawPath(buildPinPath(cx + 1f, cy + 1f, radius - 1f, pinH.toFloat() - 1f), paint)

        // Body fill
        paint.color = fillColor
        paint.style = Paint.Style.FILL
        val path = buildPinPath(cx, cy, radius - 2f, pinH.toFloat())
        canvas.drawPath(path, paint)

        // White border
        paint.color       = Color.WHITE
        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * dp
        canvas.drawPath(path, paint)

        // Inner white dot
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius * 0.32f, paint)

        return BitmapDrawable(resources, bmp)
    }

    private fun buildPinPath(cx: Float, cy: Float, r: Float, totalH: Float): Path {
        val path = Path()
        path.moveTo(cx - r * 0.6f, cy + r * 0.8f)
        path.arcTo(cx - r, cy - r, cx + r, cy + r, 150f, 240f, false)
        path.lineTo(cx + r * 0.6f, cy + r * 0.8f)
        path.lineTo(cx, totalH - 2f)
        path.close()
        return path
    }

    private fun setLoading(loading: Boolean) {
        findViewById<View>(R.id.loading_overlay).visibility =
            if (loading) View.VISIBLE else View.GONE
    }

    override fun onResume() { super.onResume(); mapFull.onResume() }
    override fun onPause()  { super.onPause();  mapFull.onPause() }
}