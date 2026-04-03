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
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Bus
import com.example.ai_smarttransportsystem.data.model.User
import com.example.ai_smarttransportsystem.data.repository.BusRepository
import com.example.ai_smarttransportsystem.data.repository.SupervisorRepository
import com.example.ai_smarttransportsystem.databinding.AdminAssignSupervisorBinding
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.BusViewModel
import com.example.ai_smarttransportsystem.ui.supervisor.SupervisorViewModel
import com.google.android.material.card.MaterialCardView

class AdminAssignSupervisorActivity : BaseActivity() {

    private lateinit var binding: AdminAssignSupervisorBinding

    private val busViewModel: BusViewModel by viewModels {
        BusViewModel.Factory(BusRepository())
    }
    private val supervisorViewModel: SupervisorViewModel by viewModels {
        SupervisorViewModel.Factory(SupervisorRepository())
    }

    // ── Route pre-filter passed from AdminManageRoutesActivity ────────────────
    // null = show all buses that need a supervisor
    private var incomingRouteId: String? = null

    // ── Selection state ────────────────────────────────────────────────────────
    private var selectedBusDocId:      String? = null
    private var selectedBusRouteId:    String? = null
    private var selectedSupervisorUid: String? = null

    private val busCardMap         = mutableMapOf<String, MaterialCardView>()
    private val busRadioMap        = mutableMapOf<String, RadioButton>()
    private val supervisorCardMap  = mutableMapOf<String, MaterialCardView>()
    private val supervisorRadioMap = mutableMapOf<String, RadioButton>()
    private val busDataMap         = mutableMapOf<String, Bus>()

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = AdminAssignSupervisorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read optional route_id passed from the route card
        incomingRouteId = intent.getStringExtra("route_id")

        binding.btnBack.setOnClickListener { finish() }
        binding.btnConfirm.setOnClickListener { handleConfirm() }

        observeBuses()
        observeSupervisors()
        observeAssignState()

        // Load only buses that have a route but no supervisor yet.
        // If we came from a specific route card, pass that route_id so only
        // the matching bus appears in the list.
        busViewModel.loadBusesForSupervisorAssign(forceRefresh = true,filterRouteId = incomingRouteId)

