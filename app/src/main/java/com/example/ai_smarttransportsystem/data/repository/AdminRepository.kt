package com.example.ai_smarttransportsystem.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.example.ai_smarttransportsystem.data.model.Bus
import com.example.ai_smarttransportsystem.data.model.Stop
import com.example.ai_smarttransportsystem.data.model.Supervisor
import com.example.ai_smarttransportsystem.data.model.Student
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AdminRepository @Inject constructor() {

    private val firestore = FirebaseFirestore.getInstance()

    private val busesCollection = firestore.collection("buses")
    private val stopsCollection = firestore.collection("stops")
    private val usersCollection = firestore.collection("users")

    // ────────────────────────────────────────────────
    // Buses Management
    // ────────────────────────────────────────────────

    suspend fun addBus(bus: Bus): Result<String> {
        return try {
            val docRef = busesCollection.add(bus).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllBuses(forceRefresh:Boolean = false): Result<List<Bus>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = busesCollection.get(source).await()
            val buses = snapshot.documents.mapNotNull { doc ->
    val d = doc.data ?: return@mapNotNull null
    Bus(
        busNumber    = d["bus_number"]    as? String,
        plateNumber  = d["plate_number"]  as? String,
        capacity     = (d["capacity"]     as? Number)?.toInt() ?: 40,
        routeId      = d["route_id"]      as? String,
        supervisorId = d["supervisor_id"] as? String,
        status       = (d["status"]       as? String) ?: "active",
        isAvailable  = (d["is_available"] as? Boolean) ?: true,
        createdAt    = (d["created_at"]   as? com.google.firebase.Timestamp) ?: com.google.firebase.Timestamp.now()
    )
}
            Result.success(buses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ────────────────────────────────────────────────
    // Students Management (Aggregate)
    // ────────────────────────────────────────────────

    suspend fun getAllStudents(forceRefresh:Boolean = false): Result<List<Student>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = firestore.collectionGroup("students").get(source).await()
            val students = snapshot.toObjects(Student::class.java)
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ────────────────────────────────────────────────
    // Stops Management
    // ────────────────────────────────────────────────

    suspend fun addStop(stop: Stop): Result<String> {
        return try {
            val docRef = stopsCollection.document()
            docRef.set(stop).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllStops(forceRefresh:Boolean = false): Result<List<Stop>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = stopsCollection.get(source).await()
            val stops = snapshot.documents.mapNotNull { doc ->
    val d = doc.data ?: return@mapNotNull null
    Stop(
        stopName               = d["stop_name"]                  as? String,
        latitude               = (d["latitude"]                  as? Number)?.toDouble(),
        longitude              = (d["longitude"]                 as? Number)?.toDouble(),
        snapDistanceM          = (d["snap_distance_m"]           as? Number)?.toDouble(),
        distanceToUniversityKm = (d["distance_to_university_km"] as? Number)?.toDouble(),
        feePerStudentPkr       = (d["fee_per_student_pkr"]       as? Number)?.toDouble(),
        studentCount           = (d["student_count"]             as? Number)?.toInt(),
        studentIds             = (d["student_ids"]               as? List<*>)?.filterIsInstance<String>(),
        routeId                = d["route_id"]                   as? String,
        isMerged               = d["is_merged"]                  as? Boolean,
        createdAt              = d["created_at"]                 as? com.google.firebase.Timestamp,
        updatedAt              = d["updated_at"]                 as? com.google.firebase.Timestamp
    )
}
            Result.success(stops)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ────────────────────────────────────────────────
    // Supervisor / Assignment Management
    // ────────────────────────────────────────────────

    suspend fun assignBusToSupervisor(
        supervisorUid: String,
        busId: String,
        routeId: String? = null
    ): Result<Unit> {
        return try {
            usersCollection
                .document(supervisorUid)
                .collection("supervisors")
                .document(supervisorUid)
                .update(
                    mapOf(
                        "assigned_bus" to busId,
                        "assigned_route" to routeId
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllSupervisors(forceRefresh:Boolean = false): Result<List<Pair<String, Supervisor>>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = firestore.collectionGroup("supervisors").get(source).await()
            val list = snapshot.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
val sup = Supervisor(
    assignedBus   = d["assigned_bus"]   as? String,
    assignedRoute = d["assigned_route"] as? String,
    createdAt     = (d["created_at"]    as? com.google.firebase.Timestamp) ?: com.google.firebase.Timestamp.now()
)
                val uid = doc.reference.parent.parent?.id // parent user doc ID
                uid?.let { Pair(it, sup) }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}