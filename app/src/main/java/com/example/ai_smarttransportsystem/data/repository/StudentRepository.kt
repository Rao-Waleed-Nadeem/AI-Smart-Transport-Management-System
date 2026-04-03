package com.example.ai_smarttransportsystem.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.ai_smarttransportsystem.data.model.Student
import com.example.ai_smarttransportsystem.data.model.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class StudentRepository @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val studentsCollection = firestore.collection("students")
    private val usersCollection = firestore.collection("users")

    private val currentUid: String?
        get() = auth.currentUser?.uid

    // ── Current student (student-facing screens) ──────────────────────────────

    suspend fun getUserProfile(forceRefresh:Boolean=false): Result<User?> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = usersCollection.document(uid).get(source).await()
            if (!doc.exists()) return Result.success(null)
            Result.success(mapDocToUser(doc))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentStudent(forceRefresh:Boolean=false): Result<Student?> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = studentsCollection.document(uid).get(source).await()
            if (!doc.exists()) return Result.success(null)
            Result.success(mapDocToStudent(doc))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveStudentData(student: Student): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            studentsCollection.document(uid).set(student).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hasStudentData(): Result<Boolean> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val doc = studentsCollection.document(uid).get().await()
            Result.success(doc.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Admin / bulk queries ──────────────────────────────────────────────────

    /** Get all students — used by admin dashboard for total count */
    suspend fun getAllStudents(forceRefresh:Boolean=false): Result<List<Student>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = studentsCollection.get(source).await()
            val students = snapshot.documents.mapNotNull { doc ->
                runCatching { mapDocToStudent(doc) }.getOrNull()
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all students assigned to a specific route
     * Uses whereEqualTo("route_id", routeId)
     */
    suspend fun getStudentsByRoute(forceRefresh:Boolean=false,routeId: String): Result<List<Student>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = firestore.collection("students")
                .whereEqualTo("route_id", routeId)
                .get(source)
                .await()

            val students = snapshot.documents.mapNotNull { doc ->
                mapDocToStudent(doc)
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    /**
     * Get all students on a specific route as (docId, Student) pairs.
     * docId == student uid, used to fetch contact from users collection.
     */
    suspend fun getStudentsByRouteId(forceRefresh:Boolean=false,routeId: String): Result<List<Pair<String, Student>>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = studentsCollection
                .whereEqualTo("route_id", routeId)
                .get(source)
                .await()
            val pairs = snapshot.documents.mapNotNull { doc ->
                runCatching { Pair(doc.id, mapDocToStudent(doc)) }.getOrNull()
            }
            Result.success(pairs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch specific students by their UIDs (from Stop.studentIds).
     * Batched in chunks of 10 to respect Firestore whereIn limit.
     * Returns (docId, Student) pairs — docId == uid for contact lookup.
     */
    suspend fun getStudentsByIds(forceRefresh:Boolean=false,studentIds: List<String>): Result<List<Pair<String, Student>>> {
        if (studentIds.isEmpty()) return Result.success(emptyList())
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val pairs = mutableListOf<Pair<String, Student>>()
            studentIds.chunked(10).forEach { chunk ->
                val snapshot = studentsCollection
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get(source)
                    .await()
                snapshot.documents.forEach { doc ->
                    runCatching { pairs.add(Pair(doc.id, mapDocToStudent(doc))) }
                }
            }
            Result.success(pairs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch the contact (phone) field from users/{uid}.
     * Returns empty string on failure so the UI never crashes.
     */
    suspend fun getUserContact(forceRefresh:Boolean=false,uid: String): String {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = usersCollection.document(uid).get(source).await()
            (doc.data?.get("contact") as? String) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Update a student's registration_status and bus_id after a bus is assigned.
     */
    suspend fun updateStudentStatus(
        studentDocId: String,
        registrationStatus: String,
        busId: String
    ): Result<Unit> {
        return try {
            studentsCollection.document(studentDocId).update(
                mapOf(
                    "registration_status" to registrationStatus,
                    "bus_id" to busId
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Convenience getters ───────────────────────────────────────────────────

    suspend fun getAssignedRouteId(forceRefresh:Boolean=false): Result<String?> {
        val result = getCurrentStudent(forceRefresh)
        return when {
            result.isSuccess -> Result.success(result.getOrNull()?.routeId)
            else -> Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    suspend fun getAssignedBusId(forceRefresh:Boolean=false): Result<String?> {
        val result = getCurrentStudent(forceRefresh)
        return when {
            result.isSuccess -> Result.success(result.getOrNull()?.busId)
            else -> Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    suspend fun getAssignedStopId(forceRefresh:Boolean=false): Result<String?> {
        val result = getCurrentStudent(forceRefresh)
        return when {
            result.isSuccess -> Result.success(result.getOrNull()?.stopId)
            else -> Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun mapDocToStudent(doc: com.google.firebase.firestore.DocumentSnapshot): Student {
        val d = doc.data ?: emptyMap<String, Any>()
        return Student(
            uid                = doc.id,
            name               = d["name"]                 as? String,
            email              = d["email"]                as? String,
            rollNo             = d["roll_no"]              as? String,
            semester           = d["semester"]             as? String,
            seatNumber         = d["seat_number"]         as? String,
            registrationStatus = (d["registration_status"] as? String) ?: "pending",
            feeStatus          = (d["fee_status"]          as? String) ?: "unpaid",
            routeId            = d["route_id"]             as? String,
            busId              = d["bus_id"]               as? String,
            stopId             = d["stop_id"]              as? String,
            createdAt          = (d["created_at"]          as? com.google.firebase.Timestamp)
                ?: com.google.firebase.Timestamp.now()
        )
    }

    private fun mapDocToUser(doc: com.google.firebase.firestore.DocumentSnapshot): User {
        val d = doc.data ?: emptyMap<String, Any>()
        return User(
            uid       = doc.id,
            name      = (d["name"]       as? String) ?: "",
            email     = (d["email"]      as? String) ?: "",
            role      = (d["role"]       as? String) ?: "",
            contact   = (d["contact"]    as? String) ?: "",
            createdAt = (d["created_at"] as? com.google.firebase.Timestamp)
                ?: com.google.firebase.Timestamp.now()
        )
    }
}
