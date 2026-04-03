package com.example.ai_smarttransportsystem.ui.supervisor

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Student
import com.example.ai_smarttransportsystem.data.repository.AttendanceRepository
import com.example.ai_smarttransportsystem.data.repository.SupervisorRepository
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.AttendanceViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SupervisorAttendanceActivity : BaseActivity() {

    private val attendanceViewModel: AttendanceViewModel by viewModels {
        AttendanceViewModel.Factory(AttendanceRepository())
    }

    private var busId:   String? = null
    private var routeId: String? = null
    private val supervisorUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    // in-memory attendance state: rollNo → isPresent
    private val attendanceState = mutableMapOf<String, Boolean>()
    private var allStudents: List<Student> = emptyList()

    private lateinit var listContainer:  LinearLayout
    private lateinit var tvListHeader:   TextView

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.supervisor_attendance)

        val now = Date()
        findViewById<TextView>(R.id.tv_date).text =
            SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault()).format(now)
        findViewById<TextView>(R.id.tv_time).text =
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)

        listContainer = findViewById(R.id.student_list_container)
        tvListHeader  = findViewById(R.id.tv_list_header)

        setupDialogButtons()
        setupQuickActions()
        setupSearch()
        observeViewModel()
        setupBottomNavigation()

        loadSupervisorAssignment()
    }

    // ── Step 1: get supervisor's assigned bus + route ──────────────────────────

    private fun loadSupervisorAssignment() {
        setLoading(true)
        val repo = SupervisorRepository()
        lifecycleScope.launch {
            val result = repo.getCurrentSupervisor(forceRefresh = true)
            if (result.isFailure || result.getOrNull() == null) {
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(this@SupervisorAttendanceActivity,
                        "No assignment found. Contact admin.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val supervisor = result.getOrNull()!!
            busId   = supervisor.assignedBus
            routeId = supervisor.assignedRoute

            val bId = busId
            val rId = routeId
            if (bId == null || rId == null) {
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(this@SupervisorAttendanceActivity,
                        "Bus or route not assigned yet.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            runOnUiThread {
                attendanceViewModel.loadAttendanceForSupervisor(forceRefresh = true,routeId = rId, busId = bId)
            }
        }
    }

    // ── Observe ViewModel states ───────────────────────────────────────────────

    private fun observeViewModel() {
        attendanceViewModel.attendanceState.observe(this) { state ->
            when (state) {
                is AttendanceViewModel.AttendanceUiState.Loading -> setLoading(true)

                is AttendanceViewModel.AttendanceUiState.Success -> {
                    setLoading(false)
                    // If this is a post-submit success, show the result view directly
                    if (state.message?.contains("submitted") == true) {
                        showAlreadyTakenView(fromLiveState = true)
                    } else {
                        // Students ready — show submit button
                        findViewById<MaterialButton>(R.id.btn_submit_attendance).visibility = View.VISIBLE
                    }
                }

                is AttendanceViewModel.AttendanceUiState.AlreadyTaken -> {
                    setLoading(false)
                    showAlreadyTakenView(fromLiveState = false)
                }

                is AttendanceViewModel.AttendanceUiState.NoStudents -> {
                    setLoading(false)
                    Toast.makeText(this, "No students assigned to this route.", Toast.LENGTH_LONG).show()
                }

                is AttendanceViewModel.AttendanceUiState.Error -> {
                    setLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }

                else -> setLoading(false)
            }
        }

        attendanceViewModel.studentsList.observe(this) { students ->
            allStudents = students
            attendanceState.clear()
            students.forEach { attendanceState[it.rollNo ?: it.email ?: ""] = false }
            renderList(students)
            updateStats()
        }
    }

    // ── Already-taken / post-submit view ──────────────────────────────────────

    /**
     * [fromLiveState] = true  → attendance was just submitted; use in-memory
     *                           attendanceState + allStudents for the list.
     * [fromLiveState] = false → loaded from Firestore (AlreadyTaken); use
     *                           todayAttendance from the ViewModel.
     */
    private fun showAlreadyTakenView(fromLiveState: Boolean) {

        // ── Resolve the students map ──────────────────────────────────────────
        val studentsMap: Map<String, Boolean>
        val dateLabel: String

        if (fromLiveState) {
            studentsMap = attendanceState.toMap()
            dateLabel   = SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault()).format(Date())
        } else {
            val taken = attendanceViewModel.todayAttendance.value ?: return
            studentsMap = taken.students ?: emptyMap()
            dateLabel   = "Date: ${taken.date ?: "Today"}"
        }

        val present = studentsMap.values.count { it }
        val absent  = studentsMap.values.count { !it }
        val total   = studentsMap.size
        val rate    = if (total > 0) present * 100 / total else 0

        // ── Populate summary stats ────────────────────────────────────────────
        findViewById<TextView>(R.id.tv_taken_present).text = present.toString()
        findViewById<TextView>(R.id.tv_taken_absent).text  = absent.toString()
        findViewById<TextView>(R.id.tv_taken_rate).text    = "$rate%"
        findViewById<TextView>(R.id.tv_taken_date).text    = dateLabel
        findViewById<TextView>(R.id.tv_taken_total).text   =
            "$total student${if (total != 1) "s" else ""}"

        // ── Build submitted student list ──────────────────────────────────────
        val takenContainer = findViewById<LinearLayout>(R.id.taken_student_list_container)
        takenContainer.removeAllViews()

        // Build lookup: rollNo → Student (so we can show name, seat, etc.)
        val studentLookup = allStudents.associateBy { it.rollNo ?: it.email ?: "" }

        studentsMap.entries
            .sortedWith(compareBy({ !it.value }, { it.key })) // present first, then by roll
            .forEachIndexed { index, (key, isPresent) ->
                val student = studentLookup[key]
                takenContainer.addView(
                    buildReadOnlyRow(
                        rollNo    = key,
                        name      = student?.name,
                        seat      = student?.seatNumber,
                        isPresent = isPresent,
                        isLast    = index == studentsMap.size - 1
                    )
                )
            }

        // ── Switch visibility ─────────────────────────────────────────────────
        findViewById<View>(R.id.already_taken_overlay).visibility = View.VISIBLE
        findViewById<View>(R.id.scroll_content).visibility        = View.GONE
    }
    /**
     * Builds a single read-only student row for the submitted-record list.
     * Shows avatar circle, name + roll/seat, and present/absent badge.
     */
    private fun buildReadOnlyRow(
        rollNo: String,
        name: String?,
        seat: String?,
        isPresent: Boolean,
        isLast: Boolean
    ): View {
        val dp = resources.displayMetrics.density

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
        }

        // ── Avatar circle with initials ───────────────────────────────────────
        val displayName = name?.takeIf { it.isNotBlank() } ?: rollNo
        val initials = displayName
            .split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .ifEmpty { "?" }

        val avatar = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (38 * dp).toInt(), (38 * dp).toInt()
            ).apply { marginEnd = (12 * dp).toInt() }
            text     = initials
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity  = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape       = GradientDrawable.OVAL
                setColor(
                    if (isPresent) Color.parseColor("#3B6D11")
                    else           Color.parseColor("#A32D2D")
                )
            }
        }

        // ── Info column ───────────────────────────────────────────────────────
        val infoCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            orientation = LinearLayout.VERTICAL
        }

        infoCol.addView(TextView(this).apply {
            text     = name?.takeIf { it.isNotBlank() } ?: "—"   // Fixed: show real name if available
            textSize = 13f
            setTextColor(Color.parseColor("#1F1F1F"))
            typeface = Typeface.DEFAULT_BOLD
        })

        infoCol.addView(TextView(this).apply {
            text     = "Roll: $rollNo${if (seat != null) "  ·  Seat: $seat" else ""}"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, (2 * dp).toInt(), 0, 0)
        })

        // ── Badge ─────────────────────────────────────────────────────────────
        val badge = TextView(this).apply {
            text     = if (isPresent) "Present" else "Absent"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(
                (10 * dp).toInt(), (4 * dp).toInt(),
                (10 * dp).toInt(), (4 * dp).toInt()
            )
            setTextColor(
                if (isPresent) Color.parseColor("#27500A")
                else           Color.parseColor("#791F1F")
            )
            background = GradientDrawable().apply {
                setColor(
                    if (isPresent) Color.parseColor("#C0DD97")
                    else           Color.parseColor("#F7C1C1")
                )
                cornerRadius = 20 * dp
            }
        }

        row.addView(avatar)
        row.addView(infoCol)
        row.addView(badge)

        // Thin divider below each row except the last
        return if (isLast) row else LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(row)
            addView(View(this@SupervisorAttendanceActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                )
                setBackgroundColor(Color.parseColor("#F0F0F0"))
            })
        }
    }

    // ── Student row rendering (live attendance) ────────────────────────────────

    private fun renderList(list: List<Student>) {
        listContainer.removeAllViews()
        tvListHeader.text = "Student list (${list.size} students)"
        if (list.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text    = "No students found"
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 40)
                setTextColor(Color.parseColor("#888888"))
            })
        } else {
            list.forEach { listContainer.addView(createStudentRow(it)) }
        }
    }

    private fun createStudentRow(student: Student): MaterialCardView {
        val dp  = resources.displayMetrics.density
        val key = student.rollNo ?: student.email ?: ""

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * dp).toInt() }
            radius        = 12 * dp
            cardElevation = 2 * dp
            isClickable   = true
            isFocusable   = true
        }

        val row = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(
                (12 * dp).toInt(), (12 * dp).toInt(),
                (12 * dp).toInt(), (12 * dp).toInt()
            )
        }

        val name     = student.name ?: student.rollNo ?: "?"
        val initials = name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")

        val avatar = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams((42 * dp).toInt(), (42 * dp).toInt())
            text     = initials
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity  = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#297fd4"))
            }
        }

        val infoCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = (12 * dp).toInt() }
            orientation = LinearLayout.VERTICAL
        }
        infoCol.addView(TextView(this).apply {
            text     = student.name ?: "—"
            textSize = 14f
            setTextColor(Color.parseColor("#1F1F1F"))
            typeface = Typeface.DEFAULT_BOLD
        })
        infoCol.addView(TextView(this).apply {
            text     = "Roll: ${student.rollNo ?: "—"}  ·  Seat: ${student.seatNumber ?: "—"}"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, (2 * dp).toInt(), 0, 0)
        })

        val badge = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(
                (10 * dp).toInt(), (4 * dp).toInt(),
                (10 * dp).toInt(), (4 * dp).toInt()
            )
        }

        val displayStatus =
            if (!attendanceState.containsKey(key)) RowStatus.NONE
            else if (attendanceState[key] == true) RowStatus.PRESENT
            else RowStatus.ABSENT

        applyRowStatus(card, badge, displayStatus)

        row.addView(avatar)
        row.addView(infoCol)
        row.addView(badge)
        card.addView(row)
        card.setOnClickListener { showMarkDialog(student) }
        return card
    }

    private enum class RowStatus { NONE, PRESENT, ABSENT }

    private fun applyRowStatus(card: MaterialCardView, badge: TextView, status: RowStatus) {
        val dp = resources.displayMetrics.density
        when (status) {
            RowStatus.PRESENT -> {
                card.setCardBackgroundColor(Color.parseColor("#F1FAF1"))
                card.strokeColor = Color.parseColor("#3B6D11")
                card.strokeWidth = (2 * dp).toInt()
                badge.text = "Present"
                badge.setTextColor(Color.parseColor("#27500A"))
                badge.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#C0DD97")); cornerRadius = 20 * dp
                }
            }
            RowStatus.ABSENT -> {
                card.setCardBackgroundColor(Color.parseColor("#FFF1F1"))
                card.strokeColor = Color.parseColor("#A32D2D")
                card.strokeWidth = (2 * dp).toInt()
                badge.text = "Absent"
                badge.setTextColor(Color.parseColor("#791F1F"))
                badge.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F7C1C1")); cornerRadius = 20 * dp
                }
            }
            RowStatus.NONE -> {
                card.setCardBackgroundColor(Color.WHITE)
                card.strokeColor = Color.parseColor("#E0E0E0")
                card.strokeWidth = (1 * dp).toInt()
                badge.text = "Tap to mark"
                badge.setTextColor(Color.parseColor("#555555"))
                badge.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EEEEEE")); cornerRadius = 20 * dp
                }
            }
        }
    }

    // ── Dialog ─────────────────────────────────────────────────────────────────

    private var dialogStudent: Student? = null

    private fun setupDialogButtons() {
        findViewById<View>(R.id.dialog_overlay).setOnClickListener { hideDialog() }
        findViewById<View>(R.id.dialog_card).setOnClickListener { /* consume */ }
    }

    private fun showMarkDialog(student: Student) {
        dialogStudent = student
        val name     = student.name ?: student.rollNo ?: "Student"
        val initials = name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")

        findViewById<TextView>(R.id.dialog_avatar).text = initials
        findViewById<TextView>(R.id.dialog_name).text   = name
        findViewById<TextView>(R.id.dialog_meta).text   =
            "Roll: ${student.rollNo ?: "—"} · Seat: ${student.seatNumber ?: "—"}"

        findViewById<View>(R.id.dialog_overlay).visibility = View.VISIBLE
    }

    private fun hideDialog() {
        dialogStudent = null
        findViewById<View>(R.id.dialog_overlay).visibility = View.GONE
    }

    private fun markStudent(present: Boolean) {
        val student = dialogStudent ?: return
        val key     = student.rollNo ?: student.email ?: return
        attendanceState[key] = present
        attendanceViewModel.toggleStudentPresence(key, present)
        hideDialog()
        renderList(allStudents)
        updateStats()
    }

    // ── Quick actions + submit ─────────────────────────────────────────────────

    private fun setupQuickActions() {
        findViewById<MaterialButton>(R.id.btn_mark_all_present).setOnClickListener {
            allStudents.forEach { s ->
                val key = s.rollNo ?: s.email ?: ""
                attendanceState[key] = true
                attendanceViewModel.toggleStudentPresence(key, true)
            }
            renderList(allStudents)
            updateStats()
            Toast.makeText(this, "All students marked present", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btn_mark_present).setOnClickListener { markStudent(true) }
        findViewById<MaterialButton>(R.id.btn_mark_absent).setOnClickListener  { markStudent(false) }

        findViewById<MaterialButton>(R.id.btn_submit_attendance).setOnClickListener {
            val bId  = busId
            val sUid = supervisorUid
            if (bId == null || sUid == null) {
                Toast.makeText(this, "Missing bus or supervisor info", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setLoading(true)
            attendanceViewModel.submitAttendance(busId = bId, supervisorId = sUid)
            // Result is handled in observeViewModel → Success("submitted")
        }
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        findViewById<TextInputEditText>(R.id.et_search)
            .addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString()?.lowercase()?.trim() ?: ""
                    val filtered = if (q.isEmpty()) allStudents
                    else allStudents.filter {
                        it.name?.lowercase()?.contains(q) == true ||
                                it.rollNo?.lowercase()?.contains(q) == true
                    }
                    renderList(filtered)
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
    }

    // ── Stats ──────────────────────────────────────────────────────────────────

    private fun updateStats() {
        val present = attendanceState.values.count { it }
        val absent  = attendanceState.values.count { !it }
        val total   = allStudents.size
        val rate    = if (total > 0) present * 100 / total else 0
        findViewById<TextView>(R.id.tv_present_count).text = present.toString()
        findViewById<TextView>(R.id.tv_absent_count).text  = absent.toString()
        findViewById<TextView>(R.id.tv_rate).text          = "$rate%"
    }

    // ── Loading overlay ────────────────────────────────────────────────────────

    private fun setLoading(loading: Boolean) {
        findViewById<View>(R.id.loading_overlay).visibility =
            if (loading) View.VISIBLE else View.GONE
    }

    // ── Bottom nav ─────────────────────────────────────────────────────────────

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.supervisor_bottom_nav)
        bottomNav.selectedItemId = R.id.nav_attendance
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, SupervisorDashboardActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_route -> {
                    startActivity(Intent(this, SupervisorRouteDetails::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_attendance -> true
                R.id.nav_location -> {
                    startActivity(Intent(this, SupervisorTrackingActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                else -> false
            }
        }
    }
}