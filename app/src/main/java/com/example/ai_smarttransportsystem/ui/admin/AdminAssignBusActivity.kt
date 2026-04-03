package com.example.ai_smarttransportsystem.ui.admin

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Bus
import com.example.ai_smarttransportsystem.data.repository.BusRepository
import com.example.ai_smarttransportsystem.databinding.AdminAssignBusBinding
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.BusViewModel
import com.google.android.material.card.MaterialCardView

class AdminAssignBusActivity : BaseActivity() {

    private lateinit var binding: AdminAssignBusBinding

    private val busViewModel: BusViewModel by viewModels {
        BusViewModel.Factory(BusRepository())
    }

    // Received from AdminViewRouteActivity via intent key "route_id"
    private var routeId: String? = null

    private var selectedBusDocId: String? = null
    private val busCardMap  = mutableMapOf<String, MaterialCardView>()
    private val busRadioMap = mutableMapOf<String, RadioButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = AdminAssignBusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Read intent extra ─────────────────────────────────────────────────
        routeId = intent.getStringExtra("route_id")
        if (routeId == null) {
            Toast.makeText(this, "Route data missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvBannerText.text = "Select an available bus to assign to route: $routeId"

        observeBuses()
        busViewModel.loadAllBuses(forceRefresh = true)

        binding.btnAssignAction.setOnClickListener { handleAssign() }
        setupBottomNavigation()
        binding.btnBack.setOnClickListener { finish() }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeBuses() {
        busViewModel.busState.observe(this) { state ->
            when (state) {
                is BusViewModel.BusUiState.Loading -> {
                    binding.progressLoading.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility     = View.GONE
                    binding.scrollView.visibility      = View.GONE
                }
                is BusViewModel.BusUiState.Empty -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.layoutEmpty.visibility     = View.VISIBLE
                    binding.scrollView.visibility      = View.GONE
                }
                is BusViewModel.BusUiState.Error -> {
                    binding.progressLoading.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> binding.progressLoading.visibility = View.GONE
            }
        }

        // Single observer on busesWithIds — contains real Firestore doc IDs
        busViewModel.busesWithIds.observe(this) { pairs ->
            val available = pairs.filter { (_, bus) ->
                bus.status?.lowercase() == "active" && bus.isAvailable == true
            }

            binding.containerBusList.removeAllViews()
            busCardMap.clear()
            busRadioMap.clear()
            selectedBusDocId = null

            if (available.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.scrollView.visibility  = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.scrollView.visibility  = View.VISIBLE
                available.forEach { (docId, bus) -> addBusCard(bus, docId) }
            }
        }
    }

    // ── Build one bus card ────────────────────────────────────────────────────

    private fun addBusCard(bus: Bus, docId: String) {
        val dp = resources.displayMetrics.density

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins((8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt()) }
            radius        = 16 * dp
            cardElevation = 4 * dp
            setCardBackgroundColor(Color.WHITE)
            strokeWidth   = 0
            isClickable   = true
            isFocusable   = true
        }

        val inner = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
            gravity = Gravity.CENTER_VERTICAL
        }

        val radio = RadioButton(this).apply { isClickable = false; isChecked = false }
        busRadioMap[docId] = radio

        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams((56*dp).toInt(), (56*dp).toInt()).also { it.marginStart = (8*dp).toInt() }
            background   = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1976D2")) }
        }
        iconFrame.addView(ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams((30*dp).toInt(), (30*dp).toInt(), Gravity.CENTER)
            setImageResource(R.drawable.baseline_directions_bus_24)
            setColorFilter(Color.WHITE)
        })

        val textCol = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = (16*dp).toInt() }
        }
        textCol.addView(TextView(this).apply {
            text     = bus.busNumber ?: "Bus"
            textSize = 18f
            setTextColor(Color.parseColor("#1A1A1A"))
            setTypeface(typeface, Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text     = bus.plateNumber ?: "—"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        })

        val capCard = buildMiniStatCard(
            icon     = R.drawable.baseline_person_24,
            iconTint = "#1976D2",
            value    = "${bus.capacity ?: "—"}",
            label    = "Capacity",
            dp       = dp
        ).also {
            it.layoutParams = LinearLayout.LayoutParams((80*dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { p -> p.marginStart = (8*dp).toInt() }
        }

        inner.addView(radio)
        inner.addView(iconFrame)
        inner.addView(textCol)
        inner.addView(capCard)
        card.addView(inner)

        busCardMap[docId] = card
        card.setOnClickListener { selectBus(docId) }
        binding.containerBusList.addView(card)
    }

    private fun buildMiniStatCard(icon: Int, iconTint: String, value: String, label: String, dp: Float): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius        = 12 * dp
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#F8F9FA"))
            strokeWidth   = (1 * dp).toInt()
            strokeColor   = Color.parseColor("#E0E0E0")
        }
        val layout = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding((12*dp).toInt(), (10*dp).toInt(), (12*dp).toInt(), (10*dp).toInt())
        }
        layout.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((20*dp).toInt(), (20*dp).toInt())
            setImageResource(icon); setColorFilter(Color.parseColor(iconTint))
        })
        layout.addView(TextView(this).apply {
            text     = value; textSize = 15f; gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1A1A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = (4*dp).toInt() }
        })
        layout.addView(TextView(this).apply {
            text = label; textSize = 11f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#808080"))
        })
        card.addView(layout)
        return card
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    private fun selectBus(docId: String) {
        val dp = resources.displayMetrics.density
        selectedBusDocId?.let { prev ->
            busCardMap[prev]?.strokeWidth = 0
            busRadioMap[prev]?.isChecked  = false
        }
        selectedBusDocId = docId
        busCardMap[docId]?.apply { strokeWidth = (2*dp).toInt(); strokeColor = Color.parseColor("#1976D2") }
        busRadioMap[docId]?.isChecked = true
    }

    // ── Assign ────────────────────────────────────────────────────────────────

    private fun handleAssign() {
        val busDocId = selectedBusDocId
        val rId      = routeId

        if (busDocId == null) { Toast.makeText(this, "Please select a bus first", Toast.LENGTH_SHORT).show(); return }
        if (rId == null)      { Toast.makeText(this, "Route ID missing", Toast.LENGTH_SHORT).show(); return }

        setLoading(true)
        busViewModel.assignBusToRoute(
            busDocId  = busDocId,
            routeId   = rId,
            onSuccess = {
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(this, "Bus assigned! Now assign a supervisor.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, AdminAssignSupervisorActivity::class.java).apply {
                        putExtra("route_id", rId)
                    })
                    finish()
                }
            },
            onError   = { msg -> runOnUiThread { setLoading(false); Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } }
        )
    }

    private fun setLoading(loading: Boolean) {
        binding.btnAssignAction.isEnabled  = !loading
        binding.btnAssignAction.text       = if (loading) "Assigning…" else "Assign Bus to Route"
        binding.progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
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