        setupBottomNavigation()
    }

    // ── Observers ──────────────────────────────────────────────────────────────

    private fun observeBuses() {
        busViewModel.busState.observe(this) { state ->
            when (state) {
                is BusViewModel.BusUiState.Loading -> {
                    binding.progressLoading.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility     = View.GONE
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

        // Observe the filtered list (buses with route, no supervisor)
        busViewModel.busesForSupervisorAssign.observe(this) { pairs ->
            binding.progressLoading.visibility = View.GONE
            binding.containerBusList.removeAllViews()
            busCardMap.clear(); busRadioMap.clear(); busDataMap.clear()
            selectedBusDocId = null; selectedBusRouteId = null

            if (pairs.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.scrollView.visibility  = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.scrollView.visibility  = View.VISIBLE
                pairs.forEach { (docId, bus) ->
                    busDataMap[docId] = bus
                    addBusCard(bus, docId)
                }
            }
        }
    }

    private fun observeSupervisors() {
        // Observe the AVAILABLE supervisors (not yet assigned)
        supervisorViewModel.availableSupervisors.observe(this) { pairs ->
            binding.containerSupervisorList.removeAllViews()
            supervisorCardMap.clear(); supervisorRadioMap.clear()
            selectedSupervisorUid = null
            binding.btnConfirm.visibility = View.GONE

            if (pairs.isEmpty()) {
                Toast.makeText(this, "No available supervisors — all are already assigned", Toast.LENGTH_SHORT).show()
                binding.sectionSupervisors.visibility = View.GONE
            } else {
                binding.sectionSupervisors.visibility = View.VISIBLE
                pairs.forEach { (uid, user) -> addSupervisorCard(uid, user) }
            }
        }
    }

    private fun observeAssignState() {
        supervisorViewModel.supervisorState.observe(this) { state ->
            if (state is SupervisorViewModel.SupervisorUiState.Error) {
                binding.progressLoading.visibility = View.GONE
                binding.btnConfirm.isEnabled       = true
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Build bus card ─────────────────────────────────────────────────────────

    private fun addBusCard(bus: Bus, docId: String) {
        val dp = resources.displayMetrics.density

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, (8*dp).toInt(), 0, (8*dp).toInt()) }
            radius        = 16 * dp
            cardElevation = 4 * dp
            setCardBackgroundColor(Color.WHITE)
            strokeWidth   = 0
            isClickable   = true
            isFocusable   = true
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
        }

        val radio = RadioButton(this).apply { isClickable = false; isChecked = false }
        busRadioMap[docId] = radio

        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams((52*dp).toInt(), (52*dp).toInt())
                .also { it.marginStart = (8*dp).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1565C0"))
            }
        }
        iconFrame.addView(ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams((26*dp).toInt(), (26*dp).toInt(), Gravity.CENTER)
            setImageResource(R.drawable.baseline_directions_bus_24)
            setColorFilter(Color.WHITE)
        })

        val textCol = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = (14*dp).toInt() }
        }
        textCol.addView(TextView(this).apply {
            text = bus.busNumber ?: "Bus"; textSize = 16f
            setTextColor(Color.parseColor("#1A1A1A")); setTypeface(typeface, Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text = bus.plateNumber ?: "—"; textSize = 12f
            setTextColor(Color.parseColor("#888888")); setPadding(0, (2*dp).toInt(), 0, 0)
        })
        textCol.addView(buildBadge("Route: ${bus.routeId ?: "Not set"}", "#FF9800", dp))

        val capCard = buildMiniStatCard(
            R.drawable.baseline_person_24, "#1976D2",
            "${bus.capacity ?: "—"}", "Capacity", dp
        ).also {
            it.layoutParams = LinearLayout.LayoutParams((76*dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { p -> p.marginStart = (8*dp).toInt() }
        }

        inner.addView(radio); inner.addView(iconFrame)
        inner.addView(textCol); inner.addView(capCard)
        card.addView(inner)

        busCardMap[docId] = card
        card.setOnClickListener { selectBus(docId) }
        binding.containerBusList.addView(card)
    }

    // ── Build supervisor card ──────────────────────────────────────────────────

    private fun addSupervisorCard(uid: String, user: User) {
        val dp = resources.displayMetrics.density

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, (8*dp).toInt(), 0, (8*dp).toInt()) }
            radius        = 16 * dp
            cardElevation = 4 * dp
            setCardBackgroundColor(Color.WHITE)
            strokeWidth   = 0
            isClickable   = true
            isFocusable   = true
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt())
        }

        val radio = RadioButton(this).apply { isClickable = false; isChecked = false }
        supervisorRadioMap[uid] = radio

        val initials = user.name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams((52*dp).toInt(), (52*dp).toInt())
                .also { it.marginStart = (8*dp).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor("#2E7D32"))
            }
        }
        iconFrame.addView(TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            text = initials; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
        })

        val textCol = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = (14*dp).toInt() }
        }
        textCol.addView(TextView(this).apply {
            text = user.name.ifEmpty { "Supervisor" }; textSize = 15f
            setTextColor(Color.parseColor("#1A1A1A")); setTypeface(typeface, Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text = user.email.ifEmpty { "—" }; textSize = 12f
            setTextColor(Color.parseColor("#555555")); setPadding(0, (2*dp).toInt(), 0, 0)
        })
        textCol.addView(buildBadge("Available", "#4CAF50", dp))

        inner.addView(radio); inner.addView(iconFrame); inner.addView(textCol)
        card.addView(inner)

        supervisorCardMap[uid] = card
        card.setOnClickListener { selectSupervisor(uid) }
        binding.containerSupervisorList.addView(card)
    }

    // ── Selection ──────────────────────────────────────────────────────────────

    private fun selectBus(docId: String) {
        val dp = resources.displayMetrics.density
        selectedBusDocId?.let {
            busCardMap[it]?.strokeWidth = 0
            busRadioMap[it]?.isChecked  = false
        }
        selectedBusDocId   = docId
        selectedBusRouteId = busDataMap[docId]?.routeId
        busCardMap[docId]?.apply { strokeWidth = (2*dp).toInt(); strokeColor = Color.parseColor("#1565C0") }
        busRadioMap[docId]?.isChecked = true

        // Reset supervisor section and load fresh available supervisors
        binding.sectionSupervisors.visibility = View.GONE
        binding.btnConfirm.visibility         = View.GONE
        supervisorViewModel.loadAvailableSupervisors(forceRefresh = true)
    }

    private fun selectSupervisor(uid: String) {
        val dp = resources.displayMetrics.density
        selectedSupervisorUid?.let {
            supervisorCardMap[it]?.strokeWidth = 0
            supervisorRadioMap[it]?.isChecked  = false
        }
        selectedSupervisorUid = uid
        supervisorCardMap[uid]?.apply { strokeWidth = (2*dp).toInt(); strokeColor = Color.parseColor("#2E7D32") }
        supervisorRadioMap[uid]?.isChecked = true
        binding.btnConfirm.visibility = View.VISIBLE
    }

    // ── Confirm ────────────────────────────────────────────────────────────────

    private fun handleConfirm() {
        val busDocId = selectedBusDocId
        val supUid   = selectedSupervisorUid
        val routeId  = selectedBusRouteId

        if (busDocId == null) { Toast.makeText(this, "Select a bus first", Toast.LENGTH_SHORT).show(); return }
        if (supUid == null)   { Toast.makeText(this, "Select a supervisor", Toast.LENGTH_SHORT).show(); return }

        binding.progressLoading.visibility = View.VISIBLE
        binding.btnConfirm.isEnabled       = false

        supervisorViewModel.assignSupervisor(
            supervisorUid = supUid,
            busDocId      = busDocId,
            routeId       = routeId,
            onSuccess = {
                runOnUiThread {
                    binding.progressLoading.visibility = View.GONE
                    Toast.makeText(this, "Supervisor assigned successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this, AdminDashboardActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                    finish()
                }
            },
            onError = { msg ->
                runOnUiThread {
                    binding.progressLoading.visibility = View.GONE
                    binding.btnConfirm.isEnabled       = true
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildBadge(text: String, colorHex: String, dp: Float): TextView {
        val parsed = Color.parseColor(colorHex)
        return TextView(this).apply {
            this.text = text; textSize = 10f
            setTypeface(typeface, Typeface.BOLD); setTextColor(parsed)
            setPadding((8*dp).toInt(), (2*dp).toInt(), (8*dp).toInt(), (2*dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (4*dp).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.argb(30, Color.red(parsed), Color.green(parsed), Color.blue(parsed)))
                cornerRadius = 20 * dp
            }
        }
    }

    private fun buildMiniStatCard(icon: Int, iconTint: String, value: String, label: String, dp: Float): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = 12 * dp; cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#F8F9FA"))
            strokeWidth = (1 * dp).toInt(); strokeColor = Color.parseColor("#E0E0E0")
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding((10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt(), (10*dp).toInt())
        }
        layout.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((18*dp).toInt(), (18*dp).toInt())
            setImageResource(icon); setColorFilter(Color.parseColor(iconTint))
        })
        layout.addView(TextView(this).apply {
            this.text = value; textSize = 14f; gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#1A1A1A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = (3*dp).toInt() }
        })
        layout.addView(TextView(this).apply {
            text = label; textSize = 10f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#808080"))
        })
        card.addView(layout)
        return card
    }

    // ── Bottom nav ─────────────────────────────────────────────────────────────

    private fun setupBottomNavigation() {
        binding.adminBottomNav.selectedItemId = R.id.nav_dashboard
        binding.adminBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard  -> { startActivity(Intent(this, AdminDashboardActivity::class.java));  finish(); true }
                R.id.nav_tracking   -> { startActivity(Intent(this, AdminTrackingActivity::class.java));   finish(); true }
                R.id.nav_fees       -> { startActivity(Intent(this, AdminFeesActivity::class.java));       finish(); true }
                R.id.nav_attendance -> { startActivity(Intent(this, AdminAttendanceActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }
}