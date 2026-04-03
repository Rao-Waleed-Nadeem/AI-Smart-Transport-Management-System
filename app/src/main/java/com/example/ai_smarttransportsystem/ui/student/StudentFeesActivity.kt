package com.example.ai_smarttransportsystem.ui.student

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.model.Fee
import com.example.ai_smarttransportsystem.data.repository.FeeRepository
import com.example.ai_smarttransportsystem.data.repository.StudentRepository
import com.example.ai_smarttransportsystem.ui.auth.BaseActivity
import com.example.ai_smarttransportsystem.ui.shared.FeeViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Locale

class StudentFeesActivity : BaseActivity() {

    private val studentViewModel: StudentViewModel by viewModels {
        StudentViewModel.factory(StudentRepository())
    }

    private val feeViewModel: FeeViewModel by viewModels {
        FeeViewModel.Factory(FeeRepository(), StudentRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.student_fees)

        resetUiToPlaceholders()
        observeViewModel()

        studentViewModel.loadCurrentStudent(forceRefresh = true)
        feeViewModel.loadCurrentFee(forceRefresh = true)
        feeViewModel.loadAllFees(forceRefresh = true)

        setupPayNowButton()
        setupBottomNavigation()
    }

    private fun resetUiToPlaceholders() {
        findViewById<TextView>(R.id.tv_fee_amount).text = "--"
        findViewById<TextView>(R.id.tv_due_date).text = "Due: --"
        findViewById<TextView>(R.id.tv_fee_status_label).text = "Status"
        findViewById<View>(R.id.tv_empty_history).visibility = View.VISIBLE
        findViewById<View>(R.id.verticalScroll).visibility = View.GONE
    }

    private fun observeViewModel() {
        feeViewModel.currentFee.observe(this) { fee ->
            updateFeeCard(fee)
        }

        feeViewModel.allFees.observe(this) { fees ->
            updatePaymentHistory(fees)
        }
    }

    private fun updateFeeCard(fee: Fee?) {
        val amountTv = findViewById<TextView>(R.id.tv_fee_amount)
        val dueTv = findViewById<TextView>(R.id.tv_due_date)
        val payBtn = this.findViewById<MaterialButton>(R.id.btn_pay_now)

        if (fee == null) {
            amountTv.text = "--"
            dueTv.text = "Due: --"
            payBtn.visibility = View.GONE
            return
        }

        if (fee.paymentStatus?.lowercase() == "paid") {
            amountTv.text = "PKR 0"  // Show 0 to student if paid
            dueTv.text = "PAID"
            dueTv.setBackgroundResource(R.drawable.rounded_rect_light_green)
            payBtn.visibility = View.GONE
        } else {
            amountTv.text = "PKR ${fee.amount?.toInt() ?: "--"}"
            dueTv.text = "Due: ${fee.semester ?: "N/A"}"
            dueTv.setBackgroundResource(R.drawable.due_date_bg)
            payBtn.visibility = View.VISIBLE
        }
    }

    private fun updatePaymentHistory(fees: List<Fee>) {
        val container = findViewById<LinearLayout>(R.id.container_history_rows)
        val emptyTv = findViewById<TextView>(R.id.tv_empty_history)
        val scrollView = findViewById<View>(R.id.verticalScroll)

        // Keep header (child 0), remove all others
        if (container.childCount > 1) {
            container.removeViews(1, container.childCount - 1)
        }

        val paidFees = fees.filter { it.paymentStatus?.lowercase() == "paid" }

        if (paidFees.isEmpty()) {
            emptyTv.visibility = View.VISIBLE
            scrollView.visibility = View.GONE
        } else {
            emptyTv.visibility = View.GONE
            scrollView.visibility = View.VISIBLE
            paidFees.forEach { fee ->
                container.addView(createHistoryRow(fee))
            }
        }
    }

    private fun createHistoryRow(fee: Fee): LinearLayout {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val paidDate = fee.updatedAt.let { dateFormat.format(it.toDate()) } ?: "N/A"

        row.addView(createTableTextView(fee.semester ?: "N/A", 140))
        // Show actual amount in history even if status is paid
        row.addView(createTableTextView("PKR ${fee.amount?.toInt() ?: 0}", 100, true))
        row.addView(createTableTextView(paidDate, 140))

        val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams((140 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "Download"
            textSize = 12f
        }
        row.addView(btn)

        return row
    }

    private fun createTableTextView(text: String, widthDp: Int, isAmount: Boolean = false): TextView {
        val dp = resources.displayMetrics.density
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams((widthDp * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            this.text = text
            if (isAmount) {
                setTextColor(Color.parseColor("#2E7D32"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
    }

    // ── Pay Now ───────────────────────────────────────────────────────────────

    private fun setupPayNowButton() {
        findViewById<MaterialButton>(R.id.btn_pay_now).setOnClickListener {
            showConfirmPaymentDialog()
        }
    }

    private fun showConfirmPaymentDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Payment")
            .setMessage(
                "Mark this semester fee as PAID?\n\n" +
                        "(Note: This is for demo purposes. No real payment will be processed.)"
            )
            .setPositiveButton("Yes, Mark as Paid") { _, _ ->
                feeViewModel.markFeeAsPaid()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.nav_wallet

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_wallet -> true
                R.id.nav_home -> {
                    startActivity(Intent(this, StudentHomeActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_attendance -> {
                    startActivity(Intent(this, StudentAttendanceActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_seat -> {
                    startActivity(Intent(this, StudentSeatRegistrationActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                else -> false
            }
        }
    }
}
