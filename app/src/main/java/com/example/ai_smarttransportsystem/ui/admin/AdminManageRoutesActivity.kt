package com.example.ai_smarttransportsystem.ui.admin

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Route
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.databinding.AdminManageRoutesBinding
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.RouteViewModel
import com.google.android.material.card.MaterialCardView

class AdminManageRoutesActivity : BaseActivity() {

    private lateinit var binding: AdminManageRoutesBinding

    private val routeViewModel: RouteViewModel by viewModels {
        RouteViewModel.Factory(RouteRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = AdminManageRoutesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        routeViewModel.allRoutes.observe(this) { routes ->
            binding.tvRouteCount.text = "${routes.size} routes found"

            val totalStops    = routes.sumOf { it.numStops ?: 0 }
            val totalStudents = routes.sumOf { it.totalStudents ?: 0 }
            val totalDistKm   = routes.sumOf { it.totalDistanceKm ?: 0.0 }
            val avgTimeHours  = if (routes.isEmpty()) 0.0
            else routes.mapNotNull { it.estimatedTimeHours }.average()
            val avgTimeMin    = (avgTimeHours * 60).toInt()

            binding.totalStops.text    = if (totalStops    > 0) totalStops.toString()              else "-"
            binding.totalStudents.text = if (totalStudents > 0) totalStudents.toString()           else "-"
            binding.totalDistance.text = if (totalDistKm   > 0) "${"%.1f".format(totalDistKm)} km" else "-"
            binding.averageTime.text   = if (avgTimeMin    > 0) "$avgTimeMin min"                  else "-"

            if (routes.isEmpty()) {
                binding.layoutEmpty.visibility    = View.VISIBLE
                binding.containerRoutes.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility    = View.GONE
                binding.containerRoutes.visibility = View.VISIBLE
                binding.containerRoutes.removeAllViews()
                routes.forEachIndexed { index, route ->
                    binding.containerRoutes.addView(buildRouteCard(route, index + 1))
                }
            }
        }

        routeViewModel.routeState.observe(this) { state ->
            when (state) {
                is RouteViewModel.RouteUiState.Loading -> binding.progressLoading.visibility = View.VISIBLE
                is RouteViewModel.RouteUiState.Error   -> {
                    binding.progressLoading.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> binding.progressLoading.visibility = View.GONE
            }
        }

        routeViewModel.fetchAllRoutes(forceRefresh = true)
        binding.btnBack.setOnClickListener { finish() }
        setupBottomNavigation()
    }

    // ── Build one route card ──────────────────────────────────────────────────

    private fun buildRouteCard(route: Route, routeNumber: Int): MaterialCardView {
        val dp = resources.displayMetrics.density

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(16*dp.toInt(), 16*dp.toInt(), 16*dp.toInt(), 8*dp.toInt()) }
            radius        = 16 * dp
            cardElevation = 6 * dp
            setCardBackgroundColor(Color.WHITE)
            strokeWidth   = 0
        }

        val outer = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // ── Header row ────────────────────────────────────────────────────────
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (8*dp).toInt())
        }

        val iconCircle = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams((48*dp).toInt(), (48*dp).toInt())
            gravity      = Gravity.CENTER
            background   = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1565C0")) }
        }
        val iconImg = android.widget.ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((24*dp).toInt(), (24*dp).toInt())
            setImageResource(R.drawable.route)
            setColorFilter(Color.WHITE)
        }
        iconCircle.addView(iconImg)

        val nameCol = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = (10*dp).toInt() }
        }
        nameCol.addView(TextView(this).apply {
            text     = route.routeName ?: "Route $routeNumber"
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        nameCol.addView(TextView(this).apply {
            text     = "Bus: ${route.busId ?: "Not assigned"}"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        })

        val badge = TextView(this).apply {
            text     = "Active"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding((10*dp).toInt(), (4*dp).toInt(), (10*dp).toInt(), (4*dp).toInt())
            background = GradientDrawable().apply { setColor(Color.parseColor("#4CAF50")); cornerRadius = 20 * dp }
        }

        headerRow.addView(iconCircle)
        headerRow.addView(nameCol)
        headerRow.addView(badge)

        // ── Stats row ─────────────────────────────────────────────────────────
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12*dp).toInt(), 0, (12*dp).toInt(), (12*dp).toInt())
        }

        listOf(
            Triple(route.numStops?.toString() ?: "-",                                       "Stops",    "#1976D2"),
            Triple(route.totalStudents?.toString() ?: "-",                                  "Students", "#4CAF50"),
            Triple(route.totalDistanceKm?.let { "${"%.1f".format(it)} km" } ?: "-",        "Distance", "#FF9800"),
            Triple(route.estimatedTimeHours?.let { "${(it * 60).toInt()} min" } ?: "-",    "Est. Time","#2196F3"),
        ).forEach { (value, label, color) ->
            statsRow.addView(buildMiniStatCard(value, label, color, dp))
        }

        // ── View Route button ─────────────────────────────────────────────────
        // ── Buttons Row (Assign Bus + View Stops) ─────────────────────────────
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.setMargins((16*dp).toInt(), (8*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
            }
        }

