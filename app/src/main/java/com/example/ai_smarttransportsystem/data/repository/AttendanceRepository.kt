package com.example.ai_smarttransportsystem.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.ai_smarttransportsystem.data.model.Attendance
import com.example.ai_smarttransportsystem.data.model.Student
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val attendanceCollection = firestore.collection("attendance")
    private val studentsCollection = firestore.collection("students")
    private val supervisorsCollection = firestore.collection("supervisors")

    private val currentUid: String?
        get() = auth.currentUser?.uid

    // ────── IN-MEMORY CACHE (this is the key) ──────
    private var cachedStats: Pair<Int, Int>? = null   // (presents, absents)
    private var lastCacheTime: Long = 0

    /**
     * Get today's date in "yyyy-MM-dd" format
     */
    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    /**
     * Fetch attendance stats for a student on a specific route.
     * Logic: 
     * 1. Find supervisorId for the routeId.
     * 2. Fetch all attendance records for that supervisorId.
     * 3. Count presents/absents for the student using studentKey (rollNo).
     */
    suspend fun getStudentAttendanceStats(forceRefresh:Boolean=false,busId: String, studentKey: String): Result<Pair<Int, Int>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT

            // 2. Get all attendance records for this supervisor
            val attendanceSnapshot = attendanceCollection
                .whereEqualTo("bus_id", busId)
                .get(source)
                .await()

            var presents = 0
            var absents = 0

            // 3. Aggregate stats
            attendanceSnapshot.documents.forEach { doc ->
                val attendance = mapDocToAttendance(doc.data)
                attendance?.students?.get(studentKey)?.let { isPresent ->
                    if (isPresent) presents++ else absents++
                }
            }

            val stats = presents to absents

            // Update cache
//            cachedStats = stats
//            lastCacheTime = System.currentTimeMillis()

            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get personal attendance stats for ONE student (presents, absents)
     * Counts across recent attendance records for their bus
     */
    suspend fun getStudentPersonalAttendanceStats(
        forceRefresh:Boolean=false,
        studentKey: String,   // usually roll_no
        busId: String
    ): Result<Pair<Int, Int>> {   // returns (presents, absents)

        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            // Get recent attendance records for this bus (last 30 days is reasonable)
            val snapshot = attendanceCollection
                .whereEqualTo("bus_id", busId)
                .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(30)   // adjust limit as needed (30 days ≈ 1 month)
                .get(source)
                .await()

            var presents = 0
            var absents = 0

            snapshot.documents.forEach { doc ->
                val attendance = mapDocToAttendance(doc.data)
                val wasPresent = attendance?.students?.get(studentKey) ?: false

                if (wasPresent) presents++ else absents++
            }

            Result.success(presents to absents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    /**
     * Get students by route_id — used by supervisor attendance flow.
     */
    suspend fun getStudentsByRoute(forceRefresh:Boolean=false,routeId: String): Result<List<Student>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = studentsCollection
                .whereEqualTo("route_id", routeId)
                .get(source)
                .await()
            val students = snapshot.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                mapDocToStudent(d)
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get list of students assigned to a specific bus
     */
    suspend fun getStudentsForBus(forceRefresh:Boolean=false,busId: String): Result<List<Student>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = studentsCollection
                .whereEqualTo("bus_id", busId)
                .get(source)
                .await()

            val students = snapshot.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                mapDocToStudent(d)
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save / update today's attendance for a bus
     */
    suspend fun saveAttendance(
        busId: String,
        supervisorId: String,
        studentsAttendance: Map<String, Boolean>
    ): Result<Unit> {
        val date = getTodayDate()
        val docId = "${busId}_$date"

        val attendance = Attendance(
            date = date,
            busId = busId,
            supervisorId = supervisorId,
            students = studentsAttendance,
            createdAt = com.google.firebase.Timestamp.now()
        )

        return try {
            attendanceCollection.document(docId).set(attendance).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get today's attendance record for a bus
     */
    suspend fun getTodayAttendance(forceRefresh:Boolean=false,busId: String): Result<Attendance?> {
        val date = getTodayDate()
        val docId = "${busId}_$date"

        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = attendanceCollection.document(docId).get(source).await()
            val attendance = if (doc.exists()) {
                mapDocToAttendance(doc.data)
            } else null
            Result.success(attendance)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun mapDocToStudent(d: Map<String, Any>): Student {
        return Student(
            name               = d["name"]                 as? String,
            email              = d["email"]                as? String,
            rollNo             = d["roll_no"]              as? String,
            semester           = d["semester"]             as? String,
            seatNumber         = (d["seat_number"]         as? String),
            registrationStatus = (d["registration_status"] as? String) ?: "pending",
            feeStatus          = (d["fee_status"]          as? String) ?: "unpaid",
            routeId            = d["route_id"]             as? String,
            busId              = d["bus_id"]               as? String,
            stopId             = d["stop_id"]              as? String,
            createdAt          = (d["created_at"] as? com.google.firebase.Timestamp)
                ?: com.google.firebase.Timestamp.now()
        )
    }

    private fun mapDocToAttendance(d: Map<String, Any>?): Attendance? {
        if (d == null) return null
        return Attendance(
            date         = d["date"]          as? String,
            busId        = d["bus_id"]        as? String,
            supervisorId = d["supervisor_id"] as? String,
            students     = (d["students"]     as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? Boolean ?: return@mapNotNull null
                key to value
            }?.toMap(),
            createdAt    = d["created_at"]    as? com.google.firebase.Timestamp
        )
    }
}