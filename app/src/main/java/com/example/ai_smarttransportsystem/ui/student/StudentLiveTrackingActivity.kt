package com.example.ai_smarttransportsystem.ui.student

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.data.repository.TrackingRepository
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.TrackingViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class StudentLiveTrackingActivity : BaseActivity(), OnMapReadyCallback {

    private val trackingViewModel: TrackingViewModel by viewModels {
        TrackingViewModel.Factory(TrackingRepository())
    }

    private var googleMap: GoogleMap? = null
    private var busMarker: Marker?    = null
    private var supervisorUid: String? = null
    private var firstUpdate = true

    companion object {
        const val EXTRA_SUPERVISOR_UID = "supervisor_uid"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.student_live_tracking)

        (supportFragmentManager.findFragmentById(R.id.map_live) as SupportMapFragment)
            .getMapAsync(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // supervisorUid can be passed directly, or we resolve it from student's bus
        supervisorUid = intent.getStringExtra(EXTRA_SUPERVISOR_UID)

        if (supervisorUid != null) {
            startListening(supervisorUid!!)
        } else {
            resolveAndListen()
        }
    }

    // ── Resolve supervisor UID from student's assigned bus ────────────────────

    private fun resolveAndListen() {
        lifecycleScope.launch {
            // 1. Get student's busId
            val studentResult = StudentRepository().getCurrentStudent(forceRefresh = true)
            val busId = studentResult.getOrNull()?.busId

            if (busId == null) {
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(this@StudentLiveTrackingActivity,
                        "No bus assigned to your account", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // 2. Get supervisor's UID from supervisors collection (doc ID = supervisorUid)
            // The bus document has supervisor_id field set during AdminAssignSupervisor flow
            val db  = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("buses").document(busId).get()
                .addOnSuccessListener { doc ->
                    val supUid = doc.getString("supervisor_id")
                    if (supUid != null) {
                        supervisorUid = supUid
                        startListening(supUid)
                    } else {
                        setLoading(false)
                        Toast.makeText(this@StudentLiveTrackingActivity, "No supervisor assigned to your bus",
                            Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    setLoading(false)
                    Toast.makeText(this@StudentLiveTrackingActivity, "Could not load bus info", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun startListening(uid: String) {
        trackingViewModel.startListening(uid)
        observeTracking()
    }

    // ── Map ready ─────────────────────────────────────────────────────────────

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.apply {
            isZoomControlsEnabled   = false
            isZoomGesturesEnabled   = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            isMapToolbarEnabled     = false
        }
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
    }

    // ── Observe live location ─────────────────────────────────────────────────

    private fun observeTracking() {
        trackingViewModel.trackingState.observe(this) { state ->
            when (state) {
                is TrackingViewModel.TrackingUiState.Sharing -> {
                    setLoading(false)
                    setBusStatus(online = true)
                    showLiveBadge(true)
                }
                is TrackingViewModel.TrackingUiState.Offline -> {
                    setLoading(false)
                    setBusStatus(online = false)
                    showLiveBadge(false)
                }
                is TrackingViewModel.TrackingUiState.Error -> {
                    setLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        trackingViewModel.liveLocation.observe(this) { tracking ->
            val lat = tracking?.currentLatitude  ?: return@observe
            val lng = tracking?.currentLongitude ?: return@observe

            val latLng = LatLng(lat, lng)
            val map    = googleMap ?: return@observe

            // Update or create bus marker
            if (busMarker == null) {
                busMarker = map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .icon(makeBusIcon())
                        .title("Your Bus")
                        .snippet("Live location")
                        .zIndex(10f)
                )
            } else {
                // Smooth marker move (animates on main thread)
                busMarker!!.position = latLng
            }

            // First update → animate camera to bus
            if (firstUpdate) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                firstUpdate = false
            }

            // Update info card
            val coordText = "${"%.5f".format(lat)}, ${"%.5f".format(lng)}"
            findViewById<TextView>(R.id.tv_coordinates).text = coordText
            findViewById<View>(R.id.row_coords).visibility   = View.VISIBLE

            tracking.speed?.let { spd ->
                val kmh = (spd * 3.6).toInt()
                val speedTv = findViewById<TextView>(R.id.tv_speed)
                speedTv.text = "$kmh km/h"
                speedTv.visibility = View.VISIBLE
            }

            val time = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
            findViewById<TextView>(R.id.tv_last_updated).text = "Updated: $time"
        }
    }

    // ── Bus marker — animated bus icon ────────────────────────────────────────
    // Circular blue background with a white bus icon

    private fun makeBusIcon(): BitmapDescriptor {
        val dp   = resources.displayMetrics.density
        val size = (52 * dp).toInt()
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c    = Canvas(bmp)
        val p    = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer shadow ring
        p.color = Color.argb(50, 0, 0, 0)
        c.drawCircle(size / 2f + 1.5f, size / 2f + 1.5f, size / 2f - 2f, p)

        // Blue circle body
        p.color = Color.parseColor("#1976D2")
        c.drawCircle(size / 2f, size / 2f, size / 2f - 2f, p)

        // White border
        p.style = Paint.Style.STROKE
        p.color = Color.WHITE
        p.strokeWidth = 3f * dp
        c.drawCircle(size / 2f, size / 2f, size / 2f - 2f, p)

        // White pulsing dot in center (simulates GPS dot look)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawCircle(size / 2f, size / 2f, size / 6f, p)

        // Draw "BUS" text
        p.color    = Color.parseColor("#1976D2")
        p.textSize = 9f * dp
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        val textY  = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText("BUS", size / 2f, textY, p)

        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun setBusStatus(online: Boolean) {
        val dot    = findViewById<View>(R.id.view_status_dot)
        val status = findViewById<TextView>(R.id.tv_bus_status)
        if (online) {
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4caf50"))
            status.text = "Bus is on the way"
            status.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CCCCCC"))
            status.text = "Bus is not sharing location"
            status.setTextColor(Color.parseColor("#888888"))
        }
    }

    private fun showLiveBadge(show: Boolean) {
        findViewById<TextView>(R.id.tv_live_badge).visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setLoading(loading: Boolean) {
        findViewById<View>(R.id.loading_overlay).visibility =
            if (loading) View.VISIBLE else View.GONE
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStop() {
        super.onStop()
        trackingViewModel.stopListening()
    }

    override fun onStart() {
        super.onStart()
        supervisorUid?.let { trackingViewModel.startListening(it) }
    }
}
