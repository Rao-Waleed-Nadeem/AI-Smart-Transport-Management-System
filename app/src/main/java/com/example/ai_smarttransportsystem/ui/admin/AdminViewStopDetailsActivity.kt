package com.example.ai_smarttransportsystem.ui.admin

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Stop
import com.example.ai_smarttransportsystem.data.repository.StopRepository
import com.example.ai_smarttransportsystem.databinding.AdminViewStopDetailsBinding
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.StopViewModel
import com.example.ai_smarttransportsystem.ui.shared.StopViewModel.StopUiState
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class AdminViewStopDetailsActivity : BaseActivity() {

    private lateinit var binding: AdminViewStopDetailsBinding

    private var routeId: String? = null
    private var optimizedOrder: ArrayList<String>? = null

    private val stopViewModel: StopViewModel by viewModels {
        StopViewModel.Factory(StopRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminViewStopDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Stop Details"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        routeId = intent.getStringExtra("route_id")
        optimizedOrder = intent.getStringArrayListExtra("optimized_order")

        // Observe and load stops
        stopViewModel.stopsState.observe(this) { state ->
            when (state) {
                is StopUiState.Success -> {
                    val ordered = stopViewModel.orderedStops.value ?: emptyList()
                    showDynamicStopCards(ordered)
                }
                is StopUiState.Empty -> {
                    binding.stopsContainer.removeAllViews()
                    Toast.makeText(this, "No stops in this route", Toast.LENGTH_SHORT).show()
                }
                is StopUiState.Error -> {
                    Toast.makeText(this, state.message ?: "Error loading stops", Toast.LENGTH_SHORT).show()
                }
                else -> {} // Loading or Idle
            }
        }

        optimizedOrder?.let { order ->
            if (order.isNotEmpty()) {
                stopViewModel.loadStopsByIds(forceRefresh = true, orderedIds = order)
            } else {
                Toast.makeText(this, "No stops found", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(this, "No route data", Toast.LENGTH_SHORT).show()

        setupBottomNavigation()
        binding.btnBack.setOnClickListener { finish() }

    }

    private fun showDynamicStopCards(orderedStops: List<Pair<String, Stop>>) {
        binding.stopsContainer.removeAllViews()
        if (orderedStops.isEmpty()) return

        val dp = resources.displayMetrics.density

        orderedStops.forEachIndexed { index, (_, stop) ->

            // ── Outer card ────────────────────────────────────────────────────
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart  = (16 * dp).toInt()
                    marginEnd    = (16 * dp).toInt()
                    topMargin    = (16 * dp).toInt()
                }
                radius        = (16 * dp)
                cardElevation = (6 * dp)
                setCardBackgroundColor(Color.WHITE)
                strokeWidth = 0
            }

            val cardInner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    (16 * dp).toInt(), (16 * dp).toInt(),
                    (16 * dp).toInt(), (16 * dp).toInt()
                )
            }

            // ── Row 1: icon + name/number + "Incoming" chip ───────────────────
            val row1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
            }

            // Blue circle icon
            val iconFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (44 * dp).toInt(), (44 * dp).toInt()
                ).apply { marginEnd = (12 * dp).toInt() }
                background = getDrawable(R.drawable.blue_full_rounded)
            }
            val icon = android.widget.ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (24 * dp).toInt(), (24 * dp).toInt()
                ).apply { gravity = Gravity.CENTER }
                setImageResource(R.drawable.route)
                setColorFilter(Color.WHITE)
            }
            iconFrame.addView(icon)

            // Stop name + Stop-#N
            val nameCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            val tvStopName = TextView(this).apply {
                text      = stop.stopName ?: "Unnamed Stop"
                textSize  = 16f
                setTextColor(Color.parseColor("#1A1A1A"))
                setTypeface(null, Typeface.BOLD)
            }
            val tvStopNumber = TextView(this).apply {
                text      = "Stop-#${index + 1}"
                textSize  = 12f
                setTextColor(Color.parseColor("#888888"))
            }
            nameCol.addView(tvStopName)
            nameCol.addView(tvStopNumber)

            // "Incoming" status chip
            val chipIncoming = TextView(this).apply {
                text = "Incoming"
                textSize = 11f
                setTextColor(Color.parseColor("#2E7D32"))
                setTypeface(null, Typeface.BOLD)
                background = getDrawable(R.drawable.rounded_chip_background)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#E8F5E9")
                )
                setPadding(
                    (10 * dp).toInt(), (4 * dp).toInt(),
                    (10 * dp).toInt(), (4 * dp).toInt()
                )
            }

            row1.addView(iconFrame)
            row1.addView(nameCol)
            row1.addView(chipIncoming)

            // ── Row 2: Arrival / Students / Distance sub-cards ────────────────
            val row2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (14 * dp).toInt() }
            }

            fun subCard(value: String, label: String, valueColor: String): MaterialCardView {
                val sc = MaterialCardView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { marginEnd = (6 * dp).toInt() }
                    radius        = (8 * dp)
                    cardElevation = 0f
                    setCardBackgroundColor(Color.parseColor("#F8F9FA"))
                    strokeColor = Color.parseColor("#E0E0E0")
                    strokeWidth = (1 * dp).toInt()
                }
                val inner = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity     = Gravity.CENTER
                    setPadding(
                        (10 * dp).toInt(), (10 * dp).toInt(),
                        (10 * dp).toInt(), (10 * dp).toInt()
                    )
                }
                val tvVal = TextView(this).apply {
                    text      = value
                    textSize  = 16f
                    setTextColor(Color.parseColor(valueColor))
                    setTypeface(null, Typeface.BOLD)
                }
                val tvLbl = TextView(this).apply {
                    text      = label
                    textSize  = 11f
                    setTextColor(Color.parseColor("#808080"))
                }
                inner.addView(tvVal)
                inner.addView(tvLbl)
                sc.addView(inner)
                return sc
            }

            // Arrival — static placeholder (no real-time data available)
            val scArrival = subCard("N/A", "Arrival", "#1976D2")
            // Students count from data
            val scStudents = subCard(
                "${stop.studentCount ?: 0}",
                "Students",
                "#4CAF50"
            )
            // Distance to university
            val distanceText = stop.distanceToUniversityKm
                ?.let { String.format("%.1f km", it) }
                ?: "N/A"
            val scDistance = subCard(distanceText, "Distance", "#FF8F00")
            // Remove end margin from last sub-card
            (scDistance.layoutParams as LinearLayout.LayoutParams).marginEnd = 0

            row2.addView(scArrival)
            row2.addView(scStudents)
            row2.addView(scDistance)

            // ── Est. Time chip ────────────────────────────────────────────────
            val chipEstTime = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                background  = getDrawable(R.drawable.rounded_chip_background)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#EEF2FF")
                )
                setPadding(
                    (8 * dp).toInt(), (4 * dp).toInt(),
                    (8 * dp).toInt(), (4 * dp).toInt()
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (12 * dp).toInt() }
            }
            val tvEstVal = TextView(this).apply {
                text      = "N/A"
                textSize  = 14f
                setTextColor(Color.parseColor("#1565C0"))
                setTypeface(null, Typeface.BOLD)
            }
            val tvEstLabel = TextView(this).apply {
                text      = "Est. Time"
                textSize  = 10f
                setTextColor(Color.parseColor("#808080"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = (6 * dp).toInt() }
            }
            chipEstTime.addView(tvEstVal)
            chipEstTime.addView(tvEstLabel)

            // ── "View Students" button ────────────────────────────────────────
            val btnViewStudents = MaterialButton(this).apply {
                text = "View Students"
                textSize = 14f
                isAllCaps = false
                setBackgroundColor(Color.parseColor("#1976D2"))
                cornerRadius = (8 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (16 * dp).toInt() }
                setOnClickListener {
                    val intent = Intent(
                        this@AdminViewStopDetailsActivity,
                        AdminViewStopStudentsActivity::class.java
                    ).apply {
                        putStringArrayListExtra(
                            "student_ids",
                            ArrayList(stop.studentIds ?: emptyList())
                        )
                        putExtra("stop_name",   stop.stopName ?: "Stop")
                        putExtra("stop_number", index + 1)
                    }
                    startActivity(intent)
                }
            }

            // ── Assemble card ─────────────────────────────────────────────────
            cardInner.addView(row1)
            cardInner.addView(row2)
            cardInner.addView(chipEstTime)
            cardInner.addView(btnViewStudents)
            card.addView(cardInner)
            binding.stopsContainer.addView(card)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupBottomNavigation() {
        binding.adminBottomNav.selectedItemId = R.id.nav_dashboard
        binding.adminBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { startActivity(Intent(this, AdminDashboardActivity::class.java)); finish(); true }
                R.id.nav_tracking  -> { startActivity(Intent(this, AdminTrackingActivity::class.java));  finish(); true }
                R.id.nav_fees      -> { startActivity(Intent(this, AdminFeesActivity::class.java));      finish(); true }
                R.id.nav_attendance-> { startActivity(Intent(this, AdminAttendanceActivity::class.java));finish(); true }
                else -> false
            }
        }
    }
}