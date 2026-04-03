package com.example.ai_smarttransportsystem.ui.admin

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Student
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.databinding.AdminViewStopStudentsBinding
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.student.StudentViewModel

class AdminViewStopStudentsActivity : BaseActivity() {

    private lateinit var binding: AdminViewStopStudentsBinding

    private val viewModel: StudentViewModel by viewModels {
        StudentViewModel.factory(StudentRepository())
    }

    private val adapter = StopStudentsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminViewStopStudentsBinding.inflate(layoutInflater)   // ✅ correct binding name
        setContentView(binding.root)

        val studentIds  = intent.getStringArrayListExtra("student_ids") ?: arrayListOf()
        val stopName   = intent.getStringExtra("stop_name")   ?: "Stop"
        val stopNumber = intent.getIntExtra("stop_number", 0)

        binding.tvStopName.text   = stopName
        binding.tvStopNumber.text = "Stop #$stopNumber"

        binding.rvStudents.adapter = adapter

        viewModel.studentState.observe(this) { state ->
            when (state) {
                is StudentViewModel.StudentUiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.rvStudents.visibility  = View.GONE
                    binding.emptyState.visibility  = View.GONE
                }
                is StudentViewModel.StudentUiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.rvStudents.visibility  = View.VISIBLE
                    binding.emptyState.visibility  = View.GONE
                }
                is StudentViewModel.StudentUiState.Empty -> {
                    binding.progressBar.visibility = View.GONE
                    binding.rvStudents.visibility  = View.GONE
                    binding.emptyState.visibility  = View.VISIBLE
                }
                is StudentViewModel.StudentUiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.rvStudents.visibility  = View.GONE
                    binding.emptyState.visibility  = View.VISIBLE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> Unit
            }
        }

        viewModel.stopStudentsList.observe(this) { list ->
            adapter.submitList(list)
//            val count = list.size
//            binding.tvStudentCount.text = "$count Student${if (count != 1) "s" else ""}"
        }

        if (studentIds.isNotEmpty()) {
            viewModel.loadStudentsByIds(forceRefresh = true,studentIds=studentIds)
        } else {
            binding.emptyState.visibility = View.VISIBLE
            Toast.makeText(this, "No students at this stop", Toast.LENGTH_SHORT).show()
        }

        setupBottomNavigation()
        binding.btnBack.setOnClickListener { finish() }
    }

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

    // ── Adapter ───────────────────────────────────────────────────────────────

    private inner class StopStudentsAdapter
        : RecyclerView.Adapter<StopStudentsAdapter.VH>() {

        private var items: List<Pair<Student, String>> = emptyList()

        fun submitList(list: List<Pair<Student, String>>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.admin_item_stop_student, parent, false)   // ✅ correct layout name
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) =
            holder.bind(items[position].first, items[position].second)

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {

            private val tvSeatAvatar: TextView = itemView.findViewById(R.id.tv_seat_avatar)
            private val tvName:       TextView = itemView.findViewById(R.id.tv_name)
            private val tvRoll:       TextView = itemView.findViewById(R.id.tv_roll)
            private val tvFeeBadge:   TextView = itemView.findViewById(R.id.tv_fee_badge)
            private val tvContact:    TextView = itemView.findViewById(R.id.tv_contact)
            private val tvSemester:   TextView = itemView.findViewById(R.id.tv_semester)
            private val tvSeat:       TextView = itemView.findViewById(R.id.tv_seat)
            private val tvRegBadge:   TextView = itemView.findViewById(R.id.tv_reg_badge)

            fun bind(student: Student, contact: String) {

                // Avatar: seat number (Int?) converted to String, or initials fallback
                tvSeatAvatar.text = student.seatNumber?.toString()   // ✅ Int? → toString()
                    ?: (student.name ?: "")
                        .split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .ifEmpty { "?" }

                tvName.text     = student.name?.ifBlank { "—" }     ?: "—"
                tvRoll.text     = student.rollNo?.ifBlank { "—" }   ?: "—"
                tvContact.text  = contact.ifBlank { "—" }
                tvSemester.text = student.semester?.ifBlank { "—" } ?: "—"
                tvSeat.text     = student.seatNumber?.toString()     ?: "—"   // ✅ Int? → toString()

                // Fee badge
                val isPaid = student.feeStatus.equals("paid", ignoreCase = true)
                tvFeeBadge.text = if (isPaid) "Paid" else "Unpaid"
                tvFeeBadge.backgroundTintList = ColorStateList.valueOf(
                    if (isPaid) Color.parseColor("#E8F5E9") else Color.parseColor("#FFF3E0")
                )
                tvFeeBadge.setTextColor(
                    if (isPaid) Color.parseColor("#2E7D32") else Color.parseColor("#E65100")
                )

                // Registration badge
                val isConfirmed = student.registrationStatus
                    .equals("successful", ignoreCase = true)
                tvRegBadge.text = if (isConfirmed) "Confirmed" else "Pending"
                tvRegBadge.backgroundTintList = ColorStateList.valueOf(
                    if (isConfirmed) Color.parseColor("#E8F5E9") else Color.parseColor("#FFF8E1")
                )
                tvRegBadge.setTextColor(
                    if (isConfirmed) Color.parseColor("#2E7D32") else Color.parseColor("#F9A825")
                )
            }
        }
    }
}