// Assign Bus Button
        val btnAssign = com.google.android.material.button.MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (52 * dp).toInt(), 1f).also {
                it.marginEnd = (8 * dp).toInt()
            }

            text = "Assign Bus"
            setTextColor(Color.parseColor("#1976D2"))
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#1976D2"))
            strokeWidth = (2 * dp).toInt()
            cornerRadius = (8 * dp).toInt()
            setBackgroundColor(Color.TRANSPARENT)

            // Disable button and change appearance if bus is already assigned
            if (!route.busId.isNullOrEmpty()) {
                isEnabled = false
                alpha = 0.6f                    // Makes it look disabled
                setTextColor(Color.parseColor("#9E9E9E"))   // Grey text
                strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
                text = "Assigned"
            } else {
                isEnabled = true
                alpha = 1.0f
                setTextColor(Color.parseColor("#1976D2"))
                strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#1976D2"))
                text = "Assign Bus"
            }
        }

// View Stops Button
        val btnStops = com.google.android.material.button.MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (52*dp).toInt(), 1f).also {
                it.marginStart = (8*dp).toInt()
            }
            text = "View Stops"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1976D2"))
            cornerRadius = (8*dp).toInt()
        }

// Click listeners
        btnAssign.setOnClickListener {
            startActivity(
                Intent(this, AdminAssignBusActivity::class.java).apply {
                    putExtra("route_id", route.docId)
                }
            )
        }

        btnStops.setOnClickListener {
            val optimizedOrder= route.optimizedOrder
            val order = routeViewModel.currentRoute.value?.optimizedOrder
                ?: optimizedOrder
            startActivity(
                Intent(this, AdminViewStopDetailsActivity::class.java).apply {
                    putExtra("route_id", route.docId)
                    route.optimizedOrder?.let {
                        putStringArrayListExtra("optimized_order", ArrayList(order))
                    }
                }
            )
        }

// Add buttons to row
        buttonRow.addView(btnAssign)
        buttonRow.addView(btnStops)

// Add row to card
        outer.addView(buttonRow)
        outer.addView(headerRow)
        outer.addView(statsRow)
        card.addView(outer)
        return card
    }

    private fun buildMiniStatCard(value: String, label: String, colorHex: String, dp: Float): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (70*dp).toInt(), 1f).also { it.setMargins((4*dp).toInt(), 0, (4*dp).toInt(), (8*dp).toInt()) }
            radius        = 12 * dp
            cardElevation = 2 * dp
            setCardBackgroundColor(Color.parseColor("#F5F5F5"))
            strokeWidth   = 0
        }
        val inner = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        inner.addView(TextView(this).apply {
            text     = value; textSize = 16f
            setTextColor(Color.parseColor(colorHex))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity  = Gravity.CENTER
        })
        inner.addView(TextView(this).apply {
            text     = label; textSize = 11f
            setTextColor(Color.parseColor("#555555"))
            gravity  = Gravity.CENTER
        })
        card.addView(inner)
        return card
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

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