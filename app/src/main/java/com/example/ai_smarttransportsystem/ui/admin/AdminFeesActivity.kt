package com.example.ai_smarttransportsystem.ui.admin

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Fee
import com.example.ai_smarttransportsystem.data.model.Route
import com.example.ai_smarttransportsystem.data.model.Student
import com.example.ai_smarttransportsystem.data.model.Utils
import com.example.ai_smarttransportsystem.data.repository.FeeRepository
import com.example.ai_smarttransportsystem.data.repository.RouteRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.data.repository.UtilsRepository
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.FeeViewModel
import com.example.ai_smarttransportsystem.ui.shared.RouteViewModel
import com.example.ai_smarttransportsystem.ui.shared.UtilsViewModel
import com.example.ai_smarttransportsystem.ui.student.StudentViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class AdminFeesActivity : BaseActivity() {

    // ── ViewModels ─────────────────────────────────────────────────────────────

    private val utilsViewModel: UtilsViewModel by viewModels {
        UtilsViewModel.Factory(UtilsRepository())
    }
    private val feesViewModel: FeeViewModel by viewModels {
        FeeViewModel.Factory(FeeRepository(), StudentRepository())
    }
    private val routeViewModel: RouteViewModel by viewModels {
        RouteViewModel.Factory(RouteRepository())
    }
    private val studentViewModel: StudentViewModel by viewModels {
        StudentViewModel.factory(StudentRepository())
    }

    // ── Cached data ────────────────────────────────────────────────────────────
    private var cachedUtils:    Utils?       = null
    private var cachedFees:     List<Fee>    = emptyList()
    private var cachedRoutes:   List<Route>  = emptyList()
    private var cachedStudents: List<Student> = emptyList()

    private var utilsReady    = false
    private var feesReady     = false
    private var routesReady    = false
    private var studentsReady = false

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.admin_fees)

        showLoading(true)
        observeAll()
        loadData()
        setupBottomNavigation()
    }

    private fun loadData() {
        utilsViewModel.loadUtils()
        feesViewModel.loadAllFeesAdmin(forceRefresh = true, semester = "Spring 2026")
        routeViewModel.fetchAllRoutes(forceRefresh = true)
        studentViewModel.loadAllStudents(forceRefresh = true)
    }

    private fun observeAll() {
        utilsViewModel.utils.observe(this) { utils ->
            if (utils != null) {
                cachedUtils = utils
                utilsReady  = true
                tryRender()
            }
        }
        feesViewModel.allFees.observe(this) { fees ->
            cachedFees = fees
            feesReady  = true
            tryRender()
        }
        routeViewModel.allRoutes.observe(this) { routes ->
            cachedRoutes = routes
            routesReady  = true
            tryRender()
        }
        studentViewModel.studentsList.observe(this) { students ->
            cachedStudents = students
            studentsReady = true
            tryRender()
        }
        
        utilsViewModel.utilsState.observe(this) { if (it is UtilsViewModel.UtilsUiState.Error) handleError(it.message) }
        feesViewModel.feeState.observe(this) { if (it is FeeViewModel.FeeUiState.Error) handleError(it.message) }
        routeViewModel.routeState.observe(this) { if (it is RouteViewModel.RouteUiState.Error) handleError(it.message) }
        studentViewModel.studentState.observe(this) { if (it is StudentViewModel.StudentUiState.Error) handleError(it.message) }
    }

    private fun handleError(msg: String) {
        showLoading(false)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun tryRender() {
        if (!utilsReady || !feesReady || !routesReady || !studentsReady) return
        showLoading(false)
        val utils  = cachedUtils  ?: return
        renderDashboard(utils, cachedFees, cachedRoutes, cachedStudents)
    }

    private fun renderDashboard(utils: Utils, fees: List<Fee>, routes: List<Route>, students: List<Student>) {
        val computed = compute(utils, fees, routes, students)
        bindTopCards(computed, utils)
        bindSummaryGrid(computed, utils)
        bindPaymentStatus(computed)
        buildRouteTable(computed.routeRows)
        bindFuelAnalysis(utils, computed)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COMPUTATION
    // ─────────────────────────────────────────────────────────────────────────

    data class RouteRow(
        val name:      String,
        val students:  Int,
        val revenue:   Double,
        val profit:    Double,
        val fuelCost:  Double
    )

    data class Computed(
        val totalRevenue:         Double,   // Paid fees only
        val totalFuelCost:        Double,   // Sum of route.fuel_cost
        val totalStudents:        Int,      // Sum of route.total_students
        val avgFeePerStudent:     Double,   // totalExpected / totalStudents
        val profitAmount:         Double,   // totalRevenue * margin%
        val fuelPerStudent:       Double,   // totalFuelCost / totalStudents
        val profitPerStudent:     Double,   // profitAmount / totalStudents
        val totalPending:         Double,
        val collectionRatePct:    Double,
        val routeRows:            List<RouteRow>
    )

    private fun compute(utils: Utils, fees: List<Fee>, routes: List<Route>, students: List<Student>): Computed {
        val marginPct = utils.profitMarginPercent ?: 0.0

        // 1. Total Revenue = Sum of fees where status is "paid"
        val totalRevenue = fees
            .filter { it.paymentStatus?.equals("paid", ignoreCase = true) == true }
            .sumOf { it.amount ?: 0.0 }
            
        val totalExpected = fees.sumOf { it.amount ?: 0.0 }
        val totalPending  = totalExpected - totalRevenue
        val collectionRate = if (totalExpected > 0) (totalRevenue / totalExpected) * 100.0 else 0.0

        // 2. Fuel Cost = Sum of route.fuel_cost field from DB
        val totalFuelCost = routes.sumOf { it.fuelCost ?: 0.0 }
        
        // 3. Total Students = Sum of total_students field from DB routes
        val totalStudentsInRoutes = routes.sumOf { it.totalStudents ?: 0 }

        val avgFee = if (totalStudentsInRoutes > 0) totalExpected / totalStudentsInRoutes else 0.0
        
        // 4. Profit = Paid Revenue * margin percentage
        val profitAmount = totalRevenue * (marginPct / 100.0)
        
        // 5. Fuel/Student = totalFuelCost / totalStudents
        val fuelPerStudent = if (totalStudentsInRoutes > 0) totalFuelCost / totalStudentsInRoutes else 0.0
        val profitPerStudent = if (totalStudentsInRoutes > 0) profitAmount / totalStudentsInRoutes else 0.0

        val studentFeeMap = fees.associateBy { it.studentId }
        val routeStudentsMap = students.groupBy { it.routeId }

        val routeRows = routes.map { route ->
            val routeAssignedStudents = routeStudentsMap[route.docId] ?: emptyList()
            
            // Route Revenue = Paid fees of students assigned to this route
            val rRevenue = routeAssignedStudents.sumOf { s ->
                val f = studentFeeMap[s.uid]
                if (f?.paymentStatus?.equals("paid", ignoreCase = true) == true) f.amount ?: 0.0 else 0.0
            }
            
            val rFuel = route.fuelCost ?: 0.0
            val rProfit = rRevenue * (marginPct / 100.0)

            RouteRow(
                name     = route.routeName ?: "Route",
                students = route.totalStudents ?: 0,
                revenue  = rRevenue,
                profit   = rProfit,
                fuelCost = rFuel
            )
        }

        return Computed(
            totalRevenue         = totalRevenue,
            totalFuelCost        = totalFuelCost,
            totalStudents        = totalStudentsInRoutes,
            avgFeePerStudent     = avgFee,
            profitAmount         = profitAmount,
            fuelPerStudent       = fuelPerStudent,
            profitPerStudent     = profitPerStudent,
            totalPending         = totalPending,
            collectionRatePct    = collectionRate,
            routeRows            = routeRows
        )
    }

    private fun bindTopCards(c: Computed, utils: Utils) {
        tv(R.id.total_revenue).text    = formatPkr(c.totalRevenue)
        tv(R.id.total_fuel_cost).text  = formatPkr(c.totalFuelCost)
        tv(R.id.fuel_price).text       = "PKR ${fmt1(utils.petrolPricePerLitre ?: 0.0)}/L"
    }

    private fun bindSummaryGrid(c: Computed, utils: Utils) {
        tv(R.id.profit_margin).text               = formatPkr(c.profitAmount)
        tv(R.id.profit_margin_in_percentage).text = "${fmt0(utils.profitMarginPercent ?: 0.0)}% Margin"
        tv(R.id.avg_fee).text    = formatPkr(c.avgFeePerStudent)
        tv(R.id.avg_fee_sub).text = "${c.totalStudents} Students"

        tv(R.id.tv_total_students).text     = c.totalStudents.toString()
        tv(R.id.tv_profit_margin_pct).text  = "${fmt0(utils.profitMarginPercent ?: 0.0)}%"
        tv(R.id.tv_fuel_per_student).text   = formatPkr(c.fuelPerStudent)
        tv(R.id.tv_profit_per_student).text = formatPkr(c.profitPerStudent)
    }

    private fun bindPaymentStatus(c: Computed) {
        tv(R.id.tv_total_collected).text  = formatPkr(c.totalRevenue)
        tv(R.id.tv_collection_rate).text  = "${fmt0(c.collectionRatePct)}%"
        tv(R.id.tv_pending_amount).text   = formatPkr(c.totalPending)
    }

    private fun bindFuelAnalysis(utils: Utils, c: Computed) {
        tv(R.id.tv_fuel_rate_large).text = "PKR ${fmt1(utils.petrolPricePerLitre ?: 0.0)} / L"
    }

    private fun buildRouteTable(rows: List<RouteRow>) {
        val table = findViewById<android.widget.TableLayout>(R.id.table_routes)
        while (table.childCount > 1) table.removeViewAt(1)

        if (rows.isEmpty()) {
            val emptyRow = TableRow(this).apply { 
                addView(tableCell("No routes found", minWidthDp = 500, color = "#888888")) 
            }
            table.addView(emptyRow)
            return
        }

        rows.forEachIndexed { i, row ->
            val bg = if (i % 2 == 0) "#FAFAFA" else "#FFFFFF"
            val tr = TableRow(this).apply { setBackgroundColor(Color.parseColor(bg)) }
            tr.addView(tableCell(row.name,                minWidthDp = 110))
            tr.addView(tableCell(row.students.toString(), minWidthDp = 90))
            // Columns: Revenue, Profit, Fuel Cost
            tr.addView(tableCell(formatPkr(row.revenue),  minWidthDp = 110))
            tr.addView(tableCell(formatPkr(row.profit),   minWidthDp = 100, color = "#2E7D32", bold = true))
            tr.addView(tableCell(formatPkr(row.fuelCost), minWidthDp = 110))
            table.addView(tr)
        }
    }

    private fun tableCell(text: String, minWidthDp: Int = 90, color: String = "#212121", bold: Boolean = false, isHeader: Boolean = false): TextView {
        val dp = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            textSize = if (isHeader) 14f else 13f
            setTextColor(Color.parseColor(color))
            gravity = Gravity.CENTER
            setPadding((12*dp).toInt(), (10*dp).toInt(), (12*dp).toInt(), (10*dp).toInt())
            minimumWidth = (minWidthDp * dp).toInt()
            if (bold || isHeader) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun showLoading(show: Boolean) {
        val root = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.main)
        val overlay = root.findViewWithTag<View>("loading_overlay")
        if (show) {
            if (overlay == null) {
                val newOverlay = android.widget.FrameLayout(this).apply {
                    tag = "loading_overlay"
                    layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, 0).apply {
                        topToTop = R.id.main; bottomToBottom = R.id.main
                        startToStart = R.id.main; endToEnd = R.id.main
                    }
                    setBackgroundColor(Color.parseColor("#F5F5F5"))
                    elevation = 20f
                    addView(android.widget.ProgressBar(context).apply {
                        layoutParams = android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)
                        indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9900"))
                    })
                }
                root.addView(newOverlay)
            } else overlay.visibility = View.VISIBLE
        } else overlay?.visibility = View.GONE
    }

    private fun tv(@androidx.annotation.IdRes id: Int): TextView = findViewById(id)

    private fun formatPkr(value: Double): String {
        return when {
            value >= 1_000_000 -> "PKR ${fmt1(value / 1_000_000)}M"
            value >= 1_000     -> "PKR ${fmt1(value / 1_000)}K"
            else               -> "PKR ${fmt0(value)}"
        }
    }
    private fun fmt1(v: Double) = "%.1f".format(v)
    private fun fmt0(v: Double) = "%.0f".format(v)

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.admin_bottom_nav)
        bottomNav.selectedItemId = R.id.nav_fees
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { startActivity(Intent(this, AdminDashboardActivity::class.java)); finish(); true }
                R.id.nav_tracking  -> { startActivity(Intent(this, AdminTrackingActivity::class.java)); finish(); true }
                R.id.nav_fees      -> true
                R.id.nav_attendance -> { startActivity(Intent(this, AdminAttendanceActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }
}
