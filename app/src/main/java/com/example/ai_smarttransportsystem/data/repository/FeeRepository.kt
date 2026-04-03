package com.example.ai_smarttransportsystem.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.ai_smarttransportsystem.data.model.Fee
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await

class FeeRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val studentsCollection = firestore.collection("students")
    private val feesCollection = firestore.collection("fees")

    private val currentUid: String?
        get() = auth.currentUser?.uid

    /**
     * Get current student's fee record.
     * Document ID is same as Student ID (UID).
     */
    suspend fun getFeeForSemester(forceRefresh:Boolean=false, semester: String): Result<Fee?> {
        val studentId = currentUid ?: return Result.failure(Exception("Not logged in"))

        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = feesCollection.document(studentId).get(source).await()
            val fee = if (doc.exists()) mapDocToFee(doc) else null
            Result.success(fee)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all fees for the current student
     */
    suspend fun getAllFeesForStudent(
        forceRefresh: Boolean = false,
        semester: String? = null
    ): Result<List<Fee>> {
        val studentId = currentUid ?: return Result.failure(Exception("Not logged in"))

        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT

            var query = feesCollection
                .whereEqualTo("student_id", studentId)

            if (!semester.isNullOrBlank()) {
                query = query.whereEqualTo("semester", semester)
            }

            val snapshot = query.get(source).await()
            val fees = snapshot.documents.mapNotNull { doc ->
                runCatching { mapDocToFee(doc) }.getOrNull()
            }
            Result.success(fees)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Admin: Get all fees across all students
     */
    suspend fun getAllFeesAdmin(forceRefresh: Boolean = false, semester: String? = null): Result<List<Fee>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT

            var query = feesCollection.limit(1000)
            if (!semester.isNullOrBlank()) {
                query = query.whereEqualTo("semester", semester)
            }

            val snapshot = query.get(source).await()
            val fees = snapshot.documents.mapNotNull { doc ->
                runCatching { mapDocToFee(doc) }.getOrNull()
            }
            Result.success(fees)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save or update fee record.
     * Document ID is the student ID.
     */
    suspend fun saveFee(fee: Fee): Result<Unit> {
        val studentId = fee.studentId ?: return Result.failure(Exception("No student ID"))

        return try {
            feesCollection.document(studentId).set(fee).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculate fee based on distance, rate and duration
     */
    fun calculateFee(distanceKm: Double, ratePerKm: Double, semesterDays: Int): Double {
        return distanceKm * ratePerKm * semesterDays
    }

    /**
     * Mark the current student's fee as paid.
     * Keeps the original amount in DB for admin calculations.
     */
    suspend fun markFeeAsPaid(semester: String = "Spring 2026"): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        
        return try {
            // 1. Update fee record status ONLY (Amount preserved in DB)
            feesCollection.document(uid).update(
                mapOf(
                    "payment_status" to "paid",
                    "updated_at"     to Timestamp.now()
                )
            ).await()

            // 2. Reflect on student doc
            studentsCollection.document(uid).update(
                "fee_status", "paid"
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapDocToFee(doc: com.google.firebase.firestore.DocumentSnapshot): Fee {
        val d = doc.data ?: emptyMap<String, Any>()
        return Fee(
            semester       = d["semester"]        as? String,
            amount         = (d["amount"]         as? Number)?.toDouble(),
            paymentStatus  = (d["payment_status"] as? String) ?: "unpaid",
            routeDistance  = (d["route_distance"] as? Number)?.toDouble(),
            studentId      = (d["student_id"]      as? String) ?: doc.id,
            createdAt      = (d["created_at"]     as? com.google.firebase.Timestamp) ?: com.google.firebase.Timestamp.now(),
            updatedAt      = (d["updated_at"]     as? com.google.firebase.Timestamp) ?: com.google.firebase.Timestamp.now()
        )
    }
